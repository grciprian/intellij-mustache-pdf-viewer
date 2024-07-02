package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile

interface MustacheContextService : DumbAware {
  fun getContext(file: VirtualFile): MustacheContext
}
