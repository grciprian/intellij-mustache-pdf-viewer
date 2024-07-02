package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.actions

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContextService
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheTreeNode
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.roots.ProjectRootManager
import generate.PdfStructureService.SEG_TYPE
import generate.PdfStructureService.Structure
import generate.Utils.getRelativeMustacheFilePathFromTemplatesPath
import javax.swing.JTree
import javax.swing.tree.TreePath

class TargetSelectedTemplateInTreeAction : MustacheAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val lastActiveEditor = e.dataContext.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR) as? TextEditorWithPreview ?: return
    if (lastActiveEditor.name == MustacheFileEditor.NAME) {
      val tree = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return
      val project = e.dataContext.getData(PlatformDataKeys.PROJECT) ?: return
      val file = lastActiveEditor.editor.virtualFile
      val mustacheContext = project.getService(MustacheContextService::class.java).getContext(file)
      val relativeFilePath =
        getRelativeMustacheFilePathFromTemplatesPath(
          file.canonicalPath,
          mustacheContext.templatesPath,
          mustacheContext.mustacheSuffix
        )
      val selectedNodes = mutableListOf<MustacheTreeNode>()

      if (tree.rowCount > 0
        && ((tree.getPathForRow(0).lastPathComponent as MustacheTreeNode).userObject as Structure).name() == relativeFilePath
      ) {
        selectedNodes.add(tree.getPathForRow(0).lastPathComponent as MustacheTreeNode)
      } else {
        for (i in 1 until tree.rowCount) {
          val node = tree.getPathForRow(i).lastPathComponent as MustacheTreeNode
          val nodeStructure = node.userObject as Structure
          if (nodeStructure.name() == relativeFilePath && nodeStructure.segType() == SEG_TYPE.INCLUDED_TEMPLATE_SEGMENT) {
            selectedNodes.add(node)
          }
        }
      }
//      TreeUtil.collapseAll(tree, 0)
      tree.selectionPaths = selectedNodes.map { TreePath(it.path) }.toTypedArray()
    }
  }
}
