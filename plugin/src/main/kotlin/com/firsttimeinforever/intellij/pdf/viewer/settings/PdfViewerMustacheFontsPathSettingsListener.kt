package com.firsttimeinforever.intellij.pdf.viewer.settings

fun interface PdfViewerMustacheFontsPathSettingsListener {
  fun fontsPathChanged(settings: PdfViewerSettings)
}
