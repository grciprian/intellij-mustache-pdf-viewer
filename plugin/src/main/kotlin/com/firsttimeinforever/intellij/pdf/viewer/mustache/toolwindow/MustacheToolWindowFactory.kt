package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MUSTACHE_SUFFIX
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorWrapper
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.RESOURCES_WITH_MUSTACHE_PREFIX_PATH
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
import generate.PdfStructureService.Structure
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.*
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
    private val messageBusConnection = project.messageBus.connect()
    private val mustacheContextService = project.service<MustacheContextService>()
    private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()

    init {
      Disposer.register(this, messageBusConnection)
      _contentPanel.layout = BorderLayout(0, 20)
      _contentPanel.border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
      messageBusConnection.subscribe(PdfFileEditorWrapper.MUSTACHE_TOOL_WINDOW_LISTENER_TOPIC,
        PdfFileEditorWrapper.MustacheToolWindowListener { root, selectedNodeName ->
          if (_contentPanel.components.isNotEmpty()) _contentPanel.remove(0)
          if (root != null) _contentPanel.add(createTree(root, selectedNodeName), BorderLayout.CENTER)
        })
    }

    private fun createTree(root: String, selectedNodeName: String?): JBScrollPane {
      val rootNode = DefaultMutableTreeNode(Structure(root, root, 0))
      val structures = mustacheIncludeProcessor.getPdfForRoot(root).structures
      val selectedNodes = mutableListOf<DefaultMutableTreeNode>()
      populateNodeFromStructures(rootNode, structures) {
        if ((it.userObject as Structure).name == selectedNodeName) selectedNodes.add(it)
      }
      val treeModel = DefaultTreeModel(rootNode)
      val tree = Tree(treeModel)
      tree.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          handleContextMenu(e)
        }

        override fun mouseReleased(e: MouseEvent) {
          handleContextMenu(e)
        }

        private fun handleContextMenu(mouseEvent: MouseEvent) {
          if (!mouseEvent.isPopupTrigger) return
          println("Go to file")
          val x = mouseEvent.x
          val y = mouseEvent.y

          val t = mouseEvent.source as JTree
          val path = t.getPathForLocation(x, y) ?: return
          tree.selectionPath = path
          val node = path.lastPathComponent as DefaultMutableTreeNode
          val nodeStructure = node.userObject as Structure
          val relativePath = nodeStructure.parentFragment
          val file = VfsUtil.findFile(Path.of("$RESOURCES_WITH_MUSTACHE_PREFIX_PATH$relativePath.$MUSTACHE_SUFFIX"), true)
            ?: return
          val line = if (nodeStructure.line > 0) nodeStructure.line - 1 else 0
          val editor =
            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, line, 0), true)
              ?: return
          val document = editor.document
          val startOffset = document.getLineStartOffset(line)
          val endOffset = document.getLineEndOffset(line)
          val selectionModel = editor.selectionModel
          selectionModel.removeSelection()
          selectionModel.setSelection(startOffset, endOffset)

//          val popup = JBPopupMenu()
//          val item = JBMenuItem(object : AbstractAction("Go to file") {
//            override fun actionPerformed(e: ActionEvent?) {
//              // TODO implement smth
//            }
//          })
//          popup.add(item)
//          popup.show(tree, x, y)
        }
      })
      expandToPaths(tree, selectedNodes)
      val scrollTree = JBScrollPane()
      scrollTree.setViewportView(tree)
      return scrollTree
    }

    fun setIconLabel(label: JLabel, imagePath: String) {
      label.icon = ImageIcon(javaClass.getResource(imagePath))
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {
      private const val TIME_ICON_PATH = "/icons/toolwindow/Time-icon.png"

      private fun interface Visitor {
        fun visit(node: DefaultMutableTreeNode)
      }

//      var name: ColumnInfo<*, *> = object : ColumnInfo<Any?, Any?>("Name") {
//        override fun valueOf(o: Any?): Any? {
//          return if (o is DefaultMutableTreeNode && o.userObject is Structure) {
//            return (o.userObject as Structure).name
//          } else o
//        }
//      }
//
//      var line: ColumnInfo<*, *> = object : ColumnInfo<Any?, Any?>("Line") {
//        override fun valueOf(o: Any?): Any? {
//          return if (o is DefaultMutableTreeNode && o.userObject is Structure) {
//            return (o.userObject as Structure).line
//          } else o
//        }
//      }
//
//      var columns: Array<ColumnInfo<*, *>> = arrayOf(name, line)

      private fun populateNodeFromStructures(node: DefaultMutableTreeNode, structures: List<Structure>, @Nullable visitor: Visitor?) {
        for (structure in structures) {
          val newNode = DefaultMutableTreeNode(structure)
          visitor?.visit(newNode)
          val insideStructures = structure.structures
          if (insideStructures != null) {
            populateNodeFromStructures(newNode, insideStructures, visitor)
          }
          node.add(newNode)
        }
      }

      private fun expandToPaths(tree: Tree, nodes: List<DefaultMutableTreeNode>) {
        val nodesCopy = nodes.toMutableList()
        tree.expandsSelectedPaths = true
        tree.scrollsOnExpand = true
        if (nodesCopy.isEmpty()) {
          nodesCopy.add(tree.model.root as DefaultMutableTreeNode)
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
    const val NAME = "Mustache Tool"
  }
}
