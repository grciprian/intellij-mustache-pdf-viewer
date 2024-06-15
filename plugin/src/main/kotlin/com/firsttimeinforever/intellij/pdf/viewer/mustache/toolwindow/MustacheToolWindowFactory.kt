package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MUSTACHE_SUFFIX
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.TEMPLATES_PATH
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.jgoodies.common.base.Objects
import generate.PdfGenerationService
import generate.PdfStructureService.Structure
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class MustacheToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return false
  }

  override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
    val toolWindowContent = MustacheToolWindowContent(project, toolWindow)
    Disposer.register(project, toolWindowContent) // something something about project as disposable
    val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class MustacheToolWindowContent(private val project: Project, toolWindow: ToolWindow) : Disposable {
    private val _contentPanel = JPanel()
    private lateinit var _root: String
    private var _selectedNodeName: String? = null
    private var _clickedNodeStructure: Structure? = null // higher priority than _selectedNodeName
    private val messageBusConnection = project.messageBus.connect()
    private val mustacheContextService = project.service<MustacheContextService>()
    private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()

    init {
      Disposer.register(this, messageBusConnection)
      _contentPanel.layout = GridLayout(0, 1)
      _contentPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      messageBusConnection.subscribe(
        MustacheToolWindowListener.TOPIC,
        object : MustacheToolWindowListener {
          override fun rootChanged(root: String, selectedNodeName: String?) {
            _root = root
            _selectedNodeName = selectedNodeName
            handleTreeInContentPanel(root, selectedNodeName)
          }

          override fun refresh() {
            handleTreeInContentPanel(_root, _selectedNodeName)
          }

          private fun handleTreeInContentPanel(root: String, selectedNodeName: String?) {
            if (_contentPanel.components.isNotEmpty()) _contentPanel.remove(0)
            Optional.ofNullable(mustacheIncludeProcessor.getPdfForRoot(root))
              .map(PdfGenerationService.Pdf::structures)
              .map {
                createTree(root, it, selectedNodeName)
              }
              .ifPresentOrElse({
                _contentPanel.add(it)
              }, {
                // smth
              })
          }
        })
    }

    private fun createTree(root: String, structures: List<Structure>, selectedNodeName: String?): JBScrollPane {
      val selectedNodes = mutableListOf<MustacheTreeNode>()
      val rootNode = MustacheTreeNode(Structure.createRootStructure(root, selectedNodeName))
      populateNodeFromStructures(rootNode, structures) {
        if (_clickedNodeStructure != null) {
          if (Objects.equals(it.userObject as Structure, _clickedNodeStructure)) selectedNodes.add(it)
          return@populateNodeFromStructures
        }
        if ((it.userObject as Structure).name() == selectedNodeName) selectedNodes.add(it)
      }
      _clickedNodeStructure = null
      val tree = Tree(DefaultTreeModel(rootNode))
      tree.addMouseListener(TreeMouseListener(project, this))
      expandToPaths(tree, selectedNodes)
      val scrollTree = JBScrollPane(tree)
      scrollTree.border = BorderFactory.createEmptyBorder()
      return scrollTree
    }

    private class TreeMouseListener(val project: Project, val toolWindowContent: MustacheToolWindowContent) : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        handleMouseEvent(e)
      }

      override fun mouseReleased(e: MouseEvent) {
        handleMouseEvent(e)
      }

      private fun handleMouseEvent(mouseEvent: MouseEvent) {
        if (!mouseEvent.isPopupTrigger) return
        val x = mouseEvent.x
        val y = mouseEvent.y

        val t = mouseEvent.source as JTree
        val path = t.getPathForLocation(x, y) ?: return
        t.selectionPath = path
        val node = path.lastPathComponent as MustacheTreeNode
        val nodeStructure = node.userObject as Structure
        val relativePath = nodeStructure.parentFragment()
        val file = VfsUtil.findFile(Path.of("$TEMPLATES_PATH$relativePath.$MUSTACHE_SUFFIX"), true)
          ?: return
        val line = if (nodeStructure.line() > 0) nodeStructure.line() - 1 else 0
        val editor =
          FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, line, 0), true)
            ?: return
        val document = editor.document
        val startOffset = document.getLineStartOffset(line)
        val endOffset = document.getLineEndOffset(line)
        val selectionModel = editor.selectionModel
        selectionModel.removeSelection()
        selectionModel.setSelection(startOffset, endOffset)

        toolWindowContent._clickedNodeStructure = nodeStructure
      }
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {
      private fun interface Visitor {
        fun visit(node: MustacheTreeNode)
      }

      private fun populateNodeFromStructures(node: MustacheTreeNode, structures: List<Structure>, @Nullable visitor: Visitor?) {
        for (structure in structures) {
          val newNode = MustacheTreeNode(structure)
          visitor?.visit(newNode)
          val insideStructures = structure.structures()
          if (insideStructures != null) {
            populateNodeFromStructures(newNode, insideStructures, visitor)
          }
          node.add(newNode)
        }
      }

      private fun expandToPaths(tree: Tree, nodes: List<MustacheTreeNode>) {
        val nodesCopy = nodes.toMutableList()
        tree.expandsSelectedPaths = true
        tree.scrollsOnExpand = true
        if (nodesCopy.isEmpty()) {
          nodesCopy.add(tree.model.root as MustacheTreeNode)
        }
        tree.selectionPaths = nodesCopy.map { TreePath(it.path) }.toTypedArray()
        tree.scrollPathToVisible(tree.selectionPaths?.get(0))
      }
    }

    private class MustacheTreeNode(userObject: Any) : DefaultMutableTreeNode(userObject)

    override fun dispose() {
      messageBusConnection.disconnect()
      Disposer.dispose(messageBusConnection)
    }
  }

  companion object {
    const val NAME = "Mustache Tool"
  }
}
