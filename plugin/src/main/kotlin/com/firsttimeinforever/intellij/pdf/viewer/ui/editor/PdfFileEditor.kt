package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.structureView.PdfStructureViewBuilder
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.structureView.StructureViewBuilder
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
class PdfFileEditor(project: Project, private var virtualFile: VirtualFile) : FileEditorBase(), DumbAware {
  var viewComponent = PdfEditorViewComponent(project, virtualFile)
  private var fileRoots: Set<String>? = null
  private val messageBusConnection = project.messageBus.connect()
  private val fileChangedListener = FileChangedListener(PdfViewerSettings.instance.enableDocumentAutoReload)

  constructor(
    project: Project, virtualFile: VirtualFile, fileRoots: Set<String>
  ) : this(project, virtualFile) {
    this.fileRoots = fileRoots
  }

  init {
    Disposer.register(this, viewComponent)
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC, PdfViewerSettingsListener {
      fileChangedListener.isEnabled = it.enableDocumentAutoReload
    })
    // subscribes to changes directly from a mustache file to reload all previews that depend on it
    messageBusConnection.subscribe(MustacheFileEditor.MUSTACHE_FILE_LISTENER_TOPIC, MustacheFileEditor.MustacheFileListener {
      // if source fileRoots intersects this PdfFileEditor target fileRoots
      // then the mustache file that was modified impacted the pdf and it needs to be reloaded
      if (fileRoots != null && it.intersect(fileRoots!!).isEmpty()) return@MustacheFileListener

      // update fileIncludePropsEntry with maybe modified ones
      fileRoots = it

      if (viewComponent.controller == null) {
        logger.warn("FileChangedListener was called for view with controller == null!")
      } else {
        logger.debug("Target file ${virtualFile.path} changed. Reloading current view.")
        viewComponent.controller?.reload(tryToPreserveState = true)
      }
    })
  }

  override fun getName(): String = NAME

  override fun getFile(): VirtualFile = virtualFile

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
      } else if (events.any { it.file == virtualFile }) {
        logger.debug("Target file ${virtualFile.path} changed. Reloading current view.")
        viewComponent.controller?.reload(tryToPreserveState = true)
      }
    }
  }

  override fun getStructureViewBuilder(): StructureViewBuilder {
    return PdfStructureViewBuilder(this)
  }

  companion object {
    private const val NAME = "Pdf Viewer File Editor"
    private val logger = logger<PdfFileEditor>()
  }
}
