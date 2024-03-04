package generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
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
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PdfGenerationService {

  public static final String DEFAULT_PREFIX = "/templates/";
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

  public static String getFileResourcesPathWithPrefix(VirtualFile file) {
    var forFileCanonicalPath = file.getCanonicalPath();
    Objects.requireNonNull(forFileCanonicalPath);
    var resourcesIndex = forFileCanonicalPath.indexOf(JAVA_RESOURCES_FOLDER_NAME);
    if (resourcesIndex != -1)
      return forFileCanonicalPath.substring(0, resourcesIndex + JAVA_RESOURCES_FOLDER_NAME.length()) + DEFAULT_PREFIX;
    throw new RuntimeException("File is not in the resources folder of the java project!");
  }

  public static PdfGenerationService getInstance(VirtualFile forFile) {

    var includeMap = new HashMap<String, IncludeProps>();
    var fileResourcesPathWithPrefix = getFileResourcesPathWithPrefix(forFile);

    var root = VfsUtil.findFile(Path.of(fileResourcesPathWithPrefix), true);
    Objects.requireNonNull(root);
    // TODO tratare daca nu e gasit root resources with prefix
    VfsUtil.processFileRecursivelyWithoutIgnored(root, file -> {
      if (file.isDirectory()) return true;
      var indexOfSuffix = file.getCanonicalPath().indexOf(DEFAULT_SUFFIX);
      if (indexOfSuffix == -1) indexOfSuffix = file.getCanonicalPath().length();
      var relativePathFromResourcesPath = file.getCanonicalPath().substring(fileResourcesPathWithPrefix.length(), indexOfSuffix);
      if (!includeMap.containsKey(relativePathFromResourcesPath)) {
        includeMap.put(relativePathFromResourcesPath, IncludeProps.getEmpty());
      }
      try {
        var contents = VfsUtil.loadText(file);
        Arrays.stream(contents.split("(\\{\\{>)")).skip(1)
          .forEach(include -> {
            var indexOfIncludeEnd = include.indexOf("}}");
            if (indexOfIncludeEnd == -1) throw new RuntimeException("Malformed include found!");
            var cleanedUpInclude = include.substring(0, indexOfIncludeEnd);
            var maybeExistingEntry = includeMap.getOrDefault(cleanedUpInclude, IncludeProps.getEmpty());
            maybeExistingEntry.directParents.add(relativePathFromResourcesPath);
            includeMap.put(cleanedUpInclude, new IncludeProps(maybeExistingEntry.numberOfIncludes + 1, maybeExistingEntry.directParents));
          });
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return true;
    });

    //process and filter main directParents into rootParents
    includeMap.values().forEach(includeProps -> includeProps.processRootParentsBasedOn(includeMap));

    if (PdfGenerationService.pdfGenerationService != null) return PdfGenerationService.pdfGenerationService;
    PdfGenerationService.pdfGenerationService = new PdfGenerationService(
      Mustache.compiler().withLoader(name -> new FileReader(
        new File(fileResourcesPathWithPrefix, name + DEFAULT_SUFFIX))
      )
    );
    return PdfGenerationService.pdfGenerationService;
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

  private static class IncludeProps {

    private final Integer numberOfIncludes;
    private final Set<String> directParents;
    private final Set<String> rootParents = new HashSet<>();

    public Set<String> getRootParents() {
      return rootParents;
    }

    IncludeProps(Integer numberOfIncludes, Set<String> directParents) {
      this.numberOfIncludes = numberOfIncludes;
      this.directParents = directParents;
    }

    public static IncludeProps getEmpty() {
      return new IncludeProps(0, new HashSet<>());
    }

    public void processRootParentsBasedOn(Map<String, IncludeProps> includeMap) {
      Set<String> dp = new HashSet<>(this.directParents);
      while (!dp.isEmpty()) {
        this.rootParents.addAll(
          dp.stream()
            .filter(directParent -> includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty())
            .collect(Collectors.toUnmodifiableSet())
        );
        dp = dp.stream()
          .filter(directParent -> !includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty())
          .collect(Collectors.toUnmodifiableSet());
      }
    }
  }
}
