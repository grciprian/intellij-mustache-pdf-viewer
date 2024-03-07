package generate;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static generate.PdfGenerationService.DEFAULT_SUFFIX;
import static generate.PdfGenerationService.getFileResourcesPathWithPrefix;

public class MustacheIncludeProcessor {

  public static Map.Entry<String, IncludeProps> processFileIncludeProps(VirtualFile forFile) {
    Objects.requireNonNull(forFile);
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
        Arrays.stream(contents.split("(\\{\\{>)")).skip(1).forEach(include -> {
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
    var indexOfSuffix = forFile.getCanonicalPath().indexOf(DEFAULT_SUFFIX);
    if (indexOfSuffix == -1) indexOfSuffix = forFile.getCanonicalPath().length();
    var relativePathFromResourcesPath = forFile.getCanonicalPath().substring(fileResourcesPathWithPrefix.length(), indexOfSuffix);
    return includeMap.entrySet().stream().filter(e -> e.getKey().equals(relativePathFromResourcesPath)).findAny()
      .orElseThrow(() -> new RuntimeException("Include map corrupted for " + relativePathFromResourcesPath));
  }

  public static class IncludeProps {

    private final Integer numberOfIncludes;
    private final Set<String> directParents;
    private final Set<String> rootParents = new HashSet<>();

    IncludeProps(Integer numberOfIncludes, Set<String> directParents) {
      this.numberOfIncludes = numberOfIncludes;
      this.directParents = directParents;
    }

    public static IncludeProps getEmpty() {
      return new IncludeProps(0, new HashSet<>());
    }

    public Set<String> getRootParents() {
      return rootParents;
    }

    public void processRootParentsBasedOn(Map<String, IncludeProps> includeMap) {
      Set<String> dp = new HashSet<>(this.directParents);
      while (!dp.isEmpty()) {
        // adauga la rootParents toti directParents care nu mai au directParents
        this.rootParents.addAll(dp.stream().filter(directParent -> includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty()).collect(Collectors.toUnmodifiableSet()));

        // filtreaza directParents care au directParents si devine noul directParents pentru a se duce pe flow in sus pana ajunge la rootParents
        dp = dp.stream().filter(directParent -> !includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty()).map(directParentWithDirectParents -> includeMap.getOrDefault(directParentWithDirectParents, IncludeProps.getEmpty()).directParents).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
      }
    }
  }

}
