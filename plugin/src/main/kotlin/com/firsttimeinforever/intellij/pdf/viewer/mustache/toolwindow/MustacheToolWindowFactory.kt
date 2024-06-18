package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory.MustacheToolWindowContent.Companion.Visitor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MUSTACHE_SUFFIX
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.TEMPLATES_PATH
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.jgoodies.common.base.Objects
import generate.PdfGenerationService
import generate.PdfStructureService.SEG_TYPE
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

  override fun init(toolWindow: ToolWindow) {
    toolWindow.setIcon(IconLoader.getIcon("/icons/toolwindow/openMustache.svg", Companion::class.java))
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
    private var _clickedNode: Pair<MustacheTreeNode, ClickedNodeStyle> = Pair.empty() // higher priority than _selectedNodeName
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
                _contentPanel.repaint()
              }, {
                // smth
              })
          }
        })
    }

    private fun createTree(root: String, structures: List<Structure>, selectedNodeName: String?): JBScrollPane {
      val rootStructure = Structure.createRootStructure(root)
      rootStructure.customToString = Optional.ofNullable(selectedNodeName).map { v -> "$v @ $root" }.orElse(root)
      val rootNode = MustacheTreeNode(rootStructure)
      val selectedNodes = mutableListOf<MustacheTreeNode>()
      val visitor = Visitor { node ->
        if (_clickedNode != Pair.empty<MustacheTreeNode, ClickedNodeStyle>()) {
          if (Objects.equals(node.userObject as Structure, _clickedNode.first.userObject as Structure)) {
            selectedNodes.add(node)
          }
        } else if ((node.userObject as Structure).name() == selectedNodeName) {
          selectedNodes.add(node)
        }
      }

      buildTreeNodesFromStructures(rootNode, structures, visitor)
      val tree = Tree(DefaultTreeModel(rootNode))
      tree.addMouseListener(TreeMouseListener(project) {
        _clickedNode = it
      })

      expandToPaths(tree, selectedNodes)
      handleClickedNode(tree, selectedNodes)
      _clickedNode = Pair.empty()

      val scrollTree = JBScrollPane(tree)
      scrollTree.border = BorderFactory.createEmptyBorder()
      return scrollTree
    }

    private class TreeMouseListener(val project: Project, val updateClickedNode: ClickedNodeUpdater) : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        handleMouseEvent(e)
      }

      private fun handleMouseEvent(e: MouseEvent) {
        val x = e.x
        val y = e.y

        val t = e.source as JTree
        val path = t.getPathForLocation(x, y) ?: return
        t.selectionPath = path

        val node = path.lastPathComponent as MustacheTreeNode
        if (e.button == MouseEvent.BUTTON1) {
          updateClickedNode.update(Pair(node, ClickedNodeStyle.LEFT))
          handleLeftClick(node)
        }
        if (e.button == MouseEvent.BUTTON3) {
          updateClickedNode.update(Pair(node, ClickedNodeStyle.RIGHT))
          handleRightClick(node)
        }
      }

      private fun handleLeftClick(node: MustacheTreeNode) {
        val nodeStructure = node.userObject as Structure
        if (nodeStructure.segType() == SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT && nodeStructure.isIncludedTemplateSegmentValid) {
          navigateToFile(nodeStructure.name(), 0)
        } else {
          navigateToFile(nodeStructure.parentFragment(), nodeStructure.line(), !isRootNodeStructure(nodeStructure))
        }
      }

      private fun handleRightClick(node: MustacheTreeNode) {
        val nodeStructure = node.userObject as Structure
        navigateToFile(nodeStructure.parentFragment(), nodeStructure.line(), !isRootNodeStructure(nodeStructure))
      }

      private fun navigateToFile(mustacheRelativePath: String, line: Int, selectLine: Boolean = false) {
        val file = VfsUtil.findFile(Path.of("$TEMPLATES_PATH$mustacheRelativePath.$MUSTACHE_SUFFIX"), true)
          ?: return
        val ln = line.coerceAtLeast(1) - 1
        val editor =
          FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, ln, 0), true)
            ?: return
        if (selectLine) {
          val document = editor.document
          val startOffset = document.getLineStartOffset(ln)
          val endOffset = document.getLineEndOffset(ln)
          val selectionModel = editor.selectionModel
          selectionModel.removeSelection()
          selectionModel.setSelection(startOffset, endOffset)
        }
      }
    }

    private fun handleClickedNode(tree: Tree, nodes: List<MustacheTreeNode>) {
      if (_clickedNode != Pair.empty<MustacheTreeNode, ClickedNodeStyle>()
        && _clickedNode.second == ClickedNodeStyle.LEFT
        && (_clickedNode.first.userObject as Structure).segType() == SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT
        && nodes.isNotEmpty() && nodes[0].children().hasMoreElements()
      ) {
        tree.expandPath(TreePath(nodes[0].path))
      }
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {

      private class MustacheTreeNode(userObject: Any) : DefaultMutableTreeNode(userObject)
      private enum class ClickedNodeStyle { LEFT, RIGHT }

      private fun interface ClickedNodeUpdater {
        fun update(clickedNode: Pair<MustacheTreeNode, ClickedNodeStyle>)
      }

      private fun interface Visitor {
        fun visit(node: MustacheTreeNode)
      }

      private fun buildTreeNodesFromStructures(node: MustacheTreeNode, structures: List<Structure>, @Nullable visitor: Visitor?) {
        for (structure in structures) {
          val newNode = MustacheTreeNode(structure)
          visitor?.visit(newNode)
          val insideStructures = structure.structures()
          if (insideStructures != null) {
            buildTreeNodesFromStructures(newNode, insideStructures, visitor)
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

      private fun isRootNodeStructure(nodeStructure: Structure): Boolean {
        return nodeStructure.line() == -1
      }
    }


    override fun dispose() {
      messageBusConnection.disconnect()
      Disposer.dispose(messageBusConnection)
    }
  }

  companion object {
    const val NAME = "Mustache Tool"
  }
}
