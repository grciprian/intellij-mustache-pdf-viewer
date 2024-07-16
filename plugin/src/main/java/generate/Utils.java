package generate;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import generate.PdfGenerationService.Pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;
import static java.util.Collections.EMPTY_MAP;

public class Utils {

  public static final String MUSTACHE_TEMPORARY_DIRECTORY = "MTD";
  public static final String MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX = "mtf.pdf";

  private Utils() {
  }

  public static VirtualFile getTemplatesDir(VirtualFile moduleDir, String mustachePrefix) {
    Objects.requireNonNull(moduleDir, "moduleDir must not be null");
    var templatesFolder = VfsUtil.findRelativeFile(moduleDir, "src", "main", "resources", mustachePrefix);
    if (templatesFolder == null || !templatesFolder.exists()) {
      throw new RuntimeException("Templates folder does not exist");
    }
    return templatesFolder;
  }

  public static String getRelativeMustacheFilePathFromTemplatesPath(String filePath, String templatesPath, String mustacheSuffix) {
    var extensionPointIndex = StringUtilRt.lastIndexOf(filePath, '.', 0, filePath.length());
    if (extensionPointIndex < 0) return null;
    var extension = filePath.subSequence(extensionPointIndex + 1, filePath.length());
    if (!mustacheSuffix.contentEquals(extension)) return null;
    return filePath.substring(templatesPath.length() + 1, extensionPointIndex);
  }

  public static Pdf getPdf(String relativeFilePath, String templatesPath, String mustacheSuffix, String moduleName) {
    try {
      var rootOutputPath = Path.of(MUSTACHE_TEMPORARY_DIRECTORY);
      if (Files.notExists(rootOutputPath)) Files.createDirectory(rootOutputPath);
      var moduleOutputPath = Path.of("%s/%s".formatted(MUSTACHE_TEMPORARY_DIRECTORY, moduleName));
      if (Files.notExists(moduleOutputPath)) Files.createDirectory(moduleOutputPath);
      var fileOutputPath = Path.of("%s/%s/%s.%s".formatted(MUSTACHE_TEMPORARY_DIRECTORY, moduleName, relativeFilePath.replace(VFS_SEPARATOR_CHAR, '_'), MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX)); // mtf MustacheTemporaryFile
      if (Files.notExists(fileOutputPath)) Files.createFile(fileOutputPath);
      var pdfContent = PdfGenerationService.getInstance(templatesPath, mustacheSuffix).generatePdf(EMPTY_MAP, relativeFilePath);
      Files.write(fileOutputPath, pdfContent.byteArray());
      return new Pdf(VfsUtil.findFile(fileOutputPath, true), pdfContent.structures());
    } catch (IOException exception) {
      throw new RuntimeException("Could not process mustache file into PDF file: " + exception.getMessage());
    }
  }
}
