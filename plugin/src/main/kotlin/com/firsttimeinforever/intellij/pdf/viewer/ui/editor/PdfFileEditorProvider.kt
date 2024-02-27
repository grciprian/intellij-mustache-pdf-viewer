package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.lang.PdfFileType
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class PdfFileEditorProvider : AsyncFileEditorProvider, DumbAware {

    private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
  override fun getEditorTypeId() = "PDF"

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file.fileType == PdfFileType || mainProvider.accept(project, file)
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createEditorAsync(project, file).build()
  }

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    return object: AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        if(file.fileType == PdfFileType) {
          return PdfFileEditor(project, file)
        } else if(mainProvider.accept(project, file) && file.extension == "mustache") {
          val firstBuilder = createEditorBuilder(provider = mainProvider, project = project, file = file)
          return MustacheFileEditor(firstBuilder as TextEditor, PdfFileEditor(project, file))
        }
        throw RuntimeException("Unsupported file type.");
      }
    }
  }

  private fun createEditorBuilder(
    provider: FileEditorProvider,
    project: Project,
    file: VirtualFile
  ): Any {
    if (provider is AsyncFileEditorProvider) {
      return runBlockingCancellable {
        provider.createEditorAsync(project, file)
      }
    }
    return object: AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return provider.createEditor(project, file)
      }
    }
  }
}
