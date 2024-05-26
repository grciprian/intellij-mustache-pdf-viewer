package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import generate.MustacheIncludeProcessor
import generate.Utils.*
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(private val project: Project) : MustacheContextService, Disposable {

  private val settings = PdfViewerSettings.instance
  private val fileChangedListener = FileChangedListener()
  private val myAppLifecycleListener = MyAppLifecycleListener()
  private val messageBusConnection = project.messageBus.connect()
  private val mustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()

  init {
    Disposer.register(this, messageBusConnection)
    println("MustacheContextServiceImpl initialized for " + project.name)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, myAppLifecycleListener)
  }

  private inner class FileChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      val fontsPath = Path.of(settings.customMustacheFontsPath).toCanonicalPath()

      fun checkFilePathWithFontsPathBasedOnFileType(filePath: String, isDirectory: Boolean): Boolean {
        val canonicalFilePath = Path.of(filePath).toCanonicalPath()
        if (isDirectory) return canonicalFilePath == fontsPath
        return canonicalFilePath.startsWith("$fontsPath/")
      }

      if (events.any { isFileUnderResourcesPathWithPrefix(project, it.file) }) {
        // TODO keep in mind operation concurrency?
        // redoing mustache dependency tree
        mustacheIncludeProcessor.processFileIncludePropsMap()
      }

      if (events.any {
          val file = it.file ?: return
          val isDirectory = file.isDirectory
          if (it is VFileMoveEvent) {
            return@any (checkFilePathWithFontsPathBasedOnFileType(it.oldPath, isDirectory)
              || checkFilePathWithFontsPathBasedOnFileType(it.newPath, isDirectory))
          }
          val filePath = file.canonicalPath ?: ""
          return@any checkFilePathWithFontsPathBasedOnFileType(filePath, isDirectory)
        }) {
        settings.notifyMustacheFontsPathSettingsListeners()
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
    messageBusConnection.disconnect()
    Disposer.dispose(messageBusConnection)
  }
}
