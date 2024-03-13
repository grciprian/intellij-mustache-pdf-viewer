package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.lang.PdfFileType
import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextServiceImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import generate.MustacheIncludeProcessor
import generate.Utils.FILE_RESOURCES_PATH_WITH_PREFIX
import generate.Utils.getFileResourcesPathWithPrefix

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

  override fun createEditorAsync(project: Project, virtualFile: VirtualFile): AsyncFileEditorProvider.Builder {
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        if (virtualFile.fileType == PdfFileType) {
          return PdfFileEditor(project, virtualFile)
        } else if (mainProvider.accept(project, virtualFile) && virtualFile.extension == "mustache") {
          FILE_RESOURCES_PATH_WITH_PREFIX = getFileResourcesPathWithPrefix(project, virtualFile)
          return MustacheFileEditor(project, virtualFile).getEditor()
        }
        throw RuntimeException("Unsupported file type. It shouldn't have come to this anyway.")
      }
    }
  }
}
