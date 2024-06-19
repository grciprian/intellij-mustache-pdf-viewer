package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.actions

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheTreeNode
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import generate.PdfStructureService.SEG_TYPE
import generate.PdfStructureService.Structure
import generate.Utils.getRelativeMustacheFilePathFromTemplatesPath
import javax.swing.JTree
import javax.swing.tree.TreePath

class TargetSelectedTemplateInTreeAction : MustacheAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val lastActiveEditor = e.dataContext.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR) as? TextEditorWithPreview
    if (lastActiveEditor?.name == MustacheFileEditor.NAME) {
      val tree = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) as JTree
      val relativeMustacheFilePath =
        getRelativeMustacheFilePathFromTemplatesPath(lastActiveEditor.editor.project, lastActiveEditor.editor.virtualFile.canonicalPath)
      val selectedNodes = mutableListOf<MustacheTreeNode>()

      if (tree.rowCount > 0
        && ((tree.getPathForRow(0).lastPathComponent as MustacheTreeNode).userObject as Structure).name() == relativeMustacheFilePath
      ) {
        selectedNodes.add(tree.getPathForRow(0).lastPathComponent as MustacheTreeNode)
      } else {
        for (i in 1 until tree.rowCount) {
          val node = tree.getPathForRow(i).lastPathComponent as MustacheTreeNode
          val nodeStructure = node.userObject as Structure
          if (nodeStructure.name() == relativeMustacheFilePath && nodeStructure.segType() == SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT) {
            selectedNodes.add(node)
          }
        }
      }
//      TreeUtil.collapseAll(tree, 0)
      tree.selectionPaths = selectedNodes.map { TreePath(it.path) }.toTypedArray()
    }
  }
}
