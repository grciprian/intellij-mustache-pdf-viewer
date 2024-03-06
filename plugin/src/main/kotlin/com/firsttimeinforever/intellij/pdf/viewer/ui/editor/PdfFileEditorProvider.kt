package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.lang.PdfFileType
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import generate.PdfGenerationService
import java.nio.file.Files
import java.nio.file.Path

class PdfFileEditorProvider : AsyncFileEditorProvider, DumbAware {

  private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
  override fun getEditorTypeId() = "PDF"

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.fileType == PdfFileType || (mainProvider.accept(project, file) && file.extension == "mustache")
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createEditorAsync(project, file).build()
  }

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        if (file.fileType == PdfFileType) {
          return PdfFileEditor(project, file)
        } else if (mainProvider.accept(project, file) && file.extension == "mustache") {
          return MustacheFileEditor(project, createEditorBuilder(mainProvider, project, file).build() as TextEditor, PdfFileEditorWrapper(project, file));
        }
        throw RuntimeException("Unsupported file type. It shouldn't have come to this anyway.")
      }
    }
  }

  private fun createEditorBuilder(
    provider: FileEditorProvider,
    project: Project,
    file: VirtualFile
  ): AsyncFileEditorProvider.Builder {
    if (provider is AsyncFileEditorProvider) {
      return runBlockingCancellable {
        provider.createEditorAsync(project, file)
      }
    }
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return provider.createEditor(project, file)
      }
    }
  }
}
