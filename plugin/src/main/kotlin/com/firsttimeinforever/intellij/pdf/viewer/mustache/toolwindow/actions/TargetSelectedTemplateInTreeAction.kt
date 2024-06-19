package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.actions

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheTreeNode
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import generate.PdfStructureService.SEG_TYPE
import generate.PdfStructureService.Structure
import javax.swing.JTree

class TargetSelectedTemplateInTreeAction : MustacheAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val lastActiveEditor = e.dataContext.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR) as? TextEditorWithPreview
    if (lastActiveEditor?.name == MustacheFileEditor.NAME) {

      val tree = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) as JTree
      for (i in 0 until tree.rowCount) {
        val node = tree.getPathForRow(i).lastPathComponent as MustacheTreeNode
        val nodeStructure = node.userObject as Structure
        if(nodeStructure.segType() == SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT
          && nodeStructure.name() == )
      }
    }
  }
}
