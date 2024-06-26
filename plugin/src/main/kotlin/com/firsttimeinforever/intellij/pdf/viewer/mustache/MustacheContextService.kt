package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.project.DumbAware

interface MustacheContextService : DumbAware {
  fun getContext(): MustacheContext
}
