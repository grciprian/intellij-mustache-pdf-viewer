package generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static generate.Utils.DEFAULT_SUFFIX;
import static generate.Utils.FILE_RESOURCES_PATH_WITH_PREFIX;

public class PdfGenerationService {

  static final String PDF_GENERATION_ERROR = "A apărut o problemă în momentul generării fișierului";
  static final String PDF_GENERATION_EMPTY_FILE = "Fișierul generat este gol. Te rugăm să încerci mai târziu!";
  private static final Logger logger = Logger.getInstance(PdfGenerationService.class);
  private static final Mustache.TemplateLoader TEMPLATE_LOADER = name -> {
    var file = new File(FILE_RESOURCES_PATH_WITH_PREFIX, name + DEFAULT_SUFFIX);
    if (!file.exists()) {
      logger.debug("Faulty partial [" + name + "].");
      return new StringReader("<span style=\"color: red !important;\">[FAULTY_PARTIAL>" + name + "]</span>");
    }
    return new FileReader(file);
  };
  private static PdfGenerationService instance;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Mustache.Compiler mustacheCompiler;

  private PdfGenerationService(Mustache.Compiler compiler) {
    this.mustacheCompiler = compiler;
  }

  public static PdfGenerationService getInstance() {
    if (PdfGenerationService.instance != null) return PdfGenerationService.instance;
    PdfGenerationService.instance = new PdfGenerationService(
      Mustache.compiler()
        .withLoader(TEMPLATE_LOADER)
        .withCollector(new CustomCollector())
    );
    return PdfGenerationService.instance;
  }

  public byte[] generatePdf(Object model, String templateContent) {
    try (var outputStream = new ByteArrayOutputStream()) {
      logger.debug("Generare PDF cu TEMPLATE -> DATE\n" + objectMapper.writeValueAsString(model));
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
    document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).charset(StandardCharsets.UTF_8);
    return document;
  }

  private String getHtml(Object model, String templateContent) {
    try {
      return mustacheCompiler
//        .defaultValue(RandomStringUtils.randomAlphanumeric(5))
        .compile(templateContent)
        .execute(model);
    } catch (Exception exception) {
      throw new RuntimeException(PDF_GENERATION_ERROR, exception);
    }
  }

  private static final class CustomCollector extends DefaultCollector {
    @Override
    public Mustache.VariableFetcher createFetcher(Object ctx, String name) {
      Mustache.VariableFetcher errorFetcher = (ctx1, name1) -> "<span style=\"color: red !important;\">[FAULTY_VAR>" + name1 + "]</span>";
      try {
        var fetcher = super.createFetcher(ctx, name);
        if (Template.NO_FETCHER_FOUND == fetcher.get(ctx, name)) {
          return errorFetcher;
        }
        return fetcher;
      } catch (Exception e) {
        return errorFetcher;
      }
    }
  }

}
