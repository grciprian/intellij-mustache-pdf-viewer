package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache

import com.intellij.util.messages.Topic

fun interface MustacheUpdatePdfFileEditorTabs {

  fun updateTabs(updatedMustacheFileRoots: Set<String?>)

  companion object {
    val TOPIC = Topic(MustacheUpdatePdfFileEditorTabs::class.java)
  }
}
