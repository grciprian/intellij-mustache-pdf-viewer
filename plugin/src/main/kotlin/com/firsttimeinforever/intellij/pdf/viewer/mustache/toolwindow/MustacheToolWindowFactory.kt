package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.TreeExpandCollapse
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class MustacheToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
    val toolWindowContent = CalendarToolWindowContent(toolWindow)
    val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class CalendarToolWindowContent(toolWindow: ToolWindow) {

    private val _contentPanel = JPanel()

    init {
      _contentPanel.layout = BorderLayout(0, 20)
      _contentPanel.border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
      _contentPanel.add(createTree(), BorderLayout.PAGE_START)
    }

    @NotNull
    private fun createTree(): JBScrollPane {
      val treePanel = JBScrollPane()

      TreeExpandCollapse.expandAll(tree)
      return treePanel
    }

    private fun setIconLabel(label: JLabel, imagePath: String) {
      label.icon = ImageIcon(javaClass.getResource(imagePath))
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {
      private const val TIME_ICON_PATH = "/icons/toolwindow/Time-icon.png"
    }
  }
}
