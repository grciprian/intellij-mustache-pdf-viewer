package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.messages.Topic
import generate.MustacheIncludeProcessor
import io.ktor.util.reflect.*

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(private val project: Project) : MustacheContextService {

//  private val messageBusConnection = project.messageBus.connect()
  private val mustacheIncludeProcessor: MustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()

  init {
    println(project.name + "ASACEVA")
//    messageBusConnection.subscribe(TOPIC, MustacheContextListener {
//      println(project.name)
//      println(it.canonicalPath)
//    })
  }

  override fun getMustacheIncludeProcessor(): MustacheIncludeProcessor {
    println("SALUUUT!")
    return mustacheIncludeProcessor
  }

  companion object {
//    val TOPIC = Topic(MustacheContextListener::class.java)
  }
}
