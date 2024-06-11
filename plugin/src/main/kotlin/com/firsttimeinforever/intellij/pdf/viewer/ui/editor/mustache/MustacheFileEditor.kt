package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import generate.Utils.isFileUnderResourcesPathWithPrefix
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  val project: @NotNull Project, val virtualFile: @NotNull VirtualFile
) : Disposable {
  private val mustacheContextService = project.service<MustacheContextService>()
  private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()
  private val provider = TextEditorProvider.getInstance()
  private val messageBusConnection = project.messageBus.connect()
  private val fileChangedListener = FileChangedListener()
  private val editor = createEditorBuilder().build() as TextEditor
  private val preview = MustachePdfFileEditorWrapper(project, virtualFile)
  private val _textEditorWithPreview = TextEditorWithPreview(
    editor, preview, NAME, TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, !PdfViewerSettings.instance.isVerticalSplit
  )

  init {
//    Disposer.register(this, textEditorWithPreview)
    Disposer.register(_textEditorWithPreview, messageBusConnection)
    Disposer.register(_textEditorWithPreview, editor)
    Disposer.register(_textEditorWithPreview, preview)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_SETTINGS, PdfViewerSettingsListener {
      _textEditorWithPreview.isVerticalSplit = !it.isVerticalSplit
    })
  }

  private inner class FileChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      // if the file modified is the current opened editor file then reprocess the pdf file
      // and announce the correspondent PdfFileEditor to reload
      val editorFile = editor.file
      if (events.any {
          it.file == editorFile && isFileUnderResourcesPathWithPrefix(project, it.file)
        }) {
        println("processFileIncludePropsMap after any files modification under resources folder")
        mustacheIncludeProcessor.processFileIncludePropsMap()
        println("Target file ${editorFile.canonicalPath} changed. Reloading current view.")
        val mustacheFileRoots = mustacheIncludeProcessor.getRootsForMustache(editorFile)
        mustacheIncludeProcessor.tryInvalidateRootPdfsForMustacheRoots(mustacheFileRoots)
        project.messageBus.syncPublisher(MustacheUpdatePdfFileEditorTabs.TOPIC).tabsUpdated()
        project.messageBus.syncPublisher(MustacheRefreshPdfFileEditorTabs.TOPIC).refreshTabs(mustacheFileRoots)
        project.messageBus.syncPublisher(MustacheToolWindowListener.TOPIC).refresh()
      }
    }
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

  val textEditorWithPreview: TextEditorWithPreview
    get() = _textEditorWithPreview

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
