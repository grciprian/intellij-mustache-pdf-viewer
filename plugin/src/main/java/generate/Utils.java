package generate;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import generate.PdfGenerationService.Pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.util.Collections.EMPTY_MAP;

public class Utils {

  public static final String MUSTACHE_TEMPORARY_DIRECTORY = "MTD";
  public static final String MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX = "mtf.pdf";

  private Utils() {
  }

  public static String getTemplatesPath(String modulePath, String mustachePrefix) {
    Objects.requireNonNull(modulePath, "modulePath must not be null");
    var templatesFolder = Path.of(modulePath, "src/main/resources/%s".formatted(mustachePrefix)).toFile();
    if (!templatesFolder.exists()) {
      throw new RuntimeException("Templates folder does not exist");
    }
    return templatesFolder.getAbsolutePath();
  }

  public static boolean isFilePathUnderTemplatesPath(String filePath, String templatesPath) {
    return filePath.startsWith(templatesPath + "\\");
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
      var moduleOutputPath = Path.of("%s\\%s".formatted(MUSTACHE_TEMPORARY_DIRECTORY, moduleName));
      if (Files.notExists(moduleOutputPath)) Files.createDirectory(moduleOutputPath);
      var fileOutputPath = Path.of("%s\\%s\\%s.%s".formatted(MUSTACHE_TEMPORARY_DIRECTORY, moduleName, relativeFilePath.replace('\\', '_'), MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX)); // mtf MustacheTemporaryFile
      if (Files.notExists(fileOutputPath)) Files.createFile(fileOutputPath);
      var pdfContent = PdfGenerationService.getInstance(templatesPath, mustacheSuffix).generatePdf(EMPTY_MAP, relativeFilePath);
      Files.write(fileOutputPath, pdfContent.byteArray());
      return new Pdf(VfsUtil.findFile(fileOutputPath, true), pdfContent.structures());
    } catch (IOException exception) {
      throw new RuntimeException("Could not process mustache file into PDF file: " + exception.getMessage());
    }
  }
}
