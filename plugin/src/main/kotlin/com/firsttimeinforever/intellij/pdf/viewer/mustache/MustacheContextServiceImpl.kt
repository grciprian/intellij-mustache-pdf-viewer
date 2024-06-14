package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MUSTACHE_SUFFIX
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.TEMPLATES_PATH
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustachePdfFileEditorWrapper
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
import generate.Utils.MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX
import generate.Utils.getRelativeFilePathFromTemplatesPath
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(private val project: Project) : MustacheContextService, Disposable {

  private val settings = PdfViewerSettings.instance
  private val fileChangedListener = FileChangedListener()
  private val appLifecycleListener = MyAppLifecycleListener()
  private val messageBusConnection = project.messageBus.connect()
  private val fileEditorManagerListener = MyFileEditorManagerListener()
  private val fileEditorManager = FileEditorManager.getInstance(project)
  private val _mustacheIncludeProcessor = MustacheIncludeProcessor.getInstance(project)

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
        if (it.name.endsWith(MUSTACHE_TEMPORARY_FILE_PDF_SUFFIX)) Files.deleteIfExists(it.toNioPath())
        return@processFileRecursivelyWithoutIgnored true
      }
    }
  }

  private inner class FileChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      watchPath(
        settings.customMustacheFontsPath, events
      ) { _, _ -> settings.notifyMustacheFontsPathSettingsListeners() }
      watchPath(
        TEMPLATES_PATH, events
      ) { file, event ->
        println("Target file ${file?.canonicalPath} changed. Reloading current view.")
        file ?: return@watchPath
        manageMustacheProcessingForFile(file, event)
        manageEditorForFile(file, event.type)
      }
    }

    private fun manageMustacheProcessingForFile(
      file: VirtualFile,
      event: WatcherFileEvent
    ) {
      _mustacheIncludeProcessor.processFileIncludePropsMap()
      invalidateRoots(file, event)
      project.messageBus.syncPublisher(MustacheUpdatePdfFileEditorTabs.TOPIC).updateTabs()
      project.messageBus.syncPublisher(MustacheToolWindowListener.TOPIC).refresh()
    }

    //TODO: optimize all the process chain in the future
    private fun invalidateRoots(
      file: VirtualFile,
      event: WatcherFileEvent
    ) {
      val needUpdateMustacheFileRoots = HashSet<String>()
      fun pushToNeedUpdate(f: VirtualFile) {
        var canonicalFilePathForOldRoots = f.canonicalPath
        if (event.e is VFileMoveEvent) canonicalFilePathForOldRoots = Path.of(event.e.oldPath).toCanonicalPath()
        val oldMustacheFileRoots = _mustacheIncludeProcessor.getOldRootsForMustache(canonicalFilePathForOldRoots)
        val updatedMustacheFileRoots = _mustacheIncludeProcessor.getRootsForMustache(f.canonicalPath)
        needUpdateMustacheFileRoots.addAll(oldMustacheFileRoots)
        needUpdateMustacheFileRoots.addAll(updatedMustacheFileRoots)
      }
      if (file.isDirectory) {
        VfsUtil.processFileRecursivelyWithoutIgnored(file) {
          if (it.isDirectory) return@processFileRecursivelyWithoutIgnored true
          pushToNeedUpdate(it)
          return@processFileRecursivelyWithoutIgnored true
        }
      } else {
        pushToNeedUpdate(file)
      }
      _mustacheIncludeProcessor.tryInvalidateRootPdfsForMustacheRoots(needUpdateMustacheFileRoots)
    }

    private fun manageEditorForFile(
      file: VirtualFile,
      watcherFileEventType: WatcherFileEvent.Type
    ) {
      if (fileEditorManager.isFileOpen(file)
        && listOf(WatcherFileEvent.Type.MOVE_OUT, WatcherFileEvent.Type.MOVE_IN).contains(watcherFileEventType)
      ) {
        val selectedEditorFile = fileEditorManager.selectedEditor?.file
        val focusEditorForMovedFile = file.extension == MUSTACHE_SUFFIX
          && selectedEditorFile?.canonicalPath?.equals(file.canonicalPath) == true
        fileEditorManager.closeFile(file)
        //https://intellij-support.jetbrains.com/hc/en-us/community/posts/206122419-Opening-file-in-editor-without-moving-focus-to-it
        //a lil bit tricky, has a weird behaviour on tab selection but it works
        fileEditorManager.openTextEditor(OpenFileDescriptor(project, file), focusEditorForMovedFile)
        if (selectedEditorFile != null && !focusEditorForMovedFile) {
          fileEditorManager.openTextEditor(OpenFileDescriptor(project, selectedEditorFile), true)
        }
      }
    }
  }

  private inner class MyFileEditorManagerListener : FileEditorManagerListener {

    private var toolWindowInitialized = false

    override fun selectionChanged(event: FileEditorManagerEvent) {
      val selectedEditor = if (event.newEditor is TextEditorWithPreview
        && (event.newEditor as TextEditorWithPreview).name == MustacheFileEditor.NAME
      ) event.newEditor as TextEditorWithPreview else null
      manageMustacheFileEditors(selectedEditor)
      manageMustacheToolWindow(selectedEditor)
    }

    private fun manageMustacheFileEditors(selectedEditor: TextEditorWithPreview?) {
      //TODO: poate un mecanism prin care tabul selectat al unui editor selectat sa faca refresh doar in cazul in care este selectat
    }

    private fun manageMustacheToolWindow(selectedEditor: TextEditorWithPreview?) {
      var root: String? = null
      if (selectedEditor != null) {
        if (!toolWindowInitialized) {
          toolWindowInitialized = true
          val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
          toolWindow?.setAvailable(true, null)
          toolWindow?.show()
        }
        root = (selectedEditor.previewEditor as MustachePdfFileEditorWrapper).activeTabRoot ?: return
        val selectedNodeName = getRelativeFilePathFromTemplatesPath(project, selectedEditor.file?.canonicalPath)
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

    private class WatcherFileEvent(val type: Type, val e: VFileEvent) {
      enum class Type {
        MOVE_IN,
        MOVE_OUT,
        CHANGE_INSIDE
      }
    }

    private fun interface FileWatcher {
      fun run(file: VirtualFile?, event: WatcherFileEvent)
    }

    private fun watchPath(watchedPath: String, events: MutableList<out VFileEvent>, fileWatcher: FileWatcher) {

      var file: VirtualFile? = null
      var event: WatcherFileEvent? = null
      val canonicalFilePath = Path.of(watchedPath).toCanonicalPath()

      fun checkFilePathWithWatchedPathBasedOnFileType(eventFilePath: String, isDirectory: Boolean): Boolean {
        val eventCanonicalFilePath = Path.of(eventFilePath).toCanonicalPath()
        if (isDirectory) return eventCanonicalFilePath == canonicalFilePath || eventCanonicalFilePath.startsWith("$canonicalFilePath/")
        return eventCanonicalFilePath.startsWith("$canonicalFilePath/")
      }

      if (events.any {
          file = it.file ?: return
          event = WatcherFileEvent(WatcherFileEvent.Type.CHANGE_INSIDE, it)
          val isDirectory = file!!.isDirectory
          if (it is VFileMoveEvent) {
            val oldPathCheck = checkFilePathWithWatchedPathBasedOnFileType(it.oldPath, isDirectory)
            val newPathCheck = checkFilePathWithWatchedPathBasedOnFileType(it.newPath, isDirectory)
            if (oldPathCheck && !newPathCheck) {
              event = WatcherFileEvent(WatcherFileEvent.Type.MOVE_OUT, it)
            }
            if (!oldPathCheck && newPathCheck) {
              event = WatcherFileEvent(WatcherFileEvent.Type.MOVE_IN, it)
            }
            return@any (oldPathCheck || newPathCheck)
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
        fileWatcher.run(file, event!!)
      }
    }
  }
}
