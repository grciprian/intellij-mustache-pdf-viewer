package com.firsttimeinforever.intellij.pdf.viewer.settings

fun interface PdfViewerMustacheFilePropsSettingsListener {
  fun fontsPathChanged(settings: PdfViewerSettings)
}
