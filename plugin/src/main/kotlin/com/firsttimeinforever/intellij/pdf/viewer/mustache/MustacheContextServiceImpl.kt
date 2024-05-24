package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
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
import generate.Utils.JAVA_RESOURCES_WITH_MUSTACHE_PREFIX
import generate.Utils.MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(private val project: Project) : MustacheContextService, Disposable {

  private val settings = PdfViewerSettings.instance
  private val fileChangedListener = FileChangedListener()
  private val myAppLifecycleListener = MyAppLifecycleListener()
  private val messageBusConnection = project.messageBus.connect()
  private val mustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()
  private var canNotifyIfFontsPathIsInvalid = false

  init {
    Disposer.register(this, messageBusConnection)
    println("MustacheContextServiceImpl initialized for " + project.name)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, myAppLifecycleListener)
    VirtualFileManager.getInstance().
  }

  private inner class FileChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      if (events.any { it.path.startsWith(project.basePath + "/" + JAVA_RESOURCES_WITH_MUSTACHE_PREFIX) }) {
        // TODO keep in mind operation concurrency?
        // redoing mustache dependency tree
        println("REDO MUSTACHE DEPENDENCY TREE")
        mustacheIncludeProcessor.processFileIncludePropsMap()
      }
      if (events.any {
          println("fileType " + it.file?.isDirectory)
          println("filePath " + it.)
          (it.file?.isDirectory == true && it.path == settings.customMustacheFontsPath)
            || (it.file?.isDirectory == false && it.path.startsWith(settings.customMustacheFontsPath))
        }) {
        println("IS FILE OR DIR")
        canNotifyIfFontsPathIsInvalid = true
        settings.notifySettingsFontsPathListeners()
      } else if (canNotifyIfFontsPathIsInvalid) {
        println("ALTCEVA")
        canNotifyIfFontsPathIsInvalid = false
        settings.notifySettingsFontsPathListeners()
      }
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
