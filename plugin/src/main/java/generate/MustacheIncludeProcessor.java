package generate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.samskivert.mustache.Mustache;
import generate.PdfGenerationService.Pdf;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static generate.Utils.getPdf;
import static generate.Utils.getRelativeMustacheFilePathFromTemplatesPath;

public class MustacheIncludeProcessor {

  private static final Logger logger = Logger.getInstance(MustacheIncludeProcessor.class);
  // maybe make this customizable in settings?
  private static final Long RECURSION_THRESHOLD = 500L;
  private static MustacheIncludeProcessor instance;
  private final Map<String, PdfFileExpirationWrapper> rootPdfFileMap = new HashMap<>();
  private final Map<String, IncludeProps> oldIncludePropsMap = new HashMap<>();
  private final Map<String, IncludeProps> includePropsMap = new HashMap<>();
  private final String templatesPath;
  private final String mustacheSuffix;
  private final String moduleName;
  // be careful to clean it up properly before each template compilation
  private final Set<String> currentlyTemplateLoaderFoundIncludesNormalized = new HashSet<>();
  private final Mustache.Compiler mustacheCompiler;
  private Pair<String, Long> recursionCounter = Pair.empty();

  private MustacheIncludeProcessor(String templatesPath, String mustacheSuffix, String moduleName) {
    Objects.requireNonNull(moduleName, "moduleName must not be null");
    this.templatesPath = templatesPath;
    this.mustacheSuffix = mustacheSuffix;
    this.moduleName = moduleName;
    this.mustacheCompiler = Mustache.compiler()
      .withLoader(name -> {
        if (!Objects.equals(name, recursionCounter.first)) {
          recursionCounter = new Pair<>(name, 1L);
        } else {
          recursionCounter = new Pair<>(recursionCounter.first, recursionCounter.second + 1);
        }
        if (recursionCounter.second > RECURSION_THRESHOLD) {
          throw new RuntimeException("Recursion found for included template segment: " + name);
        }
        var file = new File(templatesPath, name + "." + mustacheSuffix);
        var normalizedName = FileUtil.normalize(name).replaceAll("^/+", "");
        if (file.exists()) currentlyTemplateLoaderFoundIncludesNormalized.add(normalizedName);
        return new StringReader("");
      });
  }

  public static MustacheIncludeProcessor getInstance(String templatesPath, String mustacheSuffix, String moduleName) {
    if (instance != null
      && Objects.equals(instance.templatesPath, templatesPath)
      && Objects.equals(instance.mustacheSuffix, mustacheSuffix)
      && Objects.equals(instance.moduleName, moduleName)) return instance;
    return instance = new MustacheIncludeProcessor(templatesPath, mustacheSuffix, moduleName);
  }

  public void processFileIncludePropsMap() {
    oldIncludePropsMap.clear();
    oldIncludePropsMap.putAll(includePropsMap);
    includePropsMap.clear();
    var root = VfsUtil.findFile(Path.of(templatesPath), true);
    // TODO refactor includes retrieval
    VfsUtil.processFileRecursivelyWithoutIgnored(root, mustacheFile -> {
      if (mustacheFile.isDirectory()) {
        return true;
      }
      var relativePath = getRelativeMustacheFilePathFromTemplatesPath(mustacheFile.getPath(), templatesPath, mustacheSuffix);
      if (relativePath == null) return true;

      if (!includePropsMap.containsKey(relativePath)) {
        includePropsMap.put(relativePath, IncludeProps.getEmpty());
      }

      try {
        mustacheCompiler.defaultValue("").compile(new FileReader(mustacheFile.getPath())).execute(new Object());
        currentlyTemplateLoaderFoundIncludesNormalized
          .forEach(include -> {
            var maybeExistingEntry = includePropsMap.getOrDefault(include, IncludeProps.getEmpty());
            maybeExistingEntry.directParents.add(relativePath);
            includePropsMap.put(include, new IncludeProps(maybeExistingEntry.directParents));
          });
        currentlyTemplateLoaderFoundIncludesNormalized.clear();
      } catch (IOException e) {
        //TODO: customize this
        throw new RuntimeException(e);
      }

      return true;
    });

    //process and filter main directParents into roots
    includePropsMap.values().forEach(includeProps -> includeProps.processRootParentsBasedOn(includePropsMap));

    //orice includeProp daca nu are roots atunci este root si il adaugam ca atare
    var newRootPdfFileMap = new HashMap<String, VirtualFile>();
    includePropsMap.forEach((name, includeProps) -> {
      //daca este root atunci roots in punctul asta e empty si el este rootul lui
      if (includeProps.roots.isEmpty()) {
        includeProps.roots.add(name);
        //de asemenea se actualizeaza rootPdfFileMap
        if (!rootPdfFileMap.containsKey(name)) {
          rootPdfFileMap.put(name, null);
        }
        newRootPdfFileMap.put(name, null);
      }
    });
    //clean expired roots
    rootPdfFileMap.keySet().stream()
      .filter(rootName -> !newRootPdfFileMap.containsKey(rootName))
      .collect(Collectors.toUnmodifiableSet())
      .forEach(rootPdfFileMap::remove);
  }

