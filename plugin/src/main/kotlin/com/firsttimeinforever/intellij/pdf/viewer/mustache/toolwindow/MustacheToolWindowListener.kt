package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.intellij.util.messages.Topic

interface MustacheToolWindowListener {

  fun rootChanged(root: String, selectedNodeName: String?)

  fun refresh()

  companion object {
    val TOPIC = Topic(MustacheToolWindowListener::class.java)
  }
}