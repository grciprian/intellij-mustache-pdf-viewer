package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

fun interface MustacheUpdatePdfFileEditorTabs {

  fun updateTabs(originFile: VirtualFile)

  companion object {
    val TOPIC = Topic(MustacheUpdatePdfFileEditorTabs::class.java)
  }
}
