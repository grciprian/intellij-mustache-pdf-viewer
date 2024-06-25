package generate;

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextServiceImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import generate.PdfGenerationService.Pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.util.Collections.EMPTY_MAP;

public class Utils {

  public static final String MUSTACHE_TEMPORARY_DIRECTORY = "MTD";
  public static final String MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX = ".mtf.pdf";

  private Utils() {
  }

  // TODO maybe rethink this?
  public static String getTemplatesPath(String modulePath, String mustachePrefix) {
    Objects.requireNonNull(modulePath, "modulePath must not be null");
    try {
      return Path.of(modulePath, "/src/main/resources/%s/".formatted(mustachePrefix)).toFile().getCanonicalPath();
    } catch (IOException e) {
      throw new RuntimeException("Templates folder does not exist");
    }
  }

  public static boolean isFilePathUnderTemplatesPath(String filePath, String templatesPath) {
    return filePath.startsWith(templatesPath);
  }

  public static String getRelativeMustacheFilePathFromTemplatesPath(String filePath, String templatesPath, String mustacheSuffix) {
    var extensionPointIndex = StringUtilRt.lastIndexOf(filePath, '.', 0, filePath.length());
    if (extensionPointIndex < 0) return null;
    var extension = filePath.subSequence(extensionPointIndex + 1, filePath.length());
    if (!mustacheSuffix.contentEquals(extension)) return null;
    return filePath.substring(templatesPath.length(), extensionPointIndex);
  }

  public static Pdf getPdf(String relativeFilePath, String templatesPath, String mustacheSuffix, String moduleName) {
    try {
      var rootOutputPath = Path.of(MUSTACHE_TEMPORARY_DIRECTORY);
      if (Files.notExists(rootOutputPath)) Files.createDirectory(rootOutputPath);
      var moduleOutputPath = Path.of("%s/%s".formatted(MUSTACHE_TEMPORARY_DIRECTORY, moduleName));
      if (Files.notExists(moduleOutputPath)) Files.createDirectory(moduleOutputPath);
      var fileOutputPath = Path.of("%s/%s/%s%s".formatted(MUSTACHE_TEMPORARY_DIRECTORY, moduleName, relativeFilePath.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, '_'), MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX)); // mtf MustacheTemporaryFile
      if (Files.notExists(fileOutputPath)) Files.createFile(fileOutputPath);
      var pdfContent = PdfGenerationService.getInstance(templatesPath, mustacheSuffix).generatePdf(EMPTY_MAP, relativeFilePath);
      Files.write(fileOutputPath, pdfContent.byteArray());
      return new Pdf(VfsUtil.findFile(fileOutputPath, true), pdfContent.structures());
    } catch (IOException exception) {
      throw new RuntimeException("Could not process mustache file into PDF file: " + exception.getMessage());
    }
  }
}
