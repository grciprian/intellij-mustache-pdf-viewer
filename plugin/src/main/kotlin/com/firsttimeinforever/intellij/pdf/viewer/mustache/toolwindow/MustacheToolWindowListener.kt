package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

interface MustacheToolWindowListener {

  fun rootChanged(root: String, selectedMustache: VirtualFile, mustacheContext: MustacheContext)

  fun refresh()

  companion object {
    val TOPIC = Topic(MustacheToolWindowListener::class.java)
  }
}
