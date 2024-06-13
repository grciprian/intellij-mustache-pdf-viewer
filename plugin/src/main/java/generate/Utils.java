package generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
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
  public static String getTemplatesPath(Project project, VirtualFile virtualFile) {
    // maybe this is not the way. maybe another kind of path should be used for system specific
    var projectPath = project.getBasePath();
    var filePath = virtualFile.getCanonicalPath();
    Objects.requireNonNull(projectPath, "Could not getFileResourcesPathWithPrefix because path of project is null!");
    Objects.requireNonNull(filePath, "Could not getFileResourcesPathWithPrefix because path of virtualFile is null!");
    var javaResourcesWithMustachePrefix = "/resources/" + MUSTAHCE_PREFIX + "/";
    var resourcesIndex = filePath.indexOf(javaResourcesWithMustachePrefix, projectPath.length());
    if (resourcesIndex != -1) return filePath.substring(0, resourcesIndex + javaResourcesWithMustachePrefix.length());
    throw new RuntimeException("File is not in the resources folder of the java project!");
  }

  private static boolean isFilePathUnderTemplatesPath(Project project, VirtualFile virtualFile) {
    try {
      if(virtualFile == null) return false;
      getTemplatesPath(project, virtualFile);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static String getRelativeFilePathFromTemplatesPath(Project project, VirtualFile virtualFile) {
    if (!isFilePathUnderTemplatesPath(project, virtualFile)) return null;
    var canonicalPath = virtualFile.getCanonicalPath();
    Objects.requireNonNull(canonicalPath, "Could not getRelativePathFromResourcePathWithPrefix because canonicalPath of virtualFile is null!");
    var extensionPointIndex = StringUtilRt.lastIndexOf(canonicalPath, '.', 0, canonicalPath.length());
    if (extensionPointIndex < 0) return null;
    var extension = canonicalPath.subSequence(extensionPointIndex + 1, canonicalPath.length());
    if (!MUSTACHE_SUFFIX.contentEquals(extension)) return null;
    return canonicalPath.substring(TEMPLATES_PATH.length(), extensionPointIndex);
  }

  public static Pdf getPdf(Project project, String filename) {
    try {
      var path = Path.of(TEMPLATES_PATH + filename + "." + MUSTACHE_SUFFIX);
      var mustacheFile = VfsUtil.findFile(path, true);
      Objects.requireNonNull(mustacheFile, "mustacheFile for getPdfFile should not be null! Path: " + path);
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
