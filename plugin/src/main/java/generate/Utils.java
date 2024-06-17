package generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import generate.PdfGenerationService.Pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorProviderKt.*;
import static java.util.Collections.EMPTY_MAP;

public class Utils {

  public static final String MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX = ".mtf.pdf";

  private Utils() {
  }

  // TODO maybe rethink this?
  public static String getTemplatesPath(Project project, String canonicalFilePath) {
    // maybe this is not the way. maybe another kind of path should be used for system specific
    var projectPath = project.getBasePath();
    Objects.requireNonNull(projectPath, "Could not getFileResourcesPathWithPrefix because path of project is null!");
    Objects.requireNonNull(canonicalFilePath, "Could not getFileResourcesPathWithPrefix because path of virtualFile is null!");
    var javaResourcesWithMustachePrefix = "/resources/" + MUSTACHE_PREFIX + "/";
    var resourcesIndex = canonicalFilePath.indexOf(javaResourcesWithMustachePrefix, projectPath.length());
    if (resourcesIndex != -1) return canonicalFilePath.substring(0, resourcesIndex + javaResourcesWithMustachePrefix.length());
    throw new RuntimeException("File is not in the resources folder of the java project!");
  }

  public static boolean isFilePathUnderTemplatesPath(Project project, String canonicalFilePath) {
    try {
      if(canonicalFilePath == null) return false;
      getTemplatesPath(project, canonicalFilePath);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static String getRelativeMustacheFilePathFromTemplatesPath(Project project, String canonicalFilePath) {
    if (!isFilePathUnderTemplatesPath(project, canonicalFilePath)) return null;
    Objects.requireNonNull(canonicalFilePath, "Could not getRelativePathFromResourcePathWithPrefix because canonicalPath of virtualFile is null!");
    var extensionPointIndex = StringUtilRt.lastIndexOf(canonicalFilePath, '.', 0, canonicalFilePath.length());
    if (extensionPointIndex < 0) return null;
    var extension = canonicalFilePath.subSequence(extensionPointIndex + 1, canonicalFilePath.length());
    if (!MUSTACHE_SUFFIX.contentEquals(extension)) return null;
    return canonicalFilePath.substring(TEMPLATES_PATH.length(), extensionPointIndex);
  }

  public static Pdf getPdf(Project project, String filename) {
    try {
      var path = Path.of(TEMPLATES_PATH + filename + "." + MUSTACHE_SUFFIX);
      var mustacheFile = VfsUtil.findFile(path, true);
      Objects.requireNonNull(mustacheFile, "mustacheFile for getPdfFile should not be null: " + path);
      var pdfContent = PdfGenerationService.getInstance().generatePdf(project, EMPTY_MAP, mustacheFile);
      var outputPath = Path.of(filename.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, '_') + MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX); // mtf MustacheTemporaryFile
      if (!Files.exists(outputPath)) Files.createFile(outputPath);
      Files.write(outputPath, pdfContent.byteArray());
      return new Pdf(VfsUtil.findFile(outputPath, true), pdfContent.structures());
    } catch (IOException exception) {
      throw new RuntimeException("Could not process mustache file into PDF file: " + exception.getMessage());
    }
  }
}
