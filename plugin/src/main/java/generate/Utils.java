package generate;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import generate.PdfGenerationService.Pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorProviderKt.MUSTACHE_PREFIX;
import static com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorProviderKt.MUSTACHE_SUFFIX;
import static java.util.Collections.EMPTY_MAP;

public class Utils {

  public static final String MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX = ".mtf.pdf";

  private Utils() {
  }

  // TODO maybe rethink this?
  public static String getTemplatesPath(Module module) {
    // maybe this is not the way. maybe another kind of path should be used for system specific
//    var module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(VfsUtil.findFile(Path.of(canonicalFilePath), true));
    var moduleContentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (moduleContentRoots.length == 0) throw new RuntimeException("Could not get root module!");
    var moduleCanonicalPath = moduleContentRoots[0].getCanonicalPath();
    Objects.requireNonNull(moduleCanonicalPath, "moduleCanonicalPath must not be null");
    var moduleTemplatesPath = Path.of(moduleCanonicalPath, "/src/main/resources/" + MUSTACHE_PREFIX + "/");
    try {
      return moduleTemplatesPath.toFile().getCanonicalPath();
    } catch (IOException e) {
      throw new RuntimeException("Templates folder does not exist!");
    }
  }

  public static boolean isFilePathUnderTemplatesPath(String fileCanonicalPath, String templatesCanonicalPath) {
    return fileCanonicalPath.startsWith(templatesCanonicalPath);
  }

  public static String getRelativeMustacheFilePathFromTemplatesPath(String fileCanonicalPath, String templatesCanonicalPath) {
    var extensionPointIndex = StringUtilRt.lastIndexOf(fileCanonicalPath, '.', 0, fileCanonicalPath.length());
    if (extensionPointIndex < 0) return null;
    var extension = fileCanonicalPath.subSequence(extensionPointIndex + 1, fileCanonicalPath.length());
    if (!MUSTACHE_SUFFIX.contentEquals(extension)) return null;
    return fileCanonicalPath.substring(templatesCanonicalPath.length(), extensionPointIndex);
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
