package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.structureView.PdfStructureViewBuilder
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import generate.MustacheIncludeProcessor.IncludeProps
import javax.swing.JComponent

// TODO: Implement state persistence
class PdfFileEditor(project: Project, private var virtualFile: VirtualFile) : FileEditorBase(), DumbAware {
  var fileIncludePropsEntry: Map.Entry<String, IncludeProps>? = null
  var viewComponent = PdfEditorViewComponent(project, virtualFile)
  private val messageBusConnection = project.messageBus.connect()
  private val fileChangedListener = FileChangedListener(PdfViewerSettings.instance.enableDocumentAutoReload)

  constructor(
    project: Project, virtualFile: VirtualFile, fileIncludePropsEntry: Map.Entry<String, IncludeProps>
  ) : this(project, virtualFile) {
    this.fileIncludePropsEntry = fileIncludePropsEntry
  }

  init {
    Disposer.register(this, viewComponent)
    Disposer.register(this, messageBusConnection)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener)
    messageBusConnection.subscribe(PdfViewerSettings.TOPIC, PdfViewerSettingsListener {
      fileChangedListener.isEnabled = it.enableDocumentAutoReload
    })
    // subscribes to changes directly from a mustache file to reload all previews that depend on it
    messageBusConnection.subscribe(MustacheFileEditor.MUSTACHE_FILE_LISTENER_TOPIC, MustacheFileEditor.MustacheFileListener {
      val source = mutableSetOf(it.key)
      source.addAll(it.value.roots)
      val target = mutableSetOf(fileIncludePropsEntry?.key)
      target.addAll(fileIncludePropsEntry?.value?.roots!!)

      // update fileIncludePropsEntry with maybe modified ones
      fileIncludePropsEntry = it

      // if source fileIncludePropsEntry with key + rootParents intersects this PdfFileEditor target fileIncludePropsEntry
      // then the mustache file that was modified impacted the pdf and it needs to be reloaded
      if (source.intersect(target).isEmpty()) return@MustacheFileListener

//        Optional.ofNullable(keyProcessedPdfFileMap.firstNotNullOf {
//          if (target.contains(it.key)) return@firstNotNullOf keyProcessedPdfFileMap[it.key]
//          return@firstNotNullOf null
//        }).ifPresentOrElse({
//          virtualFile = it
//          viewComponent = PdfEditorViewComponent(project, virtualFile)
//        }, { throw RuntimeException() })

      if (viewComponent.controller == null) {
        logger.warn("FileChangedListener was called for view with controller == null!")
      } else {
        logger.debug("Target file ${virtualFile.path} changed. Reloading current view.")
        viewComponent.controller?.reload(tryToPreserveState = true)
      }
    })
  }

  override fun getName(): String = NAME

  override fun getFile(): VirtualFile = virtualFile

  override fun getComponent(): JComponent = viewComponent

  override fun getPreferredFocusedComponent(): JComponent = viewComponent.controlPanel

  // only works for PDF files opened directly from the project
  private inner class FileChangedListener(var isEnabled: Boolean = true) : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
      if (!isEnabled) {
        return
      }
      if (viewComponent.controller == null) {
        logger.warn("FileChangedListener was called for view with controller == null!")
      } else if (events.any { it.file == virtualFile }) {
        logger.debug("Target file ${virtualFile.path} changed. Reloading current view.")
        viewComponent.controller?.reload(tryToPreserveState = true)
      }
    }
  }

  override fun getStructureViewBuilder(): StructureViewBuilder {
    return PdfStructureViewBuilder(this)
  }

  companion object {
    private const val NAME = "Pdf Viewer File Editor"
    private val logger = logger<PdfFileEditor>()
  }
}
