package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowFactory
import com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow.MustacheToolWindowListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerMustacheFilePropsSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerMustacheFontsPathSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheFileEditor
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustachePdfFileEditorWrapper
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheRefreshPdfFileEditorTabs
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.mustache.MustacheUpdatePdfFileEditorTabs
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.openapi.wm.ToolWindowManager
import generate.MustacheIncludeProcessor
import generate.Utils.*
import org.apache.commons.io.FileUtils
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class MustacheContextServiceImpl(private val project: Project) : MustacheContextService, Disposable {

  private val moduleMustacheContextCache = HashMap<String, MustacheContextInternal>() // <ModuleDirPath, MustacheContextInternal>
  private val messageBusConnection = project.messageBus.connect()

  /***
   * Listeners
   */
  private val fileChangedListener = FileChangedListener()
  private val appLifecycleListener = MyAppLifecycleListener()
  private val fileEditorManagerListener = MyFileEditorManagerListener()
  private val mustacheFilePropsListener = MyPdfViewerMustacheFilePropsSettingsListener()
  private val mustacheFontsPathListener = MyPdfViewerMustacheFontsPathSettingsListener()

  init {
    Disposer.register(this, messageBusConnection)
    logger.debug("MustacheContextServiceImpl initialized for project: " + project.name)

    messageBusConnection.subscribe(AppLifecycleListener.TOPIC, appLifecycleListener)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_MUSTACHE_FILE_PROPS, mustacheFilePropsListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC_MUSTACHE_FONTS_PATH, mustacheFontsPathListener)
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorManagerListener)
  }

  private inner class MyAppLifecycleListener : AppLifecycleListener {
    override fun appClosing() {
      cleanupMtdPdf()
    }

    private fun cleanupMtdPdf() {
      FileUtils.deleteDirectory(Path.of(MUSTACHE_TEMPORARY_DIRECTORY).toFile())
    }
  }

  private inner class FileChangedListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      watchPath(
        PdfViewerSettings.instance.customMustacheFontsPath, events
      ) { _, _ -> PdfViewerSettings.instance.notifyMustacheFontsPathSettingsListeners() }
      moduleMustacheContextCache.values.forEach {
        watchPath(
          it.templatesDir.path, events
        ) { file, event ->
          logger.debug("Target file ${file?.path} changed. Reloading current view.")
          file ?: return@watchPath
          manageMustacheProcessingForFile(file, event, it.mustacheIncludeProcessor)
          manageMustacheEditors(file, event.type)
        }
      }
    }

    private fun manageMustacheProcessingForFile(
      file: VirtualFile, event: WatcherFileEvent, mustacheIncludeProcessor: MustacheIncludeProcessor
    ) {
      mustacheIncludeProcessor.processFileIncludePropsMap()
      invalidateRoots(file, event, mustacheIncludeProcessor)
      project.messageBus.syncPublisher(MustacheUpdatePdfFileEditorTabs.TOPIC).updateTabs()
      project.messageBus.syncPublisher(MustacheToolWindowListener.TOPIC).refresh()
    }

    //TODO: optimize all the process chain in the future
    private fun invalidateRoots(
      file: VirtualFile, event: WatcherFileEvent, mustacheIncludeProcessor: MustacheIncludeProcessor
    ) {
      val needUpdateMustacheFileRoots = HashSet<String>()
      fun pushToNeedUpdate(f: VirtualFile, rootDir: VirtualFile? = null) {
        var oldRootsFilePath = f.path
        if (listOf(
            WatcherFileEvent.Type.MOVE_INSIDE_PATH,
            WatcherFileEvent.Type.MOVE_OUT,
            WatcherFileEvent.Type.CHANGE_NAME_PROPERTY
          ).contains(event.type)
        ) {
          val eOldPath =
            if (event.e is VFileMoveEvent) event.e.oldPath else if (event.e is VFilePropertyChangeEvent) event.e.oldPath else null
          if (eOldPath != null) {
            oldRootsFilePath = if (rootDir != null) {
              val relativePathFromDirRootToFile = VfsUtil.getRelativePath(f, rootDir)
              "$eOldPath/$relativePathFromDirRootToFile"
            } else {
              eOldPath
            }
          }
        }
        val oldMustacheFileRoots = mustacheIncludeProcessor.getOldRootsForMustache(oldRootsFilePath)
        val updatedMustacheFileRoots = mustacheIncludeProcessor.getRootsForMustache(f.path)
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
      mustacheIncludeProcessor.invalidateRootPdfsForMustacheRoots(needUpdateMustacheFileRoots)
    }

    private fun manageMustacheEditors(
      file: VirtualFile, watcherFileEventType: WatcherFileEvent.Type
    ) {
      if (!listOf(WatcherFileEvent.Type.MOVE_OUT, WatcherFileEvent.Type.MOVE_IN, WatcherFileEvent.Type.CHANGE_NAME_PROPERTY)
          .contains(watcherFileEventType)
      ) return

      val selectedEditorFile = FileEditorManager.getInstance(project).selectedEditor?.file
      val switchEditorFiles = HashSet<VirtualFile>()
      fun pushToSwitchEditor(it: VirtualFile) {
        if (!FileEditorManager.getInstance(project).isFileOpen(it)) return
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
        FileEditorManager.getInstance(project).closeFile(it)
        FileEditorManager.getInstance(project).openTextEditor(
          OpenFileDescriptor(project, it),
          selectedEditorFile?.path.equals(it.path)
        )
      }
      // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206122419-Opening-file-in-editor-without-moving-focus-to-it
      // a lil bit tricky, has a weird behaviour on tab selection? but it works
      if (selectedEditorFile != null) FileEditorManager.getInstance(project)
        .openTextEditor(OpenFileDescriptor(project, selectedEditorFile), true)
    }
  }

  private inner class MyFileEditorManagerListener : FileEditorManagerListener {

    private var toolWindowInitialized = false

    override fun selectionChanged(event: FileEditorManagerEvent) {
      super.selectionChanged(event)
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
        ApplicationManager.getApplication().messageBus.syncPublisher(MustacheToolWindowListener.TOPIC)
          .rootChanged(root, getContext(selectedEditor.file!!))
      }
      if (root == null && toolWindowInitialized) {
        toolWindowInitialized = false
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MustacheToolWindowFactory.NAME)
        toolWindow?.setAvailable(false, null)
        toolWindow?.hide()
      }
    }
  }

  private inner class MyPdfViewerMustacheFontsPathSettingsListener : PdfViewerMustacheFontsPathSettingsListener {
    override fun fontsPathChanged(settings: PdfViewerSettings) {
      FileEditorManager.getInstance(project).allEditors.asSequence()
        .filter { it is TextEditorWithPreview && it.name == MustacheFileEditor.NAME }
        .map { ((it as TextEditorWithPreview).previewEditor as MustachePdfFileEditorWrapper).syncedTabbedEditors }.flatten()
        .groupBy({
          getModulePath(it.file)
        }, {
          it.rootName
        })
        .forEach { (modulePath, rootNames) ->
          val mustacheContext =
            moduleMustacheContextCache[modulePath] ?: throw RuntimeException("MustacheContext not found for roots: $rootNames")
          val rootNamesSet = rootNames.toSet()
          val mustacheIncludeProcessor = mustacheContext.mustacheIncludeProcessor
          mustacheIncludeProcessor.invalidateRootPdfsForMustacheRoots(rootNamesSet)
          rootNamesSet.forEach { mustacheIncludeProcessor.processPdfFileForMustacheRoot(it) }
          project.messageBus.syncPublisher(MustacheRefreshPdfFileEditorTabs.TOPIC).refreshTabs(rootNamesSet)
        }
    }
  }

  private inner class MyPdfViewerMustacheFilePropsSettingsListener : PdfViewerMustacheFilePropsSettingsListener {
    override fun filePropsChanged(settings: PdfViewerSettings) {
      // get editors for all files under old templates folder and under the new templates folder
//      val beforeModMustacheEditorsFiles =
//        FileEditorManager.getInstance(project).allEditors.filter { isFilePathUnderTemplatesPath(it.file.absolutePath, _templatesPath) }
//          .map { it.file }
//          .toImmutableSet()
//      val mustacheEditorsFiles =
//        FileEditorManager.getInstance(project).allEditors.filter { isFilePathUnderTemplatesPath(it.file.absolutePath, _templatesPath) }
//          .map { it.file }
//          .toImmutableSet()
//      val mustacheEditorsFiles = beforeModMustacheEditorsFiles.plus(afterModMustacheEditorsFiles)
//
//      // if the editor array is empty then we have nothing to worry about
//      if (mustacheEditorsFiles.isEmpty()) return
//
//      // if we now have an editor open for the new templates folder then we can recalculate TEMPLATES_PATH for specialized mustache editors to open
//      Optional.ofNullable(afterModMustacheEditorsFiles.firstOrNull())
//        .ifPresentOrElse(
//          { _templatesPath = getTemplatesPath(modulePath, PdfViewerSettings.instance.customMustachePrefix) },
//          {
//            println("FilePropsChanged: Not editor open so the TEMPLATES_PATH haven't have to be modified right now. It would be updated on the next valid opened mustache.")
//            _templatesPath = null.toString()
//          })
//
//      // reprocess include dependency tree for mustache files under the new templates folder
//      _mustacheIncludeProcessor.processFileIncludePropsMap()
//      _mustacheIncludeProcessor.invalidateRootPdfs()
//
//      // we save the selected editor's file for it to be reselected after editors switch
//      val selectedEditorFile = FileEditorManager.getInstance(project).selectedEditor?.file
//      // we should close the editors and reopen them accordingly
//      mustacheEditorsFiles.forEach {
//        FileEditorManager.getInstance(project).closeFile(it)
//        FileEditorManager.getInstance(project).openTextEditor(
//          OpenFileDescriptor(project, it),
//          selectedEditorFile?.absolutePath?.equals(it.absolutePath) == true
//        )
//      }
//      // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206122419-Opening-file-in-editor-without-moving-focus-to-it
//      // a lil bit tricky, has a weird behaviour on tab selection? but it works
//      if (selectedEditorFile != null) FileEditorManager.getInstance(project)
//        .openTextEditor(OpenFileDescriptor(project, selectedEditorFile), true)
    }
  }

  private fun getModulePath(file: VirtualFile): String {
    val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file)
    val moduleDir =
      module?.guessModuleDir() ?: throw RuntimeException("Could not guess module dir for file: " + file.path)
    return moduleDir.path
  }

  override fun getContext(file: VirtualFile): MustacheContext {
    if (file.extension != PdfViewerSettings.instance.customMustacheSuffix) throw RuntimeException("File does not have a valid mustache extension")

    val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file)
      ?: throw RuntimeException("Could not get module for file ${file.path}")
    val moduleDir = module.guessModuleDir()
      ?: throw RuntimeException("Could not get moduleDir for file ${file.path}")

    val templatesDir = getTemplatesDir(moduleDir, PdfViewerSettings.instance.customMustachePrefix)
    if(!VfsUtil.isUnder(file, setOf(templatesDir))) {
      throw RuntimeException(
        "File is not under templates folder [templatesPath, filePath] [${templatesDir.path}, ${file.path}]"
      )
    }

    val modulePath = moduleDir.path

    val internal = if (moduleMustacheContextCache[modulePath] == null) {
      val context = MustacheContextInternal(
        MustacheIncludeProcessor.getInstance(templatesDir.path, PdfViewerSettings.instance.customMustacheSuffix, module.name),
        templatesDir,
        PdfViewerSettings.instance.customMustachePrefix,
        PdfViewerSettings.instance.customMustacheSuffix
      )
      moduleMustacheContextCache[modulePath] = context
      logger.debug("Initializing and caching mustache context for module... " + module.name)
      context
    } else {
      logger.debug("Retrieving from cache mustache context for module... " + module.name)
      moduleMustacheContextCache[modulePath]!!
    }

    return MustacheContext(
      internal,
      getRelativeMustacheFilePathFromTemplatesPath(file.path, templatesDir.path, PdfViewerSettings.instance.customMustacheSuffix)
    )
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    Disposer.dispose(messageBusConnection)
  }

  companion object {

    private val logger = logger<MustacheContextServiceImpl>()

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

      fun checkFilePathWithWatchedPathBasedOnFileType(eventFilePath: String?, isDirectory: Boolean): Boolean {
        eventFilePath ?: return false
        if (isDirectory) return eventFilePath == watchedPath || eventFilePath.startsWith("$watchedPath/")
        return eventFilePath.startsWith("$watchedPath/")
      }

      if (events.any {
          file = it.file ?: return
          event = WatcherFileEvent(WatcherFileEvent.Type.CHANGE_OTHER_PROPERTY, it)
          val isDirectory = file!!.isDirectory
          if (it is VFileCopyEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.COPY, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(
              it.findCreatedFile()?.path,
              isDirectory
            )
          }
          if (it is VFileCreateEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.CREATE, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(file?.path, isDirectory)
          }
          if (it is VFileDeleteEvent) {
            event = WatcherFileEvent(WatcherFileEvent.Type.DELETE, it)
            return@any checkFilePathWithWatchedPathBasedOnFileType(file?.path, isDirectory)
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
            return@any checkFilePathWithWatchedPathBasedOnFileType(file?.path, isDirectory)
          }
          if (it is VFilePropertyChangeEvent) {
            event = if (it.oldPath != it.newPath) {
              WatcherFileEvent(WatcherFileEvent.Type.CHANGE_NAME_PROPERTY, it)
            } else {
              WatcherFileEvent(WatcherFileEvent.Type.CHANGE_OTHER_PROPERTY, it)
            }
            return@any (checkFilePathWithWatchedPathBasedOnFileType(it.oldPath, isDirectory)
              || checkFilePathWithWatchedPathBasedOnFileType(it.newPath, isDirectory))
          }
          return@any false
        }) {
        fileWatcher.run(file, event!!)
      }
    }
  }

  data class MustacheContextInternal(
    val mustacheIncludeProcessor: MustacheIncludeProcessor,
    val templatesDir: VirtualFile,
    val mustachePrefix: String,
    val mustacheSuffix: String
  )
}

data class MustacheContext(
  val mustacheIncludeProcessor: MustacheIncludeProcessor,
  val templatesDir: VirtualFile,
  val mustachePrefix: String,
  val mustacheSuffix: String,
  val relativeFilePath: String
) {
  constructor(
    internal: MustacheContextServiceImpl.MustacheContextInternal,
    relativeFilePath: String
  ) : this(internal.mustacheIncludeProcessor, internal.templatesDir, internal.mustachePrefix, internal.mustacheSuffix, relativeFilePath)
}
