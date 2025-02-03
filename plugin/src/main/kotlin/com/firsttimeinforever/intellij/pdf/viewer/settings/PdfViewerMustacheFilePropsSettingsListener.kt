package com.firsttimeinforever.intellij.pdf.viewer.settings

fun interface PdfViewerMustacheFilePropsSettingsListener {
  fun filePropsChanged(moduleMustacheContexts: List<ModuleMustacheContext>)
}
