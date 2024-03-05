package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  project: @NotNull Project,
  val editor: @NotNull TextEditor,
  val preview: @NotNull PdfFileEditor
) : TextEditorWithPreview(
  editor,
  preview,
  NAME,
  Layout.SHOW_EDITOR_AND_PREVIEW,
  !PdfViewerSettings.instance.isVerticalSplit
) {
  private val messageBusConnection = project.messageBus.connect()
  private val fileEditorChangedListener = FileEditorChangedListener()

  init {
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileEditorChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC, PdfViewerSettingsListener {
      handleLayoutChange(!it.isVerticalSplit)
    })
  }

  private inner class FileEditorChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      if (events.any { it.file == editor.file }) {
        logger.debug("Target file ${editor.file} changed. Reloading preview.")
        PdfFileEditorProvider.getProcessedPdfFile(editor.file)
        if (preview.viewComponent.controller == null) {
          logger.warn("FileChangedListener was called for view with controller == null!")
        } else if (events.any { it.file == editor.file }) {
          logger.debug("Target file ${editor.file.path} changed. Reloading current view.")
          preview.viewComponent.controller?.reload(tryToPreserveState = true)
        }
      }
    }
  }

  companion object {
    private const val NAME = "Mustache Viewer File Editor With Preview"
    private val logger = logger<MustacheFileEditor>()
  }
}
