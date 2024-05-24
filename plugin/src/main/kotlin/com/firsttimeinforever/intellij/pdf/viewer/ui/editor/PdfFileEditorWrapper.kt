package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsFontsPathListener
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MustacheFileEditor.Companion.MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import javax.swing.JComponent

class PdfFileEditorWrapper(
  private val project: Project, private val mustacheFile: VirtualFile
) : FileEditorBase(), DumbAware {
  private val jbTabbedPane = JBTabbedPane()
  private val syncedTabbedEditors = mutableListOf<PdfFileEditor>()
  private val messageBusConnection = project.messageBus.connect()
  private val mustacheContextService = project.service<MustacheContextService>()
  private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()
  // orice fisier mustache deschis are asociat un PdfFileEditorWrapper cu un TabbedPane

  init {
    Disposer.register(this, messageBusConnection)
    mustacheIncludeProcessor.getRootsForMustacheFile(mustacheFile).forEach { addPdfFileEditorTab(it) }

    messageBusConnection.subscribe(
      MustacheFileEditor.MUSTACHE_FILE_LISTENER_FIRST_STEP_TOPIC,
      MustacheFileEditor.MustacheFileListenerFirstStep {
        tryUpdateTabbedPane()
      })
    messageBusConnection.subscribe(
      PdfViewerSettings.TOPIC_SETTINGS_FONTS_PATH,
      PdfViewerSettingsFontsPathListener {
        val syncedTabbedRootNames = syncedTabbedEditors.map { it.rootName }.toImmutableSet()
        mustacheIncludeProcessor.tryInvalidateRootPdfFilesForMustacheFileRoots(syncedTabbedRootNames)
        syncedTabbedRootNames.forEach { mustacheIncludeProcessor.processRootPdfFile(it) }
        ApplicationManager.getApplication().messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC)
          .mustacheFileContentChangedSecondStep(syncedTabbedRootNames)
      }
    )
  }

  private fun tryUpdateTabbedPane() {
    val updatedRoots = mustacheIncludeProcessor.getRootsForMustacheFile(mustacheFile)
    val syncedTabbedRootNames = syncedTabbedEditors.map { it.rootName }.toImmutableList()

    val livingRoots = syncedTabbedRootNames.intersect(updatedRoots)
    val expiredRoots = syncedTabbedRootNames.subtract(updatedRoots)
    val newRoots = updatedRoots.subtract(syncedTabbedRootNames.toImmutableSet())

    // if source fileRoots intersects this PdfFileEditorWrapper target fileRoots
    // then the mustache file that was modified impacted
    livingRoots.forEach { mustacheIncludeProcessor.processRootPdfFile(it) }
    ApplicationManager.getApplication().messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC)
      .mustacheFileContentChangedSecondStep(livingRoots)

    // remove roots not needed anymore
    var i = 0
    while (i < syncedTabbedEditors.size) {
      if (expiredRoots.contains(syncedTabbedEditors[i].rootName)) {
        jbTabbedPane.remove(i)
        Disposer.dispose(syncedTabbedEditors[i])
        syncedTabbedEditors.removeAt(i)
        i -= 1
      }
      ++i
    }

    // add new identified root files
    newRoots.forEach { rootName -> addPdfFileEditorTab(rootName) }
  }

  private fun addPdfFileEditorTab(rootName: String) {
    val processedPdfFile = mustacheIncludeProcessor.processRootPdfFile(rootName)
    val editor = PdfFileEditor(project, processedPdfFile, rootName)
    Disposer.register(this, editor)
    jbTabbedPane.insertTab(rootName, null, editor.component, null, ADD_INDEX_FOR_NEW_TAB)
    syncedTabbedEditors.add(ADD_INDEX_FOR_NEW_TAB, editor)
  }

  override fun getComponent(): JComponent = jbTabbedPane

  override fun getName(): String = NAME

  override fun getPreferredFocusedComponent(): JComponent = getActiveTab()

  private fun getActiveTab(): PdfEditorViewComponent {
    return jbTabbedPane.selectedComponent as PdfEditorViewComponent
  }

  companion object {
    private const val NAME = "Pdf Wrapper Viewer File Editor"
    private val logger = logger<PdfFileEditorWrapper>()
    private const val ADD_INDEX_FOR_NEW_TAB = 0
  }
}
