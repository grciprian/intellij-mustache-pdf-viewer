package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache

import com.intellij.util.messages.Topic

fun interface MustacheRefreshPdfFileEditorTabs {

  fun refreshTabs(updatedMustacheFileRoots: Set<String?>)

  companion object {
    val TOPIC = Topic(MustacheRefreshPdfFileEditorTabs::class.java)
  }
}
