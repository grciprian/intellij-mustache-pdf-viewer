package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import generate.MustacheIncludeProcessor
import generate.Utils.FILE_RESOURCES_PATH_WITH_PREFIX
import generate.Utils.getProcessedPdfFile
import org.jetbrains.annotations.NotNull

class MustacheFileEditor(
  project: @NotNull Project,
  val editor: @NotNull TextEditor,
  preview: @NotNull PdfFileEditorWrapper
) : TextEditorWithPreview(
  editor, preview, NAME, Layout.SHOW_EDITOR_AND_PREVIEW, !PdfViewerSettings.instance.isVerticalSplit
) {
  private val messageBusConnection = project.messageBus.connect()
  private val fileEditorChangedListener = FileEditorChangedListener()
  private val mustacheContextService = project.service<MustacheContextService>()
  val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()

  init {
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileEditorChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC, PdfViewerSettingsListener {
      handleLayoutChange(!it.isVerticalSplit)
    })
  }

  private inner class FileEditorChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
//      val initialRootParents = mustacheIncludeProcessor.roots.toSet()
      // processFileIncludePropsMap after any files modification under resources folder
      if (events.any { it.file?.canonicalPath?.indexOf(FILE_RESOURCES_PATH_WITH_PREFIX, 0, false) == 0 }) {
        mustacheIncludeProcessor.processFileIncludePropsMap()
      }
      // if the file modified is the current opened editor file than reprocess the pdf file
      // and announce the correspondent PdfFileEditor to reload
      if (events.any { it.file == editor.file }) {
        logger.debug("Target file ${editor.file.canonicalPath} changed. Reloading current view.")
        val keyProcessedPdfFileMap = HashMap<String, VirtualFile>()
        val fileIncludePropsEntry = mustacheIncludeProcessor.getFileIncludePropsForFile(editor.file)
          .orElseThrow { RuntimeException("Include map corrupted for " + editor.file.canonicalPath) }

        fileIncludePropsEntry.value.rootParents.forEach {
          val processedPdfFile = getProcessedPdfFile(it.simpleFilename)
          keyProcessedPdfFileMap[it.simpleFilename] = processedPdfFile
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_TOPIC)
          .mustacheFileContentChanged(fileIncludePropsEntry)
        ApplicationManager.getApplication().messageBus.syncPublisher(MUSTACHE_FILES_STRUCTURE_TOPIC)
          .mustacheFilesStructureChanged(keyProcessedPdfFileMap)
      }
    }
  }

  fun interface MustacheFileListener {
    fun mustacheFileContentChanged(fileIncludePropsEntry: Map.Entry<String, MustacheIncludeProcessor.IncludeProps>)
  }

  fun interface MustacheFilesStructureListener {
    fun mustacheFilesStructureChanged(keyProcessedPdfFileMap: Map<String, VirtualFile>)
  }

  companion object {
    val MUSTACHE_FILE_LISTENER_TOPIC = Topic(MustacheFileListener::class.java)
    val MUSTACHE_FILES_STRUCTURE_TOPIC = Topic(MustacheFilesStructureListener::class.java)
    private const val NAME = "Mustache Viewer File Editor With Preview"
    private val logger = logger<MustacheFileEditor>()
  }
}
