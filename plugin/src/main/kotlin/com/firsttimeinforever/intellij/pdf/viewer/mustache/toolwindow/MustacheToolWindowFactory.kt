package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContext
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory.MustacheToolWindowContent.Companion.Visitor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.jgoodies.common.base.Objects
import generate.PdfGenerationService
import generate.PdfStructureService.SEG_TYPE
import generate.PdfStructureService.Structure
import generate.Utils.getRelativeMustacheFilePathFromTemplatesPath
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.*
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
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
    private val _contentPanel = SimpleToolWindowPanel(true, true)
    private lateinit var _root: String
    private lateinit var _selectedNodeName: String
    private lateinit var _mustacheContext: MustacheContext
    private var _clickedNode: Pair<MustacheTreeNode, ClickedNodeStyle> = Pair.empty() // higher priority than _selectedNodeName
    private val messageBusConnection = project.messageBus.connect()

    init {
      Disposer.register(this, messageBusConnection)
      val actionToolbar = ActionManager.getInstance().createActionToolbar(
        "Mustache Navigator Toolbar",
        ActionManager.getInstance().getAction("mustache.tool.NavigatorActionsToolbar") as DefaultActionGroup,
        true
      )
      val placehodlerComponent = JBLabel("Loading...", SwingConstants.CENTER)
      _contentPanel.setContent(placehodlerComponent)
      actionToolbar.targetComponent = placehodlerComponent
      _contentPanel.toolbar = actionToolbar.component
      messageBusConnection.subscribe(
        MustacheToolWindowListener.TOPIC,
        object : MustacheToolWindowListener {
          override fun rootChanged(root: String, selectedMustache: VirtualFile, mustacheContext: MustacheContext) {
            _root = root
            _mustacheContext = mustacheContext
            _selectedNodeName = getRelativeMustacheFilePathFromTemplatesPath(
              selectedMustache.canonicalPath,
              mustacheContext.templatesPath,
              mustacheContext.mustacheSuffix
            )
            handleTreeInContentPanel()
          }

          override fun refresh() {
            handleTreeInContentPanel()
          }

          private fun handleTreeInContentPanel() {
            Optional.ofNullable(_mustacheContext.mustacheIncludeProcessor.getPdfForRoot(_root))
              .map(PdfGenerationService.Pdf::structures)
              .map { createTree(_root, it, _selectedNodeName) }
              .ifPresentOrElse({
                actionToolbar.targetComponent = it
                _contentPanel.setContent(ScrollPaneFactory.createScrollPane(it))
              }, {
                // smth
              })
          }
        })
    }

    private fun createTree(root: String, structures: List<Structure>, selectedNodeName: String?): JTree {
      val rootStructure = Structure.createRoot(root, structures)
      rootStructure.customToString = Optional.ofNullable(selectedNodeName).map { v -> "/$v @ /$root" }.orElse(root)
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

      buildTreeNodesFromStructures(rootNode, visitor)
      val tree = Tree(DefaultTreeModel(rootNode))
      tree.addMouseListener(TreeMouseListener(project, _mustacheContext.templatesPath, _mustacheContext.mustacheSuffix) {
        _clickedNode = it
      })

      expandToPaths(tree, selectedNodes)
      handleClickedNode(tree, selectedNodes)
      _clickedNode = Pair.empty()

      return tree
    }

    private class TreeMouseListener(
      val project: Project,
      val templatesPath: String,
      val mustacheSuffix: String,
      val updateClickedNode: ClickedNodeUpdater
    ) :
      MouseAdapter() {
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
          navigateToFile(nodeStructure.parentFragment(), nodeStructure.line(), !nodeStructure.isRoot)
        }
      }

      private fun handleRightClick(node: MustacheTreeNode) {
        val nodeStructure = node.userObject as Structure
        navigateToFile(nodeStructure.parentFragment(), nodeStructure.line(), !nodeStructure.isRoot)
      }

      private fun navigateToFile(mustacheRelativePath: String, line: Int, selectLine: Boolean = false) {
        val file = VfsUtil.findFile(Path.of("$templatesPath/$mustacheRelativePath.$mustacheSuffix"), true)
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

      private enum class ClickedNodeStyle { LEFT, RIGHT }

      private fun interface ClickedNodeUpdater {
        fun update(clickedNode: Pair<MustacheTreeNode, ClickedNodeStyle>)
      }

      private fun interface Visitor {
        fun visit(node: MustacheTreeNode)
      }

      private fun buildTreeNodesFromStructures(node: MustacheTreeNode, @Nullable visitor: Visitor?) {
        val nodeStructures = (node.userObject as Structure).structures()
        for (structure in nodeStructures) {
          val newNode = MustacheTreeNode(structure)
          visitor?.visit(newNode)
          val insideStructures = structure.structures()
          if (insideStructures != null) {
            buildTreeNodesFromStructures(newNode, visitor)
          }
          node.add(newNode)
        }
      }

      fun visitTreeNodes(tree: Tree, visitor: Visitor) {
        fun visit(node: MustacheTreeNode) {
          visitor.visit(node)
          for (child in node.children()) {
            visit(node)
          }
        }
        visit(tree.model.root as MustacheTreeNode)
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


    override fun dispose() {
      messageBusConnection.disconnect()
      Disposer.dispose(messageBusConnection)
    }
  }

  companion object {
    const val NAME = "Mustache"
  }
}

class MustacheTreeNode(userObject: Any) : DefaultMutableTreeNode(userObject)
