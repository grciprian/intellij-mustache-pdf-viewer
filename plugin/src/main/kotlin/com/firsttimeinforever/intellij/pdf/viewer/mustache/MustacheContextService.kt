package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.project.DumbAware
import generate.MustacheIncludeProcessor

interface MustacheContextService : DumbAware {
  fun getMustacheIncludeProcessor(): MustacheIncludeProcessor
}
