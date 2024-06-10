package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MustacheFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.PdfFileEditorWrapper
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.ToolWindowManager
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
  private val _mustacheIncludeProcessor = MustacheIncludeProcessor.getInstance()
  private var toolWindowInitialized = false

  init {
    Disposer.register(this, messageBusConnection)
    println("MustacheContextServiceImpl initialized for " + project.name)

    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(AppLifecycleListener.TOPIC, myAppLifecycleListener)

    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {

      override fun selectionChanged(event: FileEditorManagerEvent) {
        val selectedEditor = event.newEditor
        var root: String? = null
        var selectedNode: String? = null
        if (selectedEditor is TextEditorWithPreview
          && selectedEditor.name == MustacheFileEditor.NAME
        ) {
          if (!toolWindowInitialized) {
            toolWindowInitialized = true
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
            toolWindow?.setAvailable(true, null)
            toolWindow?.show()
//            toolWindow?.activate(null)
          }
          val pdfFileEditorWrapper = selectedEditor.previewEditor as PdfFileEditorWrapper
          root = pdfFileEditorWrapper.activeTabRoot
          selectedNode = getRelativePathFromResourcePathWithMustachePrefixPath(selectedEditor.file)
        }
        if (root == null) {
          toolWindowInitialized = false
          val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
//          val contentManager = toolWindow!!.contentManager
//          contentManager.removeAllContents(true)
          toolWindow?.setAvailable(false, null)
          toolWindow?.hide()
//          toolWindow.remove()
        }
        ApplicationManager.getApplication().messageBus.syncPublisher(MustacheToolWindowListener.TOPIC)
          .rootUpdated(root, selectedNode)
      }
    })
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
        _mustacheIncludeProcessor.processFileIncludePropsMap()
      }

      if (events.any {
          val file = it.file ?: return
          val isDirectory = file.isDirectory
          if (it is VFileMoveEvent) {
            return@any (checkFilePathWithFontsPathBasedOnFileType(it.oldPath, isDirectory)
              || checkFilePathWithFontsPathBasedOnFileType(it.newPath, isDirectory))
          }
          if (it is VFilePropertyChangeEvent) {
            return@any (checkFilePathWithFontsPathBasedOnFileType(it.oldPath, isDirectory)
              || checkFilePathWithFontsPathBasedOnFileType(it.newPath, isDirectory))
          }
          return@any checkFilePathWithFontsPathBasedOnFileType(file.canonicalPath ?: "", isDirectory)
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

    private fun cleanupMtfPdf() {
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
    return _mustacheIncludeProcessor
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    Disposer.dispose(messageBusConnection)
  }
}
