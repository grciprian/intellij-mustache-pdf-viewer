package generate;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import generate.PdfGenerationService.Pdf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;
import static java.util.Collections.EMPTY_MAP;

public class Utils {

  private Utils() {
  }

//  public static VirtualFile getTemplatesDir(VirtualFile moduleDir, String mustachePrefix) {
//    Objects.requireNonNull(moduleDir, "moduleDir must not be null");
//    var templatesFolder = VfsUtil.findRelativeFile(moduleDir, "src", "main", "resources", mustachePrefix);
//    if (templatesFolder == null || !templatesFolder.exists()) {
//      throw new TemplatesFolderNotFoundException("Templates folder does not exist");
//    }
//    return templatesFolder;
//  }

  public static String getRelativeMustacheFilePathFromTemplatesPath(String filePath, String templatesPath, String mustacheSuffix) {
    var extensionPointIndex = StringUtilRt.lastIndexOf(filePath, '.', 0, filePath.length());
    if (extensionPointIndex < 0) return null;
    var extension = filePath.subSequence(extensionPointIndex + 1, filePath.length());
    if (!mustacheSuffix.contentEquals(extension)) return null;
    return filePath.substring(templatesPath.length() + 1, extensionPointIndex);
  }

  public static Pdf getPdf(String relativeFilePath, String templatesPath, String mustacheSuffix, String fontsPath, String moduleName) {
    try {
      var tempDir = FileUtil.getTempDirectory();
      var tempFile = new File(tempDir, "%s-%s.%s".formatted(moduleName, relativeFilePath.replace(VFS_SEPARATOR_CHAR, '_'), "mtf.pdf"));
      tempFile.deleteOnExit();
      var pdfContent = PdfGenerationService.getInstance(templatesPath, mustacheSuffix, fontsPath).generatePdf(EMPTY_MAP, relativeFilePath);
      var pdfFilePath = Files.write(tempFile.toPath(), pdfContent.byteArray());
      return new Pdf(pdfFilePath, pdfContent.structures());
    } catch (IOException exception) {
      throw new RuntimeException("Could not process mustache file into PDF file: " + exception.getMessage(), exception);
    }
  }
}
