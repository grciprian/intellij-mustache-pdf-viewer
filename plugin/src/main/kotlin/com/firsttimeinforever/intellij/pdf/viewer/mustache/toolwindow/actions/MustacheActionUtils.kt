package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

internal object MustacheActionUtils {

  fun hasProject(e: AnActionEvent): Boolean {
    return e.getData(PlatformDataKeys.CONTEXT_COMPONENT) != null
  }

}
