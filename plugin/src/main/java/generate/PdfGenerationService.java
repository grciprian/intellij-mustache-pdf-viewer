package generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.samskivert.mustache.Mustache;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class PdfGenerationService {

  public static final String DEFAULT_PREFIX = "/templates";
  public static final String DEFAULT_SUFFIX = ".mustache";
  public static final String JAVA_RESOURCES_FOLDER_NAME = "resources";
  static final String PDF_GENERATION_ERROR = "A apărut o problemă în momentul generării fișierului";
  static final String PDF_GENERATION_EMPTY_FILE = "Fișierul generat este gol. Te rugăm să încerci mai târziu!";
  private static PdfGenerationService pdfGenerationService;
  final ObjectMapper objectMapper = new ObjectMapper();
  final Mustache.Compiler mustacheCompiler;
  final Logger logger = Logger.getInstance(PdfGenerationService.class);

  private PdfGenerationService(Mustache.Compiler compiler) {
    this.mustacheCompiler = compiler;
  }

  public static PdfGenerationService getInstance(VirtualFile forFile) {
    var forFileCanonicalPath = forFile.getCanonicalPath();
    assert forFileCanonicalPath != null;
    var resourcesIndex = forFileCanonicalPath.indexOf(JAVA_RESOURCES_FOLDER_NAME);
    if (resourcesIndex != -1) {
      var forFileResourcesPath = forFileCanonicalPath.substring(0, resourcesIndex + JAVA_RESOURCES_FOLDER_NAME.length());
      if (PdfGenerationService.pdfGenerationService != null) return PdfGenerationService.pdfGenerationService;
      PdfGenerationService.pdfGenerationService = new PdfGenerationService(
        Mustache.compiler().withLoader(name -> new FileReader(
          new File(forFileResourcesPath + DEFAULT_PREFIX, name + DEFAULT_SUFFIX))
        )
      );
      return PdfGenerationService.pdfGenerationService;
    }
    throw new RuntimeException("File is not in the resources folder of the java project!");
  }

  public byte[] generatePdf(Object model, String templateContent) {
    try (var outputStream = new ByteArrayOutputStream()) {
      logger.debug("Generare PDF cu TEMPLATE -> DATE {}", objectMapper.writeValueAsString(model));
      writePdfContentToStream(outputStream, getHtml(model, templateContent));
      var pdf = outputStream.toByteArray();
      if (pdf.length == 0) {
        throw new RuntimeException(PDF_GENERATION_EMPTY_FILE);
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(PDF_GENERATION_ERROR, e);
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
            logger.error("Nu s-a putut încărca fontul: {}", file.getName());
          }
        }
      }
    }
  }

  private Document convertHtmlToXHtml(String pdfContent) {
    var document = Jsoup.parse(pdfContent);
    document.outputSettings()
      .syntax(Document.OutputSettings.Syntax.xml)
      .escapeMode(Entities.EscapeMode.xhtml)
      .charset(StandardCharsets.UTF_8);
    return document;
  }

  private String getHtml(Object model, String templateContent) {
    try {
      return mustacheCompiler.defaultValue("-").nullValue("-").compile(templateContent).execute(model);
    } catch (Exception exception) {
      throw new RuntimeException(PDF_GENERATION_ERROR, exception);
    }
  }
}
