package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import generate.MustacheIncludeProcessor
import generate.Utils.MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(project: Project) : MustacheContextService, Disposable {

  private val mustacheIncludeProcessor: MustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()
  private val myAppLifecycleListener = MyAppLifecycleListener()
  private val fileChangedListener = FileChangedListener()
  private val messageBusConnection = project.messageBus.connect()

  init {
    Disposer.register(this, messageBusConnection)
    println("MustacheContextServiceImpl initialized for " + project.name)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, myAppLifecycleListener)
  }

  private inner class FileChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      // TODO keep in mind operation concurrency
      println("RECALC")
      mustacheIncludeProcessor.processFileIncludePropsMap()
    }
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
        if (it.name.contains(MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX)) {
          Files.deleteIfExists(it.toNioPath())
        }
        return@processFileRecursivelyWithoutIgnored true
      }
    }
  }

  override fun getMustacheIncludeProcessor(): MustacheIncludeProcessor {
    return mustacheIncludeProcessor
  }

  override fun dispose() {
    Disposer.dispose(messageBusConnection)
  }
}
