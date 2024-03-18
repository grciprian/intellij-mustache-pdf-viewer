package generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;

public class Utils {

  public static final String DEFAULT_PREFIX = "/templates/";
  public static final String DEFAULT_SUFFIX = ".mustache";
  public static final String JAVA_RESOURCES_FOLDER_NAME = "resources";
  public static String FILE_RESOURCES_PATH_WITH_PREFIX;

  private Utils() {
  }

  //TODO maybe rethink this?
  public static String getFileResourcesPathWithPrefix(Project project, VirtualFile virtualFile) {
    var canonicalPath = virtualFile.getCanonicalPath();
    Objects.requireNonNull(canonicalPath, "Could not getFileResourcesPathWithPrefix because canonicalPath of virtualFile is null!");
    var resourcesIndex = canonicalPath.lastIndexOf(JAVA_RESOURCES_FOLDER_NAME); //TODO maybe find a better solution? use project's path for calculation?
    if (resourcesIndex != -1) return canonicalPath.substring(0, resourcesIndex + JAVA_RESOURCES_FOLDER_NAME.length()) + DEFAULT_PREFIX;
    throw new RuntimeException("File is not in the resources folder of the java project!");
  }

  public static String getRelativePathFromResourcePathWithPrefix(VirtualFile virtualFile) {
    return getRelativePathFromResourcePathWithPrefix(virtualFile.getCanonicalPath());
  }

  public static String getRelativePathFromResourcePathWithPrefix(String canonicalPath) {
    Objects.requireNonNull(canonicalPath, "Could not getRelativePathFromResourcePathWithPrefix because canonicalPath of virtualFile is null!");
    var indexOfSuffix = canonicalPath.indexOf(DEFAULT_SUFFIX);
    if (indexOfSuffix == -1) indexOfSuffix = canonicalPath.length();
    return canonicalPath.substring(FILE_RESOURCES_PATH_WITH_PREFIX.length(), indexOfSuffix);
  }

  public static VirtualFile getPdfFile(String simpleFilename) {
    try {
      var path = Path.of(FILE_RESOURCES_PATH_WITH_PREFIX + simpleFilename + DEFAULT_SUFFIX);
      var virtualFile = VfsUtil.findFile(path, true);
      Objects.requireNonNull(virtualFile, "virtualFile for getProcessedPdfFile should not be null! Path: " + path);
      var pdfByteArray = PdfGenerationService.getInstance(FILE_RESOURCES_PATH_WITH_PREFIX).generatePdf(Collections.EMPTY_MAP, VfsUtil.loadText(virtualFile));
      var outputPath = Path.of(simpleFilename.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, '_') + ".mtf.pdf"); // mtf MustacheTemporaryFile
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