  public Set<String> getOldRootsForMustache(String filePath) {
    var relativePath = getRelativeMustacheFilePathFromTemplatesPath(filePath, templatesPath, mustacheSuffix);
    return oldIncludePropsMap.entrySet().stream()
      .filter(e -> e.getKey().equals(relativePath)).findAny()
      .map(v -> v.getValue().getRoots())
      .orElse(Set.of());
  }

  public Set<String> getRootsForMustache(String filePath) {
    var relativePath = getRelativeMustacheFilePathFromTemplatesPath(filePath, templatesPath, mustacheSuffix);
    return includePropsMap.entrySet().stream()
      .filter(e -> e.getKey().equals(relativePath)).findAny()
      .map(v -> v.getValue().getRoots())
      .orElse(Set.of());
  }

  public void invalidateRootPdfs() {
    rootPdfFileMap
      .values().stream()
      .filter(Objects::nonNull)
      .forEach(pdfFileExpirationWrapper -> pdfFileExpirationWrapper.expired = true);
  }

  public void invalidateRootPdfsForMustacheRoots(Set<String> roots) {
    roots.stream()
      .filter(rootPdfFileMap::containsKey)
      .forEach(v -> {
        var pdfFileExpirationWrapper = rootPdfFileMap.get(v);
        if (pdfFileExpirationWrapper != null) pdfFileExpirationWrapper.expired = true;
      });
  }

  public VirtualFile processPdfFileForMustacheRoot(String root) {
    if (rootPdfFileMap.get(root) == null || rootPdfFileMap.get(root).expired) {
      var pdf = getPdf(root, templatesPath, mustacheSuffix, moduleName);
      rootPdfFileMap.put(root, new PdfFileExpirationWrapper(pdf));
    }
    return rootPdfFileMap.get(root).pdf.file();
  }

  // maybe rethink this flow
  public String getMustacheRootForPdfFile(VirtualFile pdfFile) throws RuntimeException {
    return rootPdfFileMap.entrySet().stream()
      .filter(entry -> entry.getValue() != null)
      .filter(entry -> Objects.equals(entry.getValue().pdf.file().getPath(), pdfFile.getPath()))
      .findAny()
      .map(Map.Entry::getKey)
      .orElseGet(() -> null);
  }

  public Pdf getPdfForRoot(String root) {
    if (!rootPdfFileMap.containsKey(root)) {
      logger.warn("The root provided may not actually be a root!");
      return null;
    }
    return Optional.ofNullable(rootPdfFileMap.get(root))
      .map(pdfFileExpirationWrapper -> pdfFileExpirationWrapper.pdf)
      .orElse(null);
  }

  public static class PdfFileExpirationWrapper {
    Pdf pdf;
    boolean expired;

    PdfFileExpirationWrapper(Pdf pdf) {
      this.pdf = pdf;
      this.expired = false;
    }
  }

  public static class IncludeProps {
    private final Set<String> directParents;
    private final Set<String> roots = new HashSet<>();

    IncludeProps(Set<String> directParents) {
      this.directParents = directParents;
    }

    public static IncludeProps getEmpty() {
      return new IncludeProps(new HashSet<>());
    }

    public Set<String> getRoots() {
      return roots.stream().collect(Collectors.toUnmodifiableSet());
    }

    public void processRootParentsBasedOn(Map<String, IncludeProps> includeMap) {
      Set<String> dp = new HashSet<>(this.directParents);
      while (!dp.isEmpty()) {
        // adauga la rootParents toti directParents care nu mai au directParents
        this.roots.addAll(dp.stream().filter(directParent -> includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty()).collect(Collectors.toUnmodifiableSet()));

        // filtreaza directParents care au directParents si devine noul directParents pentru a se duce pe flow in sus pana ajunge la rootParents
        Set<String> finalDp = new HashSet<>(dp);
        dp = dp.stream()
          .filter(directParent -> !includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty())
          .map(directParentWithDirectParents -> includeMap.getOrDefault(directParentWithDirectParents, IncludeProps.getEmpty()).directParents)
          .flatMap(Set::stream)
          .filter(directParent -> !finalDp.contains(directParent))
          .collect(Collectors.toUnmodifiableSet());
      }
    }
  }
}
