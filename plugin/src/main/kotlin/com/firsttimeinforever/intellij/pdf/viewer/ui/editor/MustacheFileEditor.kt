package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  project: @NotNull Project,
  editor: @NotNull TextEditor,
  preview: @NotNull PdfFileEditor
) : TextEditorWithPreview(
  editor,
  preview,
  NAME,
  Layout.SHOW_EDITOR_AND_PREVIEW,
  !PdfViewerSettings.instance.isVerticalSplit
) {
  private val messageBusConnection = project.messageBus.connect()

  init {
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC, PdfViewerSettingsListener {
      handleLayoutChange(!it.isVerticalSplit)
    })
  }

  companion object {
    private const val NAME = "Mustache Viewer File Editor With Preview"
    private val logger = logger<MustacheFileEditor>()
  }
}
