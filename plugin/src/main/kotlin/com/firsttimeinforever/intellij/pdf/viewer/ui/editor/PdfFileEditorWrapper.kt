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
import generate.Utils.getProcessedPdfFile
import java.util.*
import javax.swing.JComponent

class PdfFileEditorWrapper(
  val project: Project, virtualFile: VirtualFile
) : FileEditorBase(), DumbAware {
  private val messageBusConnection = project.messageBus.connect()
  private val jbTabbedPane = JBTabbedPane()
  private val mustacheContextService = project.service<MustacheContextService>()
  private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()
  private var fileRoots: Set<String> = mustacheIncludeProcessor.getRootsForFile(virtualFile)
    .orElseThrow { RuntimeException("Include map corrupted for " + virtualFile.canonicalPath) }
  private val tabRootAware = mutableListOf<String>()
  private val ADD_INDEX_FOR_NEW_TAB = 0

  init {
    Disposer.register(this, messageBusConnection)
    fileRoots.forEach { addPdfFileEditorTab(it) }

    messageBusConnection.subscribe(MustacheFileEditor.MUSTACHE_FILE_LISTENER_TOPIC, MustacheFileEditor.MustacheFileListener {
      // if source fileRoots intersects this PdfFileEditor target fileRoots
      // then the mustache file that was modified impacted
      val rootsIntersection = it.intersect(fileRoots)
      if (rootsIntersection.isEmpty()) return@MustacheFileListener

      // remove roots not needed anymore
      // fml
      var i = 0
      while (i < tabRootAware.size) {
        if (!it.contains(tabRootAware.elementAt(i))) {
          println("removing " + tabRootAware.elementAt(i))
          jbTabbedPane.remove(i)
          tabRootAware.removeAt(i)
          if (i - 1 >= 0) {
            i -= 1
          }
        }
        ++i
      }

      // update fileRoots with maybe modified ones
      fileRoots = it

      // add new identified root files
      it.subtract(tabRootAware.toSet())
        .forEach { rootName -> addPdfFileEditorTab(rootName) }
    })

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

  private fun addPdfFileEditorTab(rootName: String) {
    var processedPdfFile = mustacheIncludeProcessor.rootVirtualFileMap[rootName]
    if (processedPdfFile == null) {
      processedPdfFile = getProcessedPdfFile(rootName)
      mustacheIncludeProcessor.rootVirtualFileMap[rootName] = processedPdfFile
    }
    Objects.requireNonNull(processedPdfFile, "Could not get processedPdfFile!")
    val editor = PdfFileEditor(project, processedPdfFile!!, fileRoots)
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
  }
}
