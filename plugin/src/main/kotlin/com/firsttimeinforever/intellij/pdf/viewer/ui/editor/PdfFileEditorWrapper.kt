package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerMustacheFontsPathSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MustacheFileEditor.Companion.MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.messages.Topic
import generate.Utils.getRelativePathFromResourcePathWithMustachePrefixPath
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import javax.swing.DefaultSingleSelectionModel
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
    mustacheIncludeProcessor.getRootsForMustache(mustacheFile).forEach { addPdfFileEditorTab(it) }

    messageBusConnection.subscribe(
      MustacheFileEditor.MUSTACHE_FILE_LISTENER_FIRST_STEP_TOPIC,
      MustacheFileEditor.MustacheFileListenerFirstStep {
        updateTabbedPane()
      })
    messageBusConnection.subscribe(
      PdfViewerSettings.TOPIC_MUSTACHE,
      PdfViewerMustacheFontsPathSettingsListener {
        val syncedTabbedRootNames = syncedTabbedEditors.map { it.rootName }.toImmutableSet()
        mustacheIncludeProcessor.tryInvalidateRootPdfsForMustacheRoots(syncedTabbedRootNames)
        syncedTabbedRootNames.forEach { mustacheIncludeProcessor.processRootPdfFile(it) }
        project.messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC)
          .mustacheFileContentChangedSecondStep(syncedTabbedRootNames)
      }
    )
    jbTabbedPane.model.addChangeListener {
      val source = it.source
      if (source !is DefaultSingleSelectionModel) return@addChangeListener
      project.messageBus.syncPublisher(MUSTACHE_TOOL_WINDOW_LISTENER_TOPIC)
        .rootChanged(
          syncedTabbedEditors[source.selectedIndex].rootName,
          getRelativePathFromResourcePathWithMustachePrefixPath(mustacheFile)
        )
    }
  }

  private fun updateTabbedPane() {
    val updatedRoots = mustacheIncludeProcessor.getRootsForMustache(mustacheFile)
    val syncedTabbedRootNames = syncedTabbedEditors.map { it.rootName }.toImmutableList()

    val livingRoots = syncedTabbedRootNames.intersect(updatedRoots)
    val expiredRoots = syncedTabbedRootNames.subtract(updatedRoots)
    val newRoots = updatedRoots.subtract(syncedTabbedRootNames.toImmutableSet())

    // if source fileRoots intersects this PdfFileEditorWrapper target fileRoots
    // then the mustache file that was modified impacted
    livingRoots.forEach { mustacheIncludeProcessor.processRootPdfFile(it) }

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

    // refresh living roots tabs
    project.messageBus.syncPublisher(MUSTACHE_FILE_LISTENER_SECOND_STEP_TOPIC)
      .mustacheFileContentChangedSecondStep(livingRoots)
    jbTabbedPane.selectedIndex = 0
  }

  private fun addPdfFileEditorTab(rootName: String) {
    val pdfFile = mustacheIncludeProcessor.processRootPdfFile(rootName)
    val editor = PdfFileEditor(project, pdfFile, rootName)
    Disposer.register(this, editor)
    jbTabbedPane.insertTab(rootName, null, editor.component, null, ADD_INDEX_FOR_NEW_TAB)
    syncedTabbedEditors.add(ADD_INDEX_FOR_NEW_TAB, editor)
  }

  override fun getComponent(): JComponent = jbTabbedPane

  override fun getName(): String = NAME

  override fun getPreferredFocusedComponent(): JComponent = jbTabbedPane.selectedComponent as PdfEditorViewComponent

  fun interface MustacheToolWindowListener {
    fun rootChanged(root: String?, selectedNodeName: String?)
  }

  val activeTabRoot: String
    get() = syncedTabbedEditors[jbTabbedPane.selectedIndex].rootName

  companion object {
    val MUSTACHE_TOOL_WINDOW_LISTENER_TOPIC = Topic(MustacheToolWindowListener::class.java)
    private const val NAME = "Pdf Wrapper Viewer File Editor"
    private val logger = logger<PdfFileEditorWrapper>()
    private const val ADD_INDEX_FOR_NEW_TAB = 0
  }
}
