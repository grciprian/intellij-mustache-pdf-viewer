package generate;

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.samskivert.mustache.Mustache;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class PdfGenerationService {

  static final String PDF_GENERATION_ERROR = "A apărut o problemă în momentul generării fișierului.";
  static final String PDF_GENERATION_EMPTY_FILE = "Fișierul generat este gol.";
  private static final Logger logger = Logger.getInstance(PdfGenerationService.class);
  private static final String ERROR_HTML = """
    <p style="font-weight: bold;">{{message}}</p>
    <pre style="word-wrap: break-word; white-space: pre-wrap;">{{stackTrace}}</pre>
    """;
  private static PdfGenerationService instance;
  private final Mustache.Compiler mustacheCompiler;

  private PdfGenerationService(Mustache.Compiler compiler) {
    this.mustacheCompiler = compiler;
  }

  public static PdfGenerationService getInstance() {
    if (PdfGenerationService.instance != null) return PdfGenerationService.instance;
    PdfGenerationService.instance = new PdfGenerationService(CustomMustacheCompiler.getInstance());
    return PdfGenerationService.instance;
  }

  public Pdf generatePdf(Object model, String templateContent) {
    try (var outputStream = new ByteArrayOutputStream()) {
      var visitor = new JBMustacheTreeProducerVisitor();
      var html = getHtml(model, templateContent, visitor);
      writePdfContentToStream(outputStream, html);
      var pdf = outputStream.toByteArray();
      if (pdf.length == 0) {
        throw new RuntimeException(PDF_GENERATION_EMPTY_FILE);
      }
      return new Pdf(outputStream.toByteArray(), "visitor");
    } catch (Exception e) {
      try (var os = new ByteArrayOutputStream()) {
        writePdfContentToStream(os, getHtml(new ErrorObject(e), ERROR_HTML));
        return new Pdf(os.toByteArray(), "");
      } catch (Exception ex) {
        throw new RuntimeException(PDF_GENERATION_ERROR, ex);
      }
    }
  }

  private void writePdfContentToStream(OutputStream outputStream, String html) throws IOException {
    var builder = new PdfRendererBuilder();
    var document = convertHtmlToXHtml(html);
    builder.toStream(outputStream);
    builder.withW3cDocument(new W3CDom().fromJsoup(document), "/");
    addFonts(builder);
    builder.run();
  }

  private String getHtml(Object model, String templateContent) {
    return mustacheCompiler.compile(templateContent).execute(model);
  }

  private String getHtml(Object model, String templateContent, Mustache.Visitor visitor) {
    var template = mustacheCompiler.compile(templateContent);
    template.visit(visitor);
    return template.execute(model);
  }

  private Document convertHtmlToXHtml(String pdfContent) {
    var document = Jsoup.parse(pdfContent);
    document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).charset(StandardCharsets.UTF_8);
    return document;
  }

  private void addFonts(PdfRendererBuilder pdfRendererBuilder) {
    var f = new File(PdfViewerSettings.Companion.getInstance().getCustomMustacheFontsPath());
    if (f.isDirectory()) {
      var files = f.listFiles((dir, name) -> {
        var lower = name.toLowerCase();
        return lower.endsWith(".otf") || lower.endsWith(".ttf");
      });
      if (files != null) {
        for (var file : files) {
          try {
            var fontFamily = Font.createFont(Font.TRUETYPE_FONT, file).getFamily();
            pdfRendererBuilder.useFont(file, fontFamily);
          } catch (Exception e) {
            logger.error("Font could not be loaded: " + file.getName());
          }
        }
      }
    }
  }

  private static class ErrorObject {
    private final String message;
    private final String stackTrace;

    public ErrorObject(Throwable throwable) {
      this.message = throwable.getMessage();
      this.stackTrace = ExceptionUtils.getStackTrace(throwable);
    }

    public String getMessage() {
      return message;
    }

    public String getStackTrace() {
      return stackTrace;
    }
  }

  public record Pdf(byte[] content, String structure) {
  }
}
