package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.actions

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustachePdfFileEditorWrapper
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.DumbAware

abstract class MustacheAction : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    val p = e.presentation
    p.isEnabled = isAvailable(e)
//    val editor = findEditorInView(e)
//    println(editor?.file?.canonicalPath)
  }

  protected open fun isAvailable(e: AnActionEvent): Boolean {
    return MustacheActionUtils.hasProject(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {

    /**
     * Find the editor that belongs to the given [event].
     *
     * If the editor that currently has focus is a [PdfFileEditor], return that one. If not, look for any other PdfFileEditors that are open
     * (split view) and select the first we find there. If there is no [PdfFileEditor] selected, this returns null. Note that in that case it
     * is possible that there is a PDF open somewhere, but it is not in view.
     */
    fun findEditorInView(event: AnActionEvent): PdfFileEditor? {
      var focusedEditor = event.getData(PlatformDataKeys.FILE_EDITOR)

      // if we deal with a MustacheFileEditor(TextEditorWithPreview) than we have to get the wrapped PdfFileEditor
      if (focusedEditor is TextEditorWithPreview
        && focusedEditor.name == MustacheFileEditor.NAME
      ) {
        val mustachePdfFileEditorWrapper = focusedEditor.previewEditor as MustachePdfFileEditorWrapper
        focusedEditor = mustachePdfFileEditorWrapper.activeTab
      }

      return focusedEditor as? PdfFileEditor ?: run {
        val project = event.project ?: return null
        FileEditorManager.getInstance(project).selectedEditors.firstOrNull { it is PdfFileEditor } as? PdfFileEditor
      }
    }
  }

}
