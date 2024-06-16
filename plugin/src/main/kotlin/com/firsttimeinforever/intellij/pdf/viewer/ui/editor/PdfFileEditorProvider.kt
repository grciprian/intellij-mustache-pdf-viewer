package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.lang.PdfFileType
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings.Companion.instance
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
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
    return try {
      MUSTAHCE_PREFIX = instance.customMustachePrefix
      MUSTACHE_SUFFIX = instance.customMustacheSuffix
      TEMPLATES_PATH = getTemplatesPath(project, file.canonicalPath)
      mainProvider.accept(project, file) && file.extension == MUSTACHE_SUFFIX
    } catch (e: Exception) {
      // log maybe? or not
      false
    }
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
        } else if (mainProvider.accept(project, file) && file.extension == MUSTACHE_SUFFIX) {
          mustacheFileEditor = MustacheFileEditor(project, file)
          return (mustacheFileEditor as MustacheFileEditor).textEditorWithPreview
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

lateinit var MUSTAHCE_PREFIX: String
lateinit var MUSTACHE_SUFFIX: String
lateinit var TEMPLATES_PATH: String
