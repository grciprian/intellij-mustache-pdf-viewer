package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import generate.MustacheIncludeProcessor

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(project: Project) : MustacheContextService {

  private val mustacheIncludeProcessor: MustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()

  init {
    println("MustacheContextServiceImpl initialized for " + project.name)
  }

  override fun getMustacheIncludeProcessor(): MustacheIncludeProcessor {
    return mustacheIncludeProcessor
  }
}
