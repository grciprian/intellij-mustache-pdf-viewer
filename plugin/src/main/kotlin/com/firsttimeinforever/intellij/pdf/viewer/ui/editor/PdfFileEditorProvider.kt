package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.lang.PdfFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import generate.Utils.FILE_RESOURCES_PATH_WITH_PREFIX
import generate.Utils.getFileResourcesPathWithPrefix

class PdfFileEditorProvider : AsyncFileEditorProvider, DumbAware, Disposable {

  private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
  private var pdfFileEditor: PdfFileEditor? = null
  private var mustacheFileEditor: MustacheFileEditor? = null

  override fun getEditorTypeId() = "PDF"

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.fileType == PdfFileType || (mainProvider.accept(project, file) && file.extension == "mustache")
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createEditorAsync(project, file).build()
  }

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun createEditorAsync(project: Project, virtualFile: VirtualFile): AsyncFileEditorProvider.Builder {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        if (virtualFile.fileType == PdfFileType) {
          pdfFileEditor = PdfFileEditor(project, virtualFile)
          return pdfFileEditor as PdfFileEditor
        } else if (mainProvider.accept(project, virtualFile) && virtualFile.extension == "mustache") {
          // TODO make this prettier, do not instantiate here this static variable
          FILE_RESOURCES_PATH_WITH_PREFIX = getFileResourcesPathWithPrefix(project, virtualFile)
          mustacheFileEditor = MustacheFileEditor(project, virtualFile)
          return (mustacheFileEditor as MustacheFileEditor).getTextEditorWithPreview()
        }
        throw RuntimeException("Unsupported file type. It shouldn't have come to this anyway.")
      }
    }
  }

  override fun dispose() {
    if (pdfFileEditor != null) Disposer.dispose(pdfFileEditor as PdfFileEditor)
    if (mustacheFileEditor != null) Disposer.dispose(mustacheFileEditor as MustacheFileEditor)
  }
}
