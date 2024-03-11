package generate;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static generate.Utils.*;


public class MustacheIncludeProcessor {

  private static MustacheIncludeProcessor instance;
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
        var contents = VfsUtil.loadText(virtualFile);
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

    //process and filter main directParents into rootParents
    includePropsMap.values().forEach(includeProps -> includeProps.processRootParentsBasedOn(includePropsMap));

    //orice includeProp daca nu are rootParents atunci este rootParent si il adaugam ca atare
    includePropsMap.forEach((name, includeProps) -> {
      if (includeProps.rootParents.isEmpty()) {
        includeProps.rootParents.add(new RootParent(name));
      }
    });
  }

  public Optional<Map.Entry<String, IncludeProps>> getFileIncludePropsForFile(VirtualFile virtualFile) {
    return getFileIncludePropsForFile(virtualFile.getCanonicalPath());
  }

  public Optional<Map.Entry<String, IncludeProps>> getFileIncludePropsForFile(String canonicalPath) {
    var relativePathFromResourcePathWithPrefix = getRelativePathFromResourcePathWithPrefix(canonicalPath);
    return includePropsMap.entrySet().stream().filter(e -> e.getKey().equals(relativePathFromResourcePathWithPrefix)).findAny();
  }

  public Map<String, IncludeProps> getIncludePropsMap() {
    return includePropsMap;
  }

  public Set<String> getRoots() {
    return includePropsMap.entrySet().stream().filter(stringIncludePropsEntry -> stringIncludePropsEntry.getValue().getRootParents().isEmpty()).map(Map.Entry::getKey).collect(Collectors.toSet());
  }

  public static class IncludeProps {
    private final Set<String> directParents;
    private final Set<RootParent> rootParents = new HashSet<>();

    IncludeProps(Set<String> directParents) {
      this.directParents = directParents;
    }

    public static IncludeProps getEmpty() {
      return new IncludeProps(new HashSet<>());
    }

    public Set<RootParent> getRootParents() {
      return rootParents;
    }

    public Set<String> getRootParentsNames() {
      return rootParents.stream().map(RootParent::getSimpleFilename).collect(Collectors.toSet());
    }

    public void processRootParentsBasedOn(Map<String, IncludeProps> includeMap) {
      Set<String> dp = new HashSet<>(this.directParents);
      while (!dp.isEmpty()) {
        // adauga la rootParents toti directParents care nu mai au directParents
        this.rootParents.addAll(dp.stream().filter(directParent -> includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty()).map(RootParent::new).collect(Collectors.toUnmodifiableSet()));

        // filtreaza directParents care au directParents si devine noul directParents pentru a se duce pe flow in sus pana ajunge la rootParents
        dp = dp.stream().filter(directParent -> !includeMap.getOrDefault(directParent, IncludeProps.getEmpty()).directParents.isEmpty()).map(directParentWithDirectParents -> includeMap.getOrDefault(directParentWithDirectParents, IncludeProps.getEmpty()).directParents).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
      }
    }
  }

  public static class RootParent {
    private final String simpleFilename;
    private VirtualFile attachedPdf;

    RootParent(String simpleFilename) {
      this.simpleFilename = simpleFilename;
    }

    public String getSimpleFilename() {
      return simpleFilename;
    }

    public String getCanonicalPath() {
      return FILE_RESOURCES_PATH_WITH_PREFIX + simpleFilename + DEFAULT_SUFFIX;
    }

    public VirtualFile getAttachedPdf() {
      return attachedPdf;
    }

    public void setAttachedPdf(VirtualFile attachedPdf) {
      this.attachedPdf = attachedPdf;
    }
  }

}
