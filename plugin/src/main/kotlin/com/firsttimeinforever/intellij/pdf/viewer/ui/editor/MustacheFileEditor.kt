package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.util.messages.Topic
import generate.Utils.FILE_RESOURCES_PATH_WITH_PREFIX
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  val project: @NotNull Project, val virtualFile: @NotNull VirtualFile
) : Disposable {
  private val mustacheContextService = project.service<MustacheContextService>()
  private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()
  private val provider = TextEditorProvider.getInstance()
  private val messageBusConnection = project.messageBus.connect()
  private val fileEditorChangedListener = FileEditorChangedListener()
  private val editor = createEditorBuilder().build() as TextEditor
  private val preview = PdfFileEditorWrapper(project, virtualFile)
  private val textEditorWithPreview = TextEditorWithPreview(
    editor, preview, NAME, TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, !PdfViewerSettings.instance.isVerticalSplit
  )

  init {
//    Disposer.register(this, textEditorWithPreview)
    Disposer.register(textEditorWithPreview, messageBusConnection)
    Disposer.register(textEditorWithPreview, editor)
    Disposer.register(textEditorWithPreview, preview)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileEditorChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_SETTINGS, PdfViewerSettingsListener {
      textEditorWithPreview.isVerticalSplit = !it.isVerticalSplit
    })
  }

  private inner class FileEditorChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      // if the file modified is the current opened editor file then reprocess the pdf file
      // and announce the correspondent PdfFileEditor to reload
      if (events.any {
          it.file == editor.file && it.file?.canonicalPath?.indexOf(FILE_RESOURCES_PATH_WITH_PREFIX, 0, false) == 0
        }) {
        println("processFileIncludePropsMap after any files modification under resources folder")
        mustacheIncludeProcessor.processFileIncludePropsMap()
        println("Target file ${editor.file.canonicalPath} changed. Reloading current view.")
        val updatedMustacheFileRoots = mustacheIncludeProcessor.getRootsForMustacheFile(editor.file)
        println("updatedMustacheFileRoots")
        println(updatedMustacheFileRoots)
        mustacheIncludeProcessor.tryInvalidateRootPdfFilesForMustacheFileRoots(updatedMustacheFileRoots)
        ApplicationManager.getApplication().messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_FIRST_STEP_TOPIC)
          .mustacheFileContentChangedFirstStep()
        ApplicationManager.getApplication().messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC)
          .mustacheFileContentChangedSecondStep(updatedMustacheFileRoots)
      }
    }
  }

  fun getTextEditorWithPreview(): TextEditorWithPreview {
    return textEditorWithPreview
  }

  fun interface MustacheFileListenerFirstStep {
    fun mustacheFileContentChangedFirstStep()
  }

  fun interface MustacheFileListenerSecondStep {
    fun mustacheFileContentChangedSecondStep(updatedMustacheFileRoots: Set<String>)
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

  companion object {
    val MUSTACHE_FILE_LISTENER_FIRST_STEP_TOPIC = Topic(MustacheFileListenerFirstStep::class.java)
    val MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC = Topic(MustacheFileListenerSecondStep::class.java)
    private const val NAME = "Mustache Viewer File Editor With Preview"
    private val logger = logger<MustacheFileEditor>()
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    Disposer.dispose(messageBusConnection)
    Disposer.dispose(editor)
    Disposer.dispose(preview)
    Disposer.dispose(textEditorWithPreview)
  }
}