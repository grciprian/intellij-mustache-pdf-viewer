package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import generate.MustacheIncludeProcessor
import generate.Utils.PDF_MUSTACHE_TEMPORARY_FILE_EXTENSION
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(project: Project) : MustacheContextService {

  private val mustacheIncludeProcessor: MustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()
  private val myAppLifecycleListener = MyAppLifecycleListener()

  init {
    println("MustacheContextServiceImpl initialized for " + project.name)
    ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, myAppLifecycleListener)
  }

  private inner class MyAppLifecycleListener : AppLifecycleListener {

    override fun appClosing() {
      super.appClosing()
      cleanupMtfPdf()
    }

    fun cleanupMtfPdf() {
      val root = VfsUtil.findFile(Path.of("./"), true) as VirtualFile
      VfsUtil.processFileRecursivelyWithoutIgnored(root) {
        if (it.isDirectory) return@processFileRecursivelyWithoutIgnored true
        if (it.name.contains(PDF_MUSTACHE_TEMPORARY_FILE_EXTENSION)) {
          Files.deleteIfExists(it.toNioPath())
        }
        return@processFileRecursivelyWithoutIgnored true
      }
    }
  }

  override fun getMustacheIncludeProcessor(): MustacheIncludeProcessor {
    return mustacheIncludeProcessor
  }
}
