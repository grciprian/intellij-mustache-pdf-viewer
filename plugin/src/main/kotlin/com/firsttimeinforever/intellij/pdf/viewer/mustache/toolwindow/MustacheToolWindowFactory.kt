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
import generate.PdfStructureService.Structure
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class MustacheToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return false
  }

  override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
    val toolWindowContent = MustacheToolWindowContent(project, toolWindow)
    val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class MustacheToolWindowContent(private val project: Project, toolWindow: ToolWindow) : Disposable {

    private val messageBusConnection = project.messageBus.connect()
    private val _contentPanel = JPanel()
    private val mustacheContextService = project.service<MustacheContextService>()
    private val mustacheIncludeProcessor = mustacheContextService.getMustacheIncludeProcessor()

    init {
      Disposer.register(this, messageBusConnection)
      _contentPanel.layout = BorderLayout(0, 20)
      _contentPanel.border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
      messageBusConnection.subscribe(
        PdfFileEditorWrapper.MUSTACHE_TOOL_WINDOW_LISTENER_TOPIC,
        PdfFileEditorWrapper.MustacheToolWindowListener {
          if (_contentPanel.components.isNotEmpty()) _contentPanel.remove(0)
          _contentPanel.add(createTree(it), BorderLayout.CENTER)
        })
    }

    private fun createTree(root: String): JPanel {
      val panel = JPanel()

//      val editor = FileEditorManager.getInstance(project).selectedEditor
//      if (editor is TextEditorWithPreview && editor.name == MustacheFileEditor.NAME) {
//        mustacheIncludeProcessor.getRootsForMustache(editor.file).map {
      val rootNode = DefaultMutableTreeNode(root)
      val structures = mustacheIncludeProcessor.getPdfForRoot(root).structures
      populateNodeFromStructures(rootNode, structures)
      val tree = Tree(DefaultTreeModel(rootNode))
      TreeExpandCollapse.expandAll(tree)
      val scrollTree = JBScrollPane()
      scrollTree.setViewportView(tree)
//          return@map scrollTree
//        }.forEach { panel.add(it) }
//      }
      panel.add(scrollTree)

      return panel
    }

    private fun setIconLabel(label: JLabel, imagePath: String) {
      label.icon = ImageIcon(javaClass.getResource(imagePath))
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {
      private const val TIME_ICON_PATH = "/icons/toolwindow/Time-icon.png"

      private fun populateNodeFromStructures(node: DefaultMutableTreeNode, structures: MutableList<Structure>) {
        for (structure in structures) {
          val newNode = DefaultMutableTreeNode(structure)
          val insideStructures = structure.structures
          if (insideStructures != null) {
            populateNodeFromStructures(newNode, insideStructures)
          }
          node.add(newNode)
        }
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
