package generate;

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.samskivert.mustache.Mustache;
import generate.PdfStructureService.Structure;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class PdfGenerationService {

  static final String PDF_GENERATION_ERROR = "A apărut o problemă în momentul generării fișierului.";
  static final String PDF_GENERATION_EMPTY_FILE = "Fișierul generat este gol.";
  private static final Logger logger = Logger.getInstance(PdfGenerationService.class);
  private static final String ERROR_HTML = """
    <p style="font-weight: bold;">{{message}}</p>
    <pre style="word-wrap: break-word; white-space: pre-wrap;">{{stackTrace}}</pre>
    """;
  private static PdfGenerationService instance;

  private final String templatesPath;
  private final String mustacheSuffix;
  private final Mustache.Compiler mustacheCompiler;

  public PdfGenerationService(String templatesPath, String mustacheSuffix) {
    this.templatesPath = templatesPath;
    this.mustacheSuffix = mustacheSuffix;
    this.mustacheCompiler = CustomMustacheCompiler.getInstance(templatesPath, mustacheSuffix).getMustacheCompiler();
  }

  public static PdfGenerationService getInstance(String templatesPath, String mustacheSuffix) {
    if (instance != null
      && Objects.equals(instance.templatesPath, templatesPath)
      && Objects.equals(instance.mustacheSuffix, mustacheSuffix)) return instance;
    return instance = new PdfGenerationService(templatesPath, mustacheSuffix);
  }

  private static void writePdfContentToStream(OutputStream outputStream, String html) throws IOException {
    var builder = new PdfRendererBuilder();
    var document = convertHtmlToXHtml(html);
    builder.toStream(outputStream);
    builder.withW3cDocument(new W3CDom().fromJsoup(document), "/");
    addFonts(builder);
    builder.run();
  }

  private static Document convertHtmlToXHtml(String pdfContent) {
    var document = Jsoup.parse(pdfContent);
    document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).charset(StandardCharsets.UTF_8);
    return document;
  }

  private static void addFonts(PdfRendererBuilder pdfRendererBuilder) {
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

  public PdfContent generatePdf(Object model, String relativeFilePath) {
    try (var outputStream = new ByteArrayOutputStream()) {
      var filePath = Path.of("%s\\%s.%s".formatted(templatesPath, relativeFilePath, mustacheSuffix)).toFile().getAbsolutePath();
      var template = mustacheCompiler.compile(new FileReader(filePath));
      var html = template.execute(model);
      writePdfContentToStream(outputStream, html);
      var pdf = outputStream.toByteArray();
      if (pdf.length == 0) throw new RuntimeException(PDF_GENERATION_EMPTY_FILE);
      var structure = PdfStructureService.getStructure(relativeFilePath, template, templatesPath, mustacheSuffix);
      return new PdfContent(outputStream.toByteArray(), structure);
    } catch (Exception e) {
      try (var os = new ByteArrayOutputStream()) {
        writePdfContentToStream(os, mustacheCompiler.compile(ERROR_HTML).execute(new ErrorObject(e)));
        return new PdfContent(os.toByteArray(), List.of());
      } catch (Exception ex) {
        throw new RuntimeException(PDF_GENERATION_ERROR, ex);
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

  public record PdfContent(byte[] byteArray, List<Structure> structures) {
  }

  public record Pdf(VirtualFile file, List<Structure> structures) {
  }
}
