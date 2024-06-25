package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.lang.PdfFileType
import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextServiceImpl
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings.Companion.instance
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import generate.Utils.getTemplatesPath


class PdfFileEditorProvider : AsyncFileEditorProvider, DumbAware, Disposable {

  private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
  private var pdfFileEditor: PdfFileEditor? = null
  private var mustacheFileEditor: MustacheFileEditor? = null

  override fun getEditorTypeId() = PDF

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (file.fileType == PdfFileType) return true
    val mustacheContextService = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file)
      ?.service<MustacheContextService>() as MustacheContextServiceImpl
    return mainProvider.accept(project, file) && file.extension == mustacheContextService.mustacheSuffix
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createEditorAsync(project, file).build()
  }

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        if (file.fileType == PdfFileType) {
          pdfFileEditor = PdfFileEditor(project, file)
          return pdfFileEditor as PdfFileEditor
        }
        val mustacheContextService = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file)
          ?.service<MustacheContextService>() as MustacheContextServiceImpl
        if (mainProvider.accept(project, file) && file.extension == mustacheContextService.mustacheSuffix) {
          mustacheFileEditor = MustacheFileEditor(project, file)
          return (mustacheFileEditor as MustacheFileEditor).textEditorWithPreview().build()
        }
        throw RuntimeException("Unsupported file type. It shouldn't have come to this anyway.")
      }
    }
  }

  override fun dispose() {
    if (pdfFileEditor != null) Disposer.dispose(pdfFileEditor as PdfFileEditor)
    if (mustacheFileEditor != null) Disposer.dispose(mustacheFileEditor as MustacheFileEditor)
  }

  companion object {
    private const val PDF = "PDF"
  }
}
