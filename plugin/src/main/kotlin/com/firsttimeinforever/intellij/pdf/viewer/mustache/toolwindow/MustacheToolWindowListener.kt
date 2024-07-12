package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.firsttimeinforever.intellij.pdf.viewer.mustache.MustacheContext
import com.intellij.util.messages.Topic

interface MustacheToolWindowListener {

  fun rootChanged(root: String, mustacheContext: MustacheContext)

  fun refresh()

  companion object {
    val TOPIC = Topic(MustacheToolWindowListener::class.java)
  }
}
