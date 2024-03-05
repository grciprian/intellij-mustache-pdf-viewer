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
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JComponent

// TODO: Implement state persistence
class PdfFileEditor(project: Project, private val virtualFile: VirtualFile) : FileEditorBase(), DumbAware {
  //  val viewComponent = PdfEditorViewComponent(project, virtualFile)
  val jbTabbedPane = JBTabbedPane()

  init {
    jbTabbedPane.insertTab(virtualFile.canonicalPath, null, PdfEditorViewComponent(project, virtualFile), "TIP", 0)
  }

  val viewComponent: PdfEditorViewComponent
    get() {
      var selectedTab = jbTabbedPane.selectedIndex
      if (jbTabbedPane.selectedIndex != -1) selectedTab = 0
      return jbTabbedPane.getTabComponentAt(selectedTab) as PdfEditorViewComponent
    }

  private val messageBusConnection = project.messageBus.connect()
  private val fileChangedListener = FileChangedListener(PdfViewerSettings.instance.enableDocumentAutoReload)

  init {
    Disposer.register(this, getActiveTab())
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC, PdfViewerSettingsListener {
      fileChangedListener.isEnabled = it.enableDocumentAutoReload
    })
  }

  override fun getName(): String = NAME

  override fun getFile(): VirtualFile = virtualFile

  override fun getComponent(): JComponent = jbTabbedPane

  override fun getPreferredFocusedComponent(): JComponent = getActiveTab()

  private inner class FileChangedListener(var isEnabled: Boolean = true) : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      if (!isEnabled) {
        return
      }
      if (getActiveTab().controller == null) {
        logger.warn("FileChangedListener was called for view with controller == null!")
      } else if (events.any { it.file == virtualFile }) {
        logger.debug("Target file ${virtualFile.path} changed. Reloading current view.")
        getActiveTab().controller?.reload(tryToPreserveState = true)
      }
    }
  }

  override fun getStructureViewBuilder(): StructureViewBuilder {
    return PdfStructureViewBuilder(this)
  }

  private fun getActiveTab(): PdfEditorViewComponent {
    return jbTabbedPane.selectedComponent as PdfEditorViewComponent
  }

  companion object {
    private const val NAME = "Pdf Viewer File Editor"
    private val logger = logger<PdfFileEditor>()
  }
}
