package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.vfs.VirtualFile

fun interface MustacheContextListener {
  fun editorLoaded(virtualFile: VirtualFile)
}
