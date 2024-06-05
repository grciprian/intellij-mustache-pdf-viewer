package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.structureView.PdfStructureViewBuilder
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import javax.swing.JComponent

// TODO: Implement state persistence
class PdfFileEditor(project: Project, private val pdfFile: VirtualFile) : FileEditorBase(), DumbAware {
  val viewComponent = PdfEditorViewComponent(project, pdfFile)
  private val messageBusConnection = project.messageBus.connect()
  private val fileChangedListener = FileChangedListener(PdfViewerSettings.instance.enableDocumentAutoReload)
  private val mustacheContextService = project.service<MustacheContextService>()
  private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()
  var rootName: String = null.toString()

  constructor(project: Project, pdfFile: VirtualFile, rootName: String) : this(project, pdfFile) {
    this.rootName = rootName
  }

  init {
    Disposer.register(this, viewComponent)
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_SETTINGS, PdfViewerSettingsListener {
      fileChangedListener.isEnabled = it.enableDocumentAutoReload
    })
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    // subscribes to changes directly from a mustache file to reload all previews that depend on it
//    messageBusConnection.subscribe(
//      MustacheFileEditor.MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC,
//      MustacheFileEditor.MustacheFileListenerSecondStep {
//        // if source fileRoots intersects this PdfFileEditor target fileRoots
//        // then the mustache file that was modified impacted the pdf and it needs to be reloaded
//        val pdfFileRoot = mustacheIncludeProcessor.getRootForPdfFile(pdfFile)
//        if (!it.contains(pdfFileRoot)) return@MustacheFileListenerSecondStep
//
//        if (viewComponent.controller == null) {
//          logger.warn("FileChangedListener was called for view with controller == null!")
//        } else {
//          logger.debug("Target file ${pdfFile.path} changed. Reloading current view.")
//          viewComponent.controller.reload(tryToPreserveState = true)
//        }
//      })
  }

  override fun getName(): String = NAME

  override fun getFile(): VirtualFile = pdfFile

  override fun getComponent(): JComponent = viewComponent

  override fun getPreferredFocusedComponent(): JComponent = viewComponent.controlPanel

  // only works for PDF files opened directly from the project
  private inner class FileChangedListener(var isEnabled: Boolean = true) : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      if (!isEnabled) {
        return
      }
      if (viewComponent.controller == null) {
        logger.warn("FileChangedListener was called for view with controller == null!")
      } else if (events.any { it.file == pdfFile }) {
        logger.debug("Target file ${pdfFile.path} changed. Reloading current view.")
        viewComponent.controller.reload(tryToPreserveState = true)
      }
    }
  }

  override fun getStructureViewBuilder(): StructureViewBuilder {
    return PdfStructureViewBuilder(this)
  }

  override fun dispose() {
    super.dispose()
    messageBusConnection.disconnect()
    Disposer.dispose(messageBusConnection)
    Disposer.dispose(viewComponent)
  }

  companion object {
    private const val NAME = "Pdf Viewer File Editor"
    private val logger = logger<PdfFileEditor>()
  }
}
