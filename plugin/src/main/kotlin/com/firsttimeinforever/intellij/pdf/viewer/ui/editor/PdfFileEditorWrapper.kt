package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import kotlinx.collections.immutable.toImmutableList
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
        tryUpdateTabbedPane(it)
      })

    // reload on tab change? but now is implemented to load all even if not in focus
//    jbTabbedPane.addChangeListener {
//      val sourceTabbedPane = it.source as JBTabbedPane
//      val changedPdfEditorViewComponentTab =
//        (jbTabbedPane.getComponentAt(sourceTabbedPane.selectedIndex) as PdfEditorViewComponent).controller
//      if (changedPdfEditorViewComponentTab == null) {
//        logger.warn("FileChangedListener was called for view with controller == null!")
//      } else // a smart if here maybe if (events.any { it.file == editor.file }) {
//        logger.debug("Target sourceTabbedPane ${sourceTabbedPane.name} changed. Reloading current view.")
//      changedPdfEditorViewComponentTab?.reload(tryToPreserveState = true)
//    }
  }

  private fun tryUpdateTabbedPane(updatedMustacheFileRoots: Set<String>) {
    println("\n\nTRYUpdateTabbedPane FOR " + mustacheFile.name)
    println("updatedMustacheFileRoots for SOURCE")
    println(updatedMustacheFileRoots)
    println("tabRootAware")
    val updatedRoots = mustacheIncludeProcessor.getRootsForMustacheFile(mustacheFile)
    println("updatedRoots")
    println(updatedRoots)
    val syncedTabbedRootNames = syncedTabbedEditors.map { it.rootName }.toImmutableList()
    println("syncedTabbedRootNames")
    println(syncedTabbedRootNames)
    // update fileRoots with maybe modified ones before adding the pdf
//    fileRoots = mustacheIncludeProcessor.getRootsForMustacheFile(mustacheFile)
//    println("fileRoots")
//    println(fileRoots)


    val livingRoots = syncedTabbedRootNames.intersect(updatedRoots)
    val expiredRoots = syncedTabbedRootNames.subtract(updatedRoots)
    val newRoots = updatedRoots.subtract(syncedTabbedRootNames)

//    if (livingRoots.isEmpty()) return
    println("livingRoots")
    println(livingRoots)
    println("expiredRoots")
    println(expiredRoots)
    println("newRoots")
    println(newRoots)

    // if source fileRoots intersects this PdfFileEditorWrapper target fileRoots
    // then the mustache file that was modified impacted
    livingRoots.forEach { mustacheIncludeProcessor.processRootPdfFile(it) }

    // remove roots not needed anymore
    var i = 0
    while (i < syncedTabbedRootNames.size) {
      if (syncedTabbedEditors.size > 0 && expiredRoots.contains(syncedTabbedRootNames.elementAt(i))) {
        println("removing " + syncedTabbedRootNames.elementAt(i))
        jbTabbedPane.remove(i)
        syncedTabbedEditors[i].dispose()
        syncedTabbedEditors.removeAt(i)
        i -= 1
      }
      ++i
    }

    // add new identified root files
    newRoots.forEach { rootName -> addPdfFileEditorTab(rootName) }
//    fileRoots = updatedMustacheFileRoots
  }

  private fun addPdfFileEditorTab(rootName: String) {
    val processedPdfFile = mustacheIncludeProcessor.processRootPdfFile(rootName)
    val editor = PdfFileEditor(project, processedPdfFile, rootName)
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
