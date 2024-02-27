package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  editor: @NotNull TextEditor,
  preview: @NotNull PdfFileEditor
) : TextEditorWithPreview(
  editor,
  preview,
  NAME,
  Layout.SHOW_EDITOR_AND_PREVIEW,
  true //!MarkdownSettings.getInstance(ProjectUtil.currentOrDefaultProject(editor.editor.project)).isVerticalSplit
) {

  companion object {
    private const val NAME = "Mustache Viewer File Editor With Preview"
    private val logger = logger<MustacheFileEditor>()
  }
}
