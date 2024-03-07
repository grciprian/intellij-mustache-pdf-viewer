package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import generate.MustacheIncludeProcessor.processFileIncludeProps
import generate.PdfGenerationService
import generate.PdfGenerationService.DEFAULT_SUFFIX
import generate.PdfGenerationService.getFileResourcesPathWithPrefix
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

class PdfFileEditorWrapper(project: Project, virtualFile: VirtualFile) : FileEditorBase(), DumbAware {

  private val fileIncludeEntry = processFileIncludeProps(virtualFile)
  private val jbTabbedPane = JBTabbedPane()

  init {
    val fileResourcesPathWithPrefix = getFileResourcesPathWithPrefix(virtualFile)
    var fileToBeProcessed: String = fileIncludeEntry.key;
    if (fileIncludeEntry.value.rootParents.isNotEmpty()) {
      fileToBeProcessed = fileIncludeEntry.value.rootParents.first()
    }
    val rootMustacheFile = VfsUtil.findFile(Path.of(fileResourcesPathWithPrefix + fileToBeProcessed + DEFAULT_SUFFIX), true)
    val processedPdfFile = getProcessedPdfFile(rootMustacheFile!!)
    val editor = PdfFileEditor(project, processedPdfFile!!) // treat processedPdfFile null case
    jbTabbedPane.insertTab(fileToBeProcessed, null, editor.component, null, 0)
  }

  override fun getComponent(): JComponent = jbTabbedPane

  override fun getName(): String = NAME

  override fun getPreferredFocusedComponent(): JComponent = getActiveTab()

  private fun getActiveTab(): PdfEditorViewComponent {
    return jbTabbedPane.selectedComponent as PdfEditorViewComponent
  }

  companion object {
    private const val TEMP_PDF_NAME = "temp.pdf"
    private const val NAME = "Pdf Wrapper Viewer File Editor"
    private val logger = logger<PdfFileEditorWrapper>()

    fun getProcessedPdfFile(file: VirtualFile): VirtualFile? {
      val pdfByteArray = PdfGenerationService.getInstance(file).generatePdf(HashMap<String, String>(), VfsUtil.loadText(file))
      val outputPath = Path.of(TEMP_PDF_NAME)
      if (!Files.exists(outputPath)) {
        Files.createFile(outputPath)
      }
      Files.write(outputPath, pdfByteArray)
      return VfsUtil.findFile(outputPath, true)
    }
  }
}
