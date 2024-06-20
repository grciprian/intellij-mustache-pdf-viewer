package com.firsttimeinforever.intellij.pdf.viewer.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class PdfPageSpreadActionGroup : DefaultActionGroup() {
  // TODO: investigate why isPopup should not be overridden
//  override fun isPopup(): Boolean = true

  override fun update(event: AnActionEvent) {
    event.presentation.isVisible = PdfAction.hasEditorInView(event)
    event.presentation.isEnabled = PdfAction.findController(event) != null
  }
}
