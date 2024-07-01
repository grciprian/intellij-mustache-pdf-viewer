package generate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Producer;
import com.samskivert.mustache.Mustache;
import generate.PdfGenerationService.Pdf;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static generate.Utils.getPdf;
import static generate.Utils.getRelativeMustacheFilePathFromTemplatesPath;

public class MustacheIncludeProcessor {

  private static final Logger logger = Logger.getInstance(MustacheIncludeProcessor.class);
  private static MustacheIncludeProcessor instance;
  private final Map<String, PdfFileExpirationWrapper> rootPdfFileMap = new HashMap<>();
  private final Map<String, IncludeProps> oldIncludePropsMap = new HashMap<>();
  private final Map<String, IncludeProps> includePropsMap = new HashMap<>();

  private String templatesPath;
  private String mustacheSuffix;

  public MustacheIncludeProcessor setTemplatesPath(String templatesPath) {
    this.templatesPath = templatesPath;
    return this;
  }

  public MustacheIncludeProcessor setMustacheSuffix(String mustacheSuffix) {
    this.mustacheSuffix = mustacheSuffix;
    return this;
  }

  private final String moduleName;

  // be careful to clean it up properly before each template compilation
  private final Set<String> currentlyTemplateLoaderFoundIncludes = new HashSet<>();
  // maybe make this customizable in settings?
  private static final Long RECURSION_THRESHOLD = 500L;
  private Pair<String, Long> recursionCounter = Pair.empty();
  private final BiFunction<String, String, Mustache.TemplateLoader> TEMPLATE_LOADER =
    (templatesPath, mustacheSuffix) -> name -> {
      if (!Objects.equals(name, recursionCounter.first)) {
        recursionCounter = new Pair<>(name, 1L);
      } else {
        recursionCounter = new Pair<>(recursionCounter.first, recursionCounter.second + 1);
      }
      if (recursionCounter.second > RECURSION_THRESHOLD) {
        throw new RuntimeException("Recursion found for included template segment: " + name);
      }
      var file = new File(templatesPath, name + "." + mustacheSuffix);
      if (file.exists()) currentlyTemplateLoaderFoundIncludes.add(name);
      return new StringReader("");
    };
  private final Mustache.Compiler mustacheCompiler;

  private MustacheIncludeProcessor(String moduleName) {
    Objects.requireNonNull(moduleName, "moduleName must not be null");
    this.moduleName = moduleName;
    mustacheCompiler = Mustache.compiler()
      .withLoader(TEMPLATE_LOADER.apply(templatesPath, mustacheSuffix));
//    processFileIncludePropsMap();
  }

  public static MustacheIncludeProcessor getInstance(String templatesPath, String mustacheSuffix, String moduleName) {
    var inst = (Producer<MustacheIncludeProcessor>) () -> instance
      .setTemplatesPath(templatesPath)
      .setMustacheSuffix(mustacheSuffix);
    if (instance != null) return inst.produce();
    instance = new MustacheIncludeProcessor(moduleName);
    return inst.produce();
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
      var relativePath = getRelativeMustacheFilePathFromTemplatesPath(mustacheFile.getCanonicalPath(), templatesPath, mustacheSuffix);
      if (relativePath == null) return true;

      if (!includePropsMap.containsKey(relativePath)) {
        includePropsMap.put(relativePath, IncludeProps.getEmpty());
      }

      try {
        mustacheCompiler.defaultValue("").compile(new FileReader(mustacheFile.getPath())).execute(new Object());
        currentlyTemplateLoaderFoundIncludes
          .forEach(include -> {
            var normalizedInclude = include.replaceAll("/+", "/");
            if (normalizedInclude.startsWith("/")) normalizedInclude = normalizedInclude.substring(1);
            var maybeExistingEntry = includePropsMap.getOrDefault(normalizedInclude, IncludeProps.getEmpty());
            maybeExistingEntry.directParents.add(relativePath);
            includePropsMap.put(normalizedInclude, new IncludeProps(maybeExistingEntry.directParents));
          });
        currentlyTemplateLoaderFoundIncludes.clear();
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

  public Set<String> getOldRootsForMustache(String canonicalFilePath) {
    var relativePath = getRelativeMustacheFilePathFromTemplatesPath(canonicalFilePath, templatesPath, mustacheSuffix);
    return oldIncludePropsMap.entrySet().stream()
      .filter(e -> e.getKey().equals(relativePath)).findAny()
      .map(v -> v.getValue().getRoots())
      .orElse(Set.of());
//      .orElseThrow(() -> new RuntimeException("Include map corrupted for " + file.getCanonicalPath()));
  }

  public Set<String> getRootsForMustache(String canonicalFilePath) {
    var relativePath = getRelativeMustacheFilePathFromTemplatesPath(canonicalFilePath, templatesPath, mustacheSuffix);
    return includePropsMap.entrySet().stream()
      .filter(e -> e.getKey().equals(relativePath)).findAny()
      .map(v -> v.getValue().getRoots())
      .orElse(Set.of());
//      .orElseThrow(() -> new RuntimeException("Include map corrupted for " + file.getCanonicalPath()));
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
      .filter(entry -> Objects.equals(entry.getValue().pdf.file().getCanonicalPath(), pdfFile.getCanonicalPath()))
      .findAny()
      .map(Map.Entry::getKey)
      .orElseGet(() -> {
//        new RuntimeException("No root key found for pdfFile with canonical path: " + pdfFile.getCanonicalPath())
        return null;
      });
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
