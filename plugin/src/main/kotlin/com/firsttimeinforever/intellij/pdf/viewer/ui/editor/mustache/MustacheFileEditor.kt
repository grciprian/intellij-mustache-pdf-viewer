package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  val project: @NotNull Project, val virtualFile: @NotNull VirtualFile
) : Disposable {
  private val provider = TextEditorProvider.getInstance()
  private val messageBusConnection = project.messageBus.connect()
  private lateinit var editor: TextEditor
  private lateinit var preview: MustachePdfFileEditorWrapper
  private lateinit var _textEditorWithPreview: TextEditorWithPreview

  init {

  }

  private fun createEditorBuilder(): AsyncFileEditorProvider.Builder {
    if (provider is AsyncFileEditorProvider) {
      return runBlockingCancellable {
        provider.createEditorAsync(project, virtualFile)
      }
    }
    return object : AsyncFileEditorProvider.Builder() {
      override fun build(): FileEditor {
        return provider.createEditor(project, virtualFile)
      }
    }
  }

  val textEditorWithPreview: () -> AsyncFileEditorProvider.Builder
    get() = {
      object : AsyncFileEditorProvider.Builder() {
        override fun build(): FileEditor {
          editor = createEditorBuilder().build() as TextEditor
          preview = MustachePdfFileEditorWrapper(project, virtualFile)
          _textEditorWithPreview = TextEditorWithPreview(
            editor, preview, NAME, TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, !PdfViewerSettings.instance.isVerticalSplit
          )
          //    Disposer.register(this, textEditorWithPreview)
          Disposer.register(_textEditorWithPreview, messageBusConnection)
          Disposer.register(_textEditorWithPreview, editor)
          Disposer.register(_textEditorWithPreview, preview)
          messageBusConnection.subscribe(PdfViewerSettings.TOPIC_SETTINGS, PdfViewerSettingsListener {
            _textEditorWithPreview.isVerticalSplit = !it.isVerticalSplit
          })
          return _textEditorWithPreview
        }
      }
    }

  companion object {
    const val NAME = "Mustache Viewer File Editor With Preview"
    private val logger = logger<MustacheFileEditor>()
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    Disposer.dispose(messageBusConnection)
    Disposer.dispose(editor)
    Disposer.dispose(preview)
    Disposer.dispose(_textEditorWithPreview)
  }
}
