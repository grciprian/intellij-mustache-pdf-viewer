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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.loadText;
import static generate.Utils.getRelativePathFromResourcePathWithMustachePrefixPath;

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

  private PdfGenerationService() {
    this.mustacheCompiler = CustomMustacheCompiler.getInstance();
  }

  public static PdfGenerationService getInstance() {
    if (instance != null) return instance;
    instance = new PdfGenerationService();
    return instance;
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

  public PdfContent generatePdf(Object model, VirtualFile mustacheFile) {
    try (var outputStream = new ByteArrayOutputStream()) {
      var relativePath = getRelativePathFromResourcePathWithMustachePrefixPath(mustacheFile);
      var templateContent = loadText(mustacheFile);
      var template = mustacheCompiler.compile(templateContent);
      var html = template.execute(model);
      writePdfContentToStream(outputStream, html);
      var pdf = outputStream.toByteArray();
      if (pdf.length == 0) {
        throw new RuntimeException(PDF_GENERATION_EMPTY_FILE);
      }
      var structure = PdfStructureService.getStructure(relativePath, template);
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
