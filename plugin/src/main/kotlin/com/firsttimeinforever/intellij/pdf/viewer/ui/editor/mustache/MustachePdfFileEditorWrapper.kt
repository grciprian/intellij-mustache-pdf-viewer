package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import generate.MustacheIncludeProcessor
import kotlinx.collections.immutable.toImmutableSet
import javax.swing.DefaultSingleSelectionModel
import javax.swing.JComponent

class MustachePdfFileEditorWrapper(
  private val mainEditor: MustacheFileEditor, private val project: Project, private val mustacheFile: VirtualFile
) : FileEditorBase(), DumbAware {
  private val _jbTabbedPane = JBTabbedPane()
  private var _focusedSyncedTabEditor: PdfFileEditor? = null
  private val _syncedTabbedEditors = mutableListOf<PdfFileEditor>()
  private var _aliveRootNamesThatNeedUpdate = mutableSetOf<String>()
  val getAliveRootNamesThatNeedUpdateAndRelease
    get() = run {
      val copy = _aliveRootNamesThatNeedUpdate.toSet()
      _aliveRootNamesThatNeedUpdate.clear()
      copy
    }
  private val messageBusConnection = project.messageBus.connect()

  // orice fisier mustache deschis are asociat un PdfFileEditorWrapper cu un TabbedPane
  init {
    Disposer.register(this, messageBusConnection)
    val mustacheContext = project.getService(MustacheContextService::class.java).getContext(mustacheFile)
    val mustacheIncludeProcessor = mustacheContext.mustacheIncludeProcessor
    mustacheIncludeProcessor.getRootsForMustache(mustacheFile.path).forEach { addPdfFileEditorTab(it, mustacheIncludeProcessor) }
    changeSelectionAndFocus(FocusedTabSelectionType.FIRST)
    messageBusConnection.subscribe(
      MustacheUpdatePdfFileEditorTabs.TOPIC,
      MustacheUpdatePdfFileEditorTabs { updatePdfFileEditorTabs(it) }
    )
    _jbTabbedPane.model.addChangeListener {
      val source = it.source
      if (source !is DefaultSingleSelectionModel) return@addChangeListener
      if (_jbTabbedPane.selectedIndex < 0 || _jbTabbedPane.selectedIndex >= _syncedTabbedEditors.size) {
        _focusedSyncedTabEditor = null
        return@addChangeListener
      }
      val pdfFileEditor = _syncedTabbedEditors[source.selectedIndex]
      pdfFileEditor.isFocused = mainEditor.textEditorWithPreview.component.isShowing
      pdfFileEditor.tryReload()
      if (_focusedSyncedTabEditor != null) _focusedSyncedTabEditor!!.isFocused = false
      _focusedSyncedTabEditor = pdfFileEditor
      project.messageBus.syncPublisher(MustacheToolWindowListener.TOPIC)
        .rootChanged(pdfFileEditor.rootName, mustacheContext)
    }
  }

  private fun updatePdfFileEditorTabs(originFile: VirtualFile) {
    val mustacheIncludeProcessor = project.getService(MustacheContextService::class.java).getContext(mustacheFile).mustacheIncludeProcessor
    val thisUpdatedRoots = mustacheIncludeProcessor.getRootsForMustache(mustacheFile.path)
    val originUpdatedRoots = mustacheIncludeProcessor.getRootsForMustache(originFile.path)
    val syncedTabbedRootNames = _syncedTabbedEditors.map { it.rootName }.toImmutableSet()

    // if source fileRoots intersects this PdfFileEditorWrapper target fileRoots
    // then the mustache file that was modified impacted
    val commonWithOriginFileUpdatedRoots = thisUpdatedRoots.intersect(originUpdatedRoots)
    _aliveRootNamesThatNeedUpdate = syncedTabbedRootNames.intersect(commonWithOriginFileUpdatedRoots).toMutableSet()
    _aliveRootNamesThatNeedUpdate.forEach { mustacheIncludeProcessor.processPdfFileForMustacheRoot(it) }

    val expiredRoots = syncedTabbedRootNames.subtract(thisUpdatedRoots)
    val newRoots = thisUpdatedRoots.subtract(syncedTabbedRootNames)

    // remove roots not needed anymore
    var i = 0
    while (i < _syncedTabbedEditors.size) {
      if (expiredRoots.contains(_syncedTabbedEditors[i].rootName)) {
        _jbTabbedPane.remove(i)
        Disposer.dispose(_syncedTabbedEditors[i])
        _syncedTabbedEditors.removeAt(i)
        i -= 1
      }
      ++i
    }

    // add new identified root files
    newRoots.forEach { rootName -> addPdfFileEditorTab(rootName, mustacheIncludeProcessor) }

    // change selection and focus
    changeSelectionAndFocus(FocusedTabSelectionType.FREESTYLE, true)
  }

  private fun addPdfFileEditorTab(rootName: String, mustacheIncludeProcessor: MustacheIncludeProcessor) {
    val pdfFile = mustacheIncludeProcessor.processPdfFileForMustacheRoot(rootName)
    val editor = PdfFileEditor(project, pdfFile, rootName, mustacheIncludeProcessor)
    Disposer.register(this, editor)
    _jbTabbedPane.insertTab(rootName, null, editor.component, null, ADD_INDEX_FOR_NEW_TAB)
    _syncedTabbedEditors.add(ADD_INDEX_FOR_NEW_TAB, editor)
  }

  private fun changeSelectionAndFocus(focusedTabSelectionType: FocusedTabSelectionType, editorDependable: Boolean = false) {
    if (_jbTabbedPane.tabCount > 0 && _jbTabbedPane.selectedIndex >= _jbTabbedPane.tabCount) {
      _jbTabbedPane.selectedIndex = when (focusedTabSelectionType) {
        FocusedTabSelectionType.FIRST, FocusedTabSelectionType.FREESTYLE -> 0
        FocusedTabSelectionType.LAST -> _jbTabbedPane.tabCount - 1
      }
      _syncedTabbedEditors[_jbTabbedPane.selectedIndex].isFocused =
        if (editorDependable) mainEditor.textEditorWithPreview.component.isShowing else true
      _focusedSyncedTabEditor = _syncedTabbedEditors[_jbTabbedPane.selectedIndex]
    } else if (_jbTabbedPane.selectedIndex >= 0 && _jbTabbedPane.selectedIndex < _jbTabbedPane.tabCount) {
      _jbTabbedPane.selectedIndex = when (focusedTabSelectionType) {
        FocusedTabSelectionType.FIRST -> 0
        FocusedTabSelectionType.LAST -> _jbTabbedPane.tabCount - 1
        FocusedTabSelectionType.FREESTYLE -> _jbTabbedPane.selectedIndex
      }
      _syncedTabbedEditors[_jbTabbedPane.selectedIndex].isFocused =
        if (editorDependable) mainEditor.textEditorWithPreview.component.isShowing else true
      _focusedSyncedTabEditor = _syncedTabbedEditors[_jbTabbedPane.selectedIndex]
    } else {
      _focusedSyncedTabEditor = null
      throw RuntimeException("Something unexpected happened while trying to focus pdf tab in tabbedPane range")
    }
  }

  override fun getComponent(): JComponent = _jbTabbedPane

  override fun getName(): String = NAME

  override fun getPreferredFocusedComponent(): JComponent = _jbTabbedPane.selectedComponent as PdfEditorViewComponent

  val syncedTabbedEditors: MutableList<PdfFileEditor>
    get() = _syncedTabbedEditors

  val activeTab: PdfFileEditor?
    get() = _focusedSyncedTabEditor

  companion object {
    private const val NAME = "Pdf Wrapper Viewer File Editor"
    private val logger = logger<MustachePdfFileEditorWrapper>()
    private const val ADD_INDEX_FOR_NEW_TAB = 0

    private enum class FocusedTabSelectionType {
      FIRST, LAST, FREESTYLE
    }
  }
}
