package generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static com.intellij.openapi.vfs.VfsUtilCore.loadText;
import static java.util.Collections.EMPTY_MAP;

public class Utils {

  public static final String MUSTACHE_DEFAULT_PREFIX = "templates";
  public static final String MUSTACHE_DEFAULT_SUFFIX = "mustache";
  public static final String JAVA_RESOURCES_FOLDER_NAME = "resources";
  public static final String MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX = ".mtf.pdf";

  private Utils() {
  }

  // TODO maybe rethink this?
  // It is called multiple times, for every opened mustache
  public static String getFileResourcesPathWithPrefix(Project project, VirtualFile virtualFile) {
    // maybe this is not the way. maybe another kind of path should be used for system specific
    var projectPath = project.getBasePath();
    var filePath = virtualFile.getCanonicalPath();
    Objects.requireNonNull(projectPath, "Could not getFileResourcesPathWithPrefix because path of project is null!");
    Objects.requireNonNull(filePath, "Could not getFileResourcesPathWithPrefix because path of virtualFile is null!");
    var resourcesIndex = filePath.indexOf(JAVA_RESOURCES_FOLDER_NAME, projectPath.length());
    if (resourcesIndex != -1)
      return filePath.substring(0, resourcesIndex + JAVA_RESOURCES_FOLDER_NAME.length()) + "/" + MUSTACHE_DEFAULT_PREFIX + "/";
    throw new RuntimeException("File is not in the resources folder of the java project!");
  }

  public static String getRelativePathFromResourcePathWithPrefix(String fileResourcesPathWithMustachePrefix, VirtualFile virtualFile) {
    return getRelativePathFromResourcePathWithPrefix(fileResourcesPathWithMustachePrefix, virtualFile.getCanonicalPath());
  }

  public static String getRelativePathFromResourcePathWithPrefix(String fileResourcesPathWithMustachePrefix, String canonicalPath) {
    Objects.requireNonNull(canonicalPath, "Could not getRelativePathFromResourcePathWithPrefix because canonicalPath of virtualFile is null!");
    var indexOfSuffix = canonicalPath.indexOf("." + MUSTACHE_DEFAULT_SUFFIX);
    if (indexOfSuffix == -1) indexOfSuffix = canonicalPath.length();
    return canonicalPath.substring(fileResourcesPathWithMustachePrefix.length(), indexOfSuffix);
  }

  public static VirtualFile getPdfFile(String fileResourcesPathWithMustachePrefix, String simpleFilename) {
    try {
      var path = Path.of(fileResourcesPathWithMustachePrefix + simpleFilename + "." + MUSTACHE_DEFAULT_SUFFIX);
      var virtualFile = VfsUtil.findFile(path, true);
      Objects.requireNonNull(virtualFile, "virtualFile for getPdfFile should not be null! Path: " + path);
      var pdfByteArray = PdfGenerationService.getInstance().generatePdf(EMPTY_MAP, loadText(virtualFile));
      var outputPath = Path.of(simpleFilename.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, '_') + MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX); // mtf MustacheTemporaryFile
      if (!Files.exists(outputPath)) {
        Files.createFile(outputPath);
      }
      Files.write(outputPath, pdfByteArray);
      return VfsUtil.findFile(outputPath, true);
    } catch (IOException exception) {
      throw new RuntimeException("Could not process mustache file into PDF file: " + exception.getMessage());
    }
  }
}
