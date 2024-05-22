package generate;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.vfs.VfsUtilCore.loadText;
import static generate.Utils.*;

public class MustacheIncludeProcessor {

  private static MustacheIncludeProcessor instance;
  private final Map<String, PdfFileExpirationWrapper> rootPdfFileMap = new HashMap<>();
  private final Map<String, MustacheIncludeProcessor.IncludeProps> includePropsMap = new HashMap<>();

  private MustacheIncludeProcessor() {
    Objects.requireNonNull(FILE_RESOURCES_PATH_WITH_PREFIX, "FILE_RESOURCES_PATH_WITH_PREFIX must not be null!");
    processFileIncludePropsMap();
  }

  public static MustacheIncludeProcessor getInstance() {
    if (MustacheIncludeProcessor.instance != null) return MustacheIncludeProcessor.instance;
    MustacheIncludeProcessor.instance = new MustacheIncludeProcessor();
    return MustacheIncludeProcessor.instance;
  }

  public void processFileIncludePropsMap() {
    includePropsMap.clear();
    var root = VfsUtil.findFile(Path.of(FILE_RESOURCES_PATH_WITH_PREFIX), true);
    Objects.requireNonNull(root, "Root folder FILE_RESOURCES_PATH_WITH_PREFIX " + FILE_RESOURCES_PATH_WITH_PREFIX + " not found!");
    // TODO alta tratare daca nu e gasit root resources with prefix?
    VfsUtil.processFileRecursivelyWithoutIgnored(root, virtualFile -> {
      if (virtualFile.isDirectory()) return true;
      var relativePathFromResourcePathWithPrefix = getRelativePathFromResourcePathWithPrefix(virtualFile);
      if (!includePropsMap.containsKey(relativePathFromResourcePathWithPrefix)) {
        includePropsMap.put(relativePathFromResourcePathWithPrefix, IncludeProps.getEmpty());
      }
      try {
        var contents = loadText(virtualFile);
        Arrays.stream(contents.split("(\\{\\{>)")).skip(1).forEach(include -> {
          var indexOfIncludeEnd = include.indexOf("}}");
          if (indexOfIncludeEnd == -1) throw new RuntimeException("Malformed include found!");
          var cleanedUpInclude = include.substring(0, indexOfIncludeEnd);
          var maybeExistingEntry = includePropsMap.getOrDefault(cleanedUpInclude, IncludeProps.getEmpty());
          maybeExistingEntry.directParents.add(relativePathFromResourcePathWithPrefix);
          includePropsMap.put(cleanedUpInclude, new IncludeProps(maybeExistingEntry.directParents));
        });
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return true;
    });

    //process and filter main directParents into roots
    includePropsMap.values().forEach(includeProps -> includeProps.processRootParentsBasedOn(includePropsMap));

    //orice includeProp daca nu are roots atunci este root si il adaugam ca atare
    var newRootPdfFileMap = new HashMap<String, VirtualFile>();
    includePropsMap.forEach((name, includeProps) -> {
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

  public Set<String> getRootsForMustacheFile(VirtualFile mustacheFile) {
    return getRootsForMustacheFile(mustacheFile.getCanonicalPath());
  }

  public Set<String> getRootsForMustacheFile(String mustacheFileCanonicalPath) {
    var relativePathFromResourcePathWithPrefix = getRelativePathFromResourcePathWithPrefix(mustacheFileCanonicalPath);
    return includePropsMap.entrySet().stream()
      .filter(e -> e.getKey().equals(relativePathFromResourcePathWithPrefix)).findAny()
      .map(v -> v.getValue().getRoots())
      .orElseThrow(() -> new RuntimeException("Include map corrupted for " + mustacheFileCanonicalPath));
  }

  public void tryInvalidateRootPdfFilesForMustacheFileRoots(Set<String> mustacheFileRoots) {
    mustacheFileRoots.stream()
      .filter(rootPdfFileMap::containsKey)
      .forEach(v -> rootPdfFileMap.get(v).expired = true);
  }

  public VirtualFile processRootPdfFile(String rootName) {
//    if (Optional.ofNullable(rootPdfFileMap.get(rootName)).map(v -> v.expired).orElse(true)) {
    if (rootPdfFileMap.get(rootName) == null || (rootPdfFileMap.get(rootName) != null && rootPdfFileMap.get(rootName).expired)) {
      var pdfFile = getPdfFile(rootName);
      rootPdfFileMap.put(rootName, new PdfFileExpirationWrapper(pdfFile));
    }
    return rootPdfFileMap.get(rootName).pdfFile; // same as pdfFile
  }

  public String getRootForPdfFile(VirtualFile pdfFile) {
    return rootPdfFileMap.entrySet().stream()
      .filter(entry -> entry.getValue() != null)
      .filter(entry -> Objects.equals(entry.getValue().pdfFile.getCanonicalPath(), pdfFile.getCanonicalPath()))
      .findAny()
      .map(Map.Entry::getKey)
      .orElseThrow(() -> new RuntimeException("No root key found for pdfFile with canonical path: " + pdfFile.getCanonicalPath()));
  }

  public static class PdfFileExpirationWrapper {

    VirtualFile pdfFile;
    boolean expired;

    PdfFileExpirationWrapper(VirtualFile pdfFile) {
      this.pdfFile = pdfFile;
      this.expired = false;
    }

    @Override
    public String toString() {
      return "PdfFileExpirationWrapper{" +
             "pdfFile=" + pdfFile +
             ", expired=" + expired +
             '}';
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
        dp = dp.stream().filter(directParent -> !includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty()).map(directParentWithDirectParents -> includeMap.getOrDefault(directParentWithDirectParents, IncludeProps.getEmpty()).directParents).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
      }
    }
  }
}
