package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.TreeExpandCollapse
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.toArray
import generate.PdfStructureService.Structure
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class MustacheToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return false
  }

  override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
    val toolWindowContent = MustacheToolWindowContent(project, toolWindow)
    Disposer.register(project, toolWindowContent)
    val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, null, false)
    toolWindow.contentManager.addContent(content)
  }

  private class MustacheToolWindowContent(private val project: Project, toolWindow: ToolWindow) : Disposable {
    private val _contentPanel = JPanel()
    private val messageBusConnection = project.messageBus.connect()
    private val mustacheContextService = project.service<MustacheContextService>()
    private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()

    init {
      Disposer.register(this, messageBusConnection)
      _contentPanel.layout = BorderLayout(0, 20)
      _contentPanel.border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
      messageBusConnection.subscribe(
        PdfFileEditorWrapper.MUSTACHE_TOOL_WINDOW_LISTENER_TOPIC,
        PdfFileEditorWrapper.MustacheToolWindowListener { root, selectedNodeName ->
          if (_contentPanel.components.isNotEmpty()) _contentPanel.remove(0)
          if (root != null) _contentPanel.add(createTree(root, selectedNodeName), BorderLayout.CENTER)
          _contentPanel.updateUI()
        })
    }

    private fun createTree(root: String, selectedNodeName: String?): JBScrollPane {
      val rootNode = DefaultMutableTreeNode(Structure(root, 0))
      val structures = mustacheIncludeProcessor.getPdfForRoot(root).structures
      val selectedTreePaths = mutableListOf<TreePath>()
      populateNodeFromStructures(rootNode, structures) {
        if ((it.userObject as Structure).name == selectedNodeName)
          selectedTreePaths.add(TreePath(it))
      }
      val treeModel = DefaultTreeModel(rootNode)
      val tree = Tree(treeModel)
      expandToPaths(tree, selectedTreePaths)
      val scrollTree = JBScrollPane()
      scrollTree.setViewportView(tree)
      return scrollTree
    }

    private fun setIconLabel(label: JLabel, imagePath: String) {
      label.icon = ImageIcon(javaClass.getResource(imagePath))
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {
      private const val TIME_ICON_PATH = "/icons/toolwindow/Time-icon.png"

      private fun interface Visitor {
        fun visit(node: DefaultMutableTreeNode)
      }

      private fun populateNodeFromStructures(node: DefaultMutableTreeNode, structures: List<Structure>, @Nullable visitor: Visitor?) {
        for (structure in structures) {
          val newNode = DefaultMutableTreeNode(structure)
          val insideStructures = structure.structures
          if (insideStructures != null) {
            populateNodeFromStructures(newNode, insideStructures, visitor)
          }
          node.add(newNode)
          visitor?.visit(node)
        }
      }

      private fun expandToPaths(tree: Tree, treePaths: List<TreePath>) {
        if(treePaths.isEmpty()) {
          TreeExpandCollapse.expandAll(tree)
          return
        }
        tree.expandsSelectedPaths = true
        tree.scrollsOnExpand = true
        tree.selectionPaths = treePaths.toTypedArray()
        tree.scrollPathToVisible(treePaths[0])
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