package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.TEMPLATES_PATH
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustachePdfFileEditorWrapper
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheRefreshPdfFileEditorTabs
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheUpdatePdfFileEditorTabs
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
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
  private val appLifecycleListener = MyAppLifecycleListener()
  private val fileEditorManagerListener = MyFileEditorManagerListener()
  private val messageBusConnection = project.messageBus.connect()
  private val _mustacheIncludeProcessor = MustacheIncludeProcessor.getInstance(project);
  private var toolWindowInitialized = false
  private var selectedEditor: TextEditorWithPreview? = null

  init {
    Disposer.register(this, messageBusConnection)
    println("MustacheContextServiceImpl initialized for " + project.name)

    messageBusConnection.subscribe(AppLifecycleListener.TOPIC, appLifecycleListener)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorManagerListener)
  }

  private inner class MyAppLifecycleListener : AppLifecycleListener {

    override fun appClosing() {
      cleanupMtfPdf()
    }

    private fun cleanupMtfPdf() {
      val root = VfsUtil.findFile(Path.of("./"), true) as VirtualFile
      VfsUtil.processFileRecursivelyWithoutIgnored(root) {
        if (it.isDirectory) return@processFileRecursivelyWithoutIgnored true
        if (it.name.contains(MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX)) Files.deleteIfExists(it.toNioPath())
        return@processFileRecursivelyWithoutIgnored true
      }
    }
  }

  private inner class FileChangedListener : BulkFileListener {



    override fun after(events: MutableList<out VFileEvent>) {
      watchPath(settings.customMustacheFontsPath, events) { settings.notifyMustacheFontsPathSettingsListeners() }
      watchPath(TEMPLATES_PATH, events) {
        println("Target file ${it?.canonicalPath} changed. Reloading current view.")
        manageForFile(it)
//        project.messageBus.syncPublisher(MustacheRefreshPdfFileEditorTabs.TOPIC).refreshTabs(mustacheFileRoots)
      }
    }

    private fun manageForFile(file: VirtualFile?) {
      if(file == null) return
      if(!isFilePathUnderTemplatesPath(project, file)) {
        FileEditorManager.getInstance(project).closeFile(file)
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
      } else {

      }
//      val oldMustacheFileRoots = _mustacheIncludeProcessor.getRootsForMustache(file)
      _mustacheIncludeProcessor.processFileIncludePropsMap()
      val newMustacheFileRoots = _mustacheIncludeProcessor.getRootsForMustache(file)
      _mustacheIncludeProcessor.tryInvalidateRootPdfsForMustacheRoots(newMustacheFileRoots)
      project.messageBus.syncPublisher(MustacheUpdatePdfFileEditorTabs.TOPIC).updateTabs()
      project.messageBus.syncPublisher(MustacheToolWindowListener.TOPIC).refresh()
    }

    private fun watchPath(watchedPath: String, events: MutableList<out VFileEvent>, fileWatcher: FileWatcher) {
      var file: VirtualFile? = null
      val canonicalFilePath = Path.of(watchedPath).toCanonicalPath()

      fun checkFilePathWithWatchedPathBasedOnFileType(eventFilePath: String, isDirectory: Boolean): Boolean {
        val eventCanonicalFilePath = Path.of(eventFilePath).toCanonicalPath()
        if (isDirectory) return eventCanonicalFilePath == canonicalFilePath
        return eventCanonicalFilePath.startsWith("$canonicalFilePath/")
      }

      if (events.any {
          file = it.file ?: return
          val isDirectory = file!!.isDirectory
          if (it is VFileMoveEvent) {
            return@any (checkFilePathWithWatchedPathBasedOnFileType(it.oldPath, isDirectory)
              || checkFilePathWithWatchedPathBasedOnFileType(it.newPath, isDirectory))
          }
          if (it is VFilePropertyChangeEvent) {
            return@any (checkFilePathWithWatchedPathBasedOnFileType(it.oldPath, isDirectory)
              || checkFilePathWithWatchedPathBasedOnFileType(it.newPath, isDirectory))
          }
          if (it is VFileCopyEvent) {
            return@any checkFilePathWithWatchedPathBasedOnFileType(it.findCreatedFile()?.canonicalPath ?: "", isDirectory)
          }
          return@any checkFilePathWithWatchedPathBasedOnFileType(file!!.canonicalPath ?: "", isDirectory)
        }) {
        fileWatcher.run(file)
      }

//      if (events.any {
//        isFilePathUnderResourcesPathWithPrefix(project, it.file)
//      }) {
//        // TODO keep in mind operation concurrency?
//        // redoing mustache dependency tree
////        val mustacheFileRoots = _mustacheIncludeProcessor.getRootsForMustache(editorFile)
////        _mustacheIncludeProcessor.tryInvalidateRootPdfsForMustacheRoots(mustacheFileRoots)
////        _mustacheIncludeProcessor.processFileIncludePropsMap()
//      }
    }
  }

  private inner class MyFileEditorManagerListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      selectedEditor = if (event.newEditor is TextEditorWithPreview
        && (event.newEditor as TextEditorWithPreview).name == MustacheFileEditor.NAME
      ) event.newEditor as TextEditorWithPreview else null
      manageMustacheFileEditors()
      manageMustacheToolWindow()
    }

    private fun manageMustacheFileEditors() {

    }

    private fun manageMustacheToolWindow() {
      var root: String? = null
      if (selectedEditor != null) {
        if (!toolWindowInitialized) {
          toolWindowInitialized = true
          val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
          toolWindow?.setAvailable(true, null)
          toolWindow?.show()
        }
        root = (selectedEditor!!.previewEditor as MustachePdfFileEditorWrapper).activeTabRoot ?: return
        val selectedNodeName = getRelativeFilePathFromTemplatesPath(project, selectedEditor!!.file)
        ApplicationManager.getApplication().messageBus.syncPublisher(MustacheToolWindowListener.TOPIC)
          .rootChanged(root, selectedNodeName)
      }
      if (root == null && toolWindowInitialized) {
        toolWindowInitialized = false
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
        toolWindow?.setAvailable(false, null)
        toolWindow?.hide()
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

  companion object {

    enum class FileEvent {

    }

    fun interface FileWatcher {
      fun run(file: VirtualFile?, event: FileEvent)
    }
  }
}
