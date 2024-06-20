package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerMustacheFilePropsSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerMustacheFontsPathSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MUSTACHE_PREFIX
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.MUSTACHE_SUFFIX
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
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.openapi.wm.ToolWindowManager
import generate.MustacheIncludeProcessor
import generate.Utils.*
import io.sentry.util.Objects
import kotlinx.collections.immutable.toImmutableSet
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(private val project: Project) : MustacheContextService, Disposable {

  private val _mustacheIncludeProcessor = MustacheIncludeProcessor.getInstance(project)
  private val fileEditorManager = FileEditorManager.getInstance(project)
  private val settings = PdfViewerSettings.instance
  private val fileChangedListener = FileChangedListener()
  private val appLifecycleListener = MyAppLifecycleListener()
  private val messageBusConnection = project.messageBus.connect()
  private val fileEditorManagerListener = MyFileEditorManagerListener()
  private val mustacheFilePropsListener = MyPdfViewerMustacheFilePropsSettingsListener()

  init {
    Disposer.register(this, messageBusConnection)
    println("MustacheContextServiceImpl initialized for " + project.name)

    messageBusConnection.subscribe(AppLifecycleListener.TOPIC, appLifecycleListener)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_MUSTACHE_FILE_PROPS, mustacheFilePropsListener)
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorManagerListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_MUSTACHE_FONTS_PATH, PdfViewerMustacheFontsPathSettingsListener {
      val syncedTabbedRootNamesFromAllValidEditors =
        fileEditorManager.allEditors.asSequence().filter { it is TextEditorWithPreview && it.name == MustacheFileEditor.NAME }
          .map { ((it as TextEditorWithPreview).previewEditor as MustachePdfFileEditorWrapper).syncedTabbedEditors }.flatten()
          .map { it.rootName }.toSet()
      _mustacheIncludeProcessor.invalidateRootPdfsForMustacheRoots(syncedTabbedRootNamesFromAllValidEditors)
      syncedTabbedRootNamesFromAllValidEditors.forEach { _mustacheIncludeProcessor.processPdfFileForMustacheRoot(it) }
      project.messageBus.syncPublisher(MustacheRefreshPdfFileEditorTabs.TOPIC).refreshTabs(syncedTabbedRootNamesFromAllValidEditors)
    })
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
        manageMustacheEditors(file, event.type)
      }
    }

    private fun manageMustacheProcessingForFile(
      file: VirtualFile, event: WatcherFileEvent
    ) {
      _mustacheIncludeProcessor.processFileIncludePropsMap()
      invalidateRoots(file, event)
      project.messageBus.syncPublisher(MustacheUpdatePdfFileEditorTabs.TOPIC).updateTabs()
      project.messageBus.syncPublisher(MustacheToolWindowListener.TOPIC).refresh()
    }

    //TODO: optimize all the process chain in the future
    private fun invalidateRoots(
      file: VirtualFile, event: WatcherFileEvent
    ) {
      val needUpdateMustacheFileRoots = HashSet<String>()
      fun pushToNeedUpdate(f: VirtualFile, rootDir: VirtualFile? = null) {
        var canonicalFilePathForOldRoots = f.canonicalPath
        if (listOf(
            WatcherFileEvent.Type.MOVE_INSIDE_PATH,
            WatcherFileEvent.Type.MOVE_OUT,
            WatcherFileEvent.Type.CHANGE_NAME_PROPERTY
          ).contains(event.type)
        ) {
          val eOldPath =
            if (event.e is VFileMoveEvent) event.e.oldPath else if (event.e is VFilePropertyChangeEvent) event.e.oldPath else null
          if (eOldPath != null) {
            canonicalFilePathForOldRoots = if (rootDir != null) {
              val fCanonicalPath = f.canonicalPath
              val rootCanonicalPath = rootDir.canonicalPath
              Objects.requireNonNull(rootDir, "rootCanonicalPath must not be null")
              Objects.requireNonNull(fCanonicalPath, "canonicalPath of child file must not be null")
              val relativePathFromDirRootToFile = fCanonicalPath!!.substring(rootCanonicalPath!!.length + 1)
              Path.of(eOldPath).toCanonicalPath() + "/" + relativePathFromDirRootToFile
            } else {
              Path.of(eOldPath).toCanonicalPath()
            }
          }
        }
        val oldMustacheFileRoots = _mustacheIncludeProcessor.getOldRootsForMustache(canonicalFilePathForOldRoots)
        val updatedMustacheFileRoots = _mustacheIncludeProcessor.getRootsForMustache(f.canonicalPath)
        needUpdateMustacheFileRoots.addAll(oldMustacheFileRoots)
        needUpdateMustacheFileRoots.addAll(updatedMustacheFileRoots)
      }
      if (file.isDirectory) {
        VfsUtil.processFileRecursivelyWithoutIgnored(file) {
          if (it.isDirectory) return@processFileRecursivelyWithoutIgnored true
          pushToNeedUpdate(it, file)
          return@processFileRecursivelyWithoutIgnored true
        }
      } else {
        pushToNeedUpdate(file)
      }
      _mustacheIncludeProcessor.invalidateRootPdfsForMustacheRoots(needUpdateMustacheFileRoots)
    }

    private fun manageMustacheEditors(
      file: VirtualFile, watcherFileEventType: WatcherFileEvent.Type
    ) {
      if (!listOf(WatcherFileEvent.Type.MOVE_OUT, WatcherFileEvent.Type.MOVE_IN, WatcherFileEvent.Type.CHANGE_NAME_PROPERTY)
          .contains(watcherFileEventType)
      ) return

      val selectedEditorFile = fileEditorManager.selectedEditor?.file
      val switchEditorFiles = HashSet<VirtualFile>()
      fun pushToSwitchEditor(it: VirtualFile) {
        if (!fileEditorManager.isFileOpen(it)) return
        switchEditorFiles.add(it)
      }

      if (file.isDirectory) {
        VfsUtil.processFileRecursivelyWithoutIgnored(file) {
          if (it.isDirectory) return@processFileRecursivelyWithoutIgnored true
          pushToSwitchEditor(it)
          return@processFileRecursivelyWithoutIgnored true
        }
      } else {
        pushToSwitchEditor(file)
      }

      switchEditorFiles.forEach {
        fileEditorManager.closeFile(it)
        fileEditorManager.openTextEditor(
          OpenFileDescriptor(project, it),
          selectedEditorFile?.canonicalPath?.equals(it.canonicalPath) == true
        )
      }
      // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206122419-Opening-file-in-editor-without-moving-focus-to-it
      // a lil bit tricky, has a weird behaviour on tab selection? but it works
      if (selectedEditorFile != null) fileEditorManager.openTextEditor(OpenFileDescriptor(project, selectedEditorFile), true)
    }
  }

  private inner class MyFileEditorManagerListener : FileEditorManagerListener {

    private var toolWindowInitialized = false

    override fun selectionChanged(event: FileEditorManagerEvent) {
      val selectedEditor =
        if (event.newEditor is TextEditorWithPreview && (event.newEditor as TextEditorWithPreview).name == MustacheFileEditor.NAME) event.newEditor as TextEditorWithPreview else null
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
        val selectedNodeName = getRelativeMustacheFilePathFromTemplatesPath(project, selectedEditor.file?.canonicalPath)
        ApplicationManager.getApplication().messageBus.syncPublisher(MustacheToolWindowListener.TOPIC).rootChanged(root, selectedNodeName)
      }
      if (root == null && toolWindowInitialized) {
        toolWindowInitialized = false
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
        toolWindow?.setAvailable(false, null)
        toolWindow?.hide()
      }
    }
  }

  private inner class MyPdfViewerMustacheFilePropsSettingsListener : PdfViewerMustacheFilePropsSettingsListener {
    override fun filePropsChanged(settings: PdfViewerSettings) {
      // get editors for all files under old templates folder and under the new templates folder
      val beforeModMustacheEditorsFiles =
        fileEditorManager.allEditors.filter { isFilePathUnderTemplatesPath(project, it.file.canonicalPath) }.map { it.file }
          .toImmutableSet()
      MUSTACHE_PREFIX = PdfViewerSettings.instance.customMustachePrefix
      MUSTACHE_SUFFIX = PdfViewerSettings.instance.customMustacheSuffix
      val afterModMustacheEditorsFiles =
        fileEditorManager.allEditors.filter { isFilePathUnderTemplatesPath(project, it.file.canonicalPath) }.map { it.file }
          .toImmutableSet()
      val mustacheEditorsFiles = beforeModMustacheEditorsFiles.plus(afterModMustacheEditorsFiles)

      // if the editor array is empty then we have nothing to worry about
      if (mustacheEditorsFiles.isEmpty()) return

      // if we now have an editor open for the new templates folder then we can recalculate TEMPLATES_PATH for specialized mustache editors to open
      Optional.ofNullable(afterModMustacheEditorsFiles.firstOrNull())
        .ifPresentOrElse(
          { TEMPLATES_PATH = getTemplatesPath(project, it.canonicalPath) },
          {
            println("FilePropsChanged: Not editor open so the TEMPLATES_PATH haven't have to be modified right now. It would be updated on the next valid opened mustache.")
            TEMPLATES_PATH = null.toString()
          })

      // reprocess include dependency tree for mustache files under the new templates folder
      _mustacheIncludeProcessor.processFileIncludePropsMap()
      _mustacheIncludeProcessor.invalidateRootPdfs()

      // we save the selected editor's file for it to be reselected after editors switch
      val selectedEditorFile = fileEditorManager.selectedEditor?.file
      // we should close the editors and reopen them accordingly
      mustacheEditorsFiles.forEach {
        fileEditorManager.closeFile(it)
        fileEditorManager.openTextEditor(
          OpenFileDescriptor(project, it),
          selectedEditorFile?.canonicalPath?.equals(it.canonicalPath) == true
        )
      }
      // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206122419-Opening-file-in-editor-without-moving-focus-to-it
      // a lil bit tricky, has a weird behaviour on tab selection? but it works
      if (selectedEditorFile != null) fileEditorManager.openTextEditor(OpenFileDescriptor(project, selectedEditorFile), true)
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
        COPY, CREATE, DELETE, MOVE_IN, MOVE_OUT, MOVE_INSIDE_PATH, CHANGE_CONTENT, CHANGE_NAME_PROPERTY, CHANGE_OTHER_PROPERTY
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
          event = WatcherFileEvent(WatcherFileEvent.Type.CHANGE_OTHER_PROPERTY, it)
          val isDirectory = file!!.isDirectory
          if (it is VFileCopyEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.COPY, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(it.findCreatedFile()?.canonicalPath ?: "", isDirectory)
          }
          if (it is VFileCreateEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.CREATE, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(file!!.canonicalPath ?: "", isDirectory)
          }
          if (it is VFileDeleteEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.DELETE, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(file!!.canonicalPath ?: "", isDirectory)
          }
          if (it is VFileMoveEvent) {
            val oldPathCheck = checkFilePathWithWatchedPathBasedOnFileType(it.oldPath, isDirectory)
            val newPathCheck = checkFilePathWithWatchedPathBasedOnFileType(it.newPath, isDirectory)
            if (!oldPathCheck && newPathCheck) {
              event = WatcherFileEvent(WatcherFileEvent.Type.MOVE_IN, it)
            }
            if (oldPathCheck && !newPathCheck) {
              event = WatcherFileEvent(WatcherFileEvent.Type.MOVE_OUT, it)
            }
            if (oldPathCheck && newPathCheck) {
              event = WatcherFileEvent(WatcherFileEvent.Type.MOVE_INSIDE_PATH, it)
            }
            return@any (oldPathCheck || newPathCheck)
          }
          if (it is VFileContentChangeEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.CHANGE_CONTENT, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(file!!.canonicalPath ?: "", isDirectory)
          }
          if (it is VFilePropertyChangeEvent) {
            event = if (it.oldPath != it.newPath) {
              WatcherFileEvent(WatcherFileEvent.Type.CHANGE_NAME_PROPERTY, it)
            } else {
              WatcherFileEvent(WatcherFileEvent.Type.CHANGE_OTHER_PROPERTY, it)
            }
            return@any (checkFilePathWithWatchedPathBasedOnFileType(it.oldPath, isDirectory) || checkFilePathWithWatchedPathBasedOnFileType(
              it.newPath,
              isDirectory
            ))
          }
          return@any false
        }) {
        fileWatcher.run(file, event!!)
      }
    }
  }
}
