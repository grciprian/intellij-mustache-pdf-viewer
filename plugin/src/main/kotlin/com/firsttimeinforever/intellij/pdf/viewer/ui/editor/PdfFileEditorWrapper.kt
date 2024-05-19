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
import javax.swing.JComponent

class PdfFileEditorWrapper(
  private val project: Project, private val mustacheFile: VirtualFile
) : FileEditorBase(), DumbAware {
  private val jbTabbedPane = JBTabbedPane()
  private val tabRootAware = mutableListOf<String>()
  private val messageBusConnection = project.messageBus.connect()
  private val mustacheContextService = project.service<MustacheContextService>()
  private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()
//  private var fileRoots: Set<String> = mustacheIncludeProcessor.getRootsForMustacheFile(mustacheFile)

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
    println("updatedMustacheFileRoots")
    println(updatedMustacheFileRoots)
    println("tabRootAware")
    println(tabRootAware)
    // update fileRoots with maybe modified ones before adding the pdf
//    fileRoots = mustacheIncludeProcessor.getRootsForMustacheFile(mustacheFile)
//    println("fileRoots")
//    println(fileRoots)


    val livingRoots = tabRootAware.intersect(updatedMustacheFileRoots)
    val expiredRoots = tabRootAware.subtract(updatedMustacheFileRoots)
    val newRoots = updatedMustacheFileRoots.subtract(tabRootAware.toSet())

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
    while (i < tabRootAware.size) {
      if (expiredRoots.contains(tabRootAware.elementAt(i))) {
        println("removing " + tabRootAware.elementAt(i))
        jbTabbedPane.remove(i)
        tabRootAware.removeAt(i)
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
    val editor = PdfFileEditor(project, processedPdfFile)
    jbTabbedPane.insertTab(rootName, null, editor.component, null, ADD_INDEX_FOR_NEW_TAB)
    tabRootAware.add(ADD_INDEX_FOR_NEW_TAB, rootName)
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
