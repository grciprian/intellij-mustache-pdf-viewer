package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view

import com.firsttimeinforever.intellij.pdf.viewer.mpi.model.ViewTheme
import com.firsttimeinforever.intellij.pdf.viewer.mpi.model.ViewThemeUtils.create
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettingsListener
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.controls.PdfEditorControlPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.BoxLayout
import javax.swing.JPanel

class PdfEditorViewComponent(project: Project, virtualFile: VirtualFile) : JPanel(), Disposable {
  val controlPanel = PdfEditorControlPanel(project)
  val controller = PdfPreviewControllerProvider.createViewController(project, virtualFile)

  init {
    Disposer.register(this, controlPanel)
    if (controller != null) {
      Disposer.register(this, controller)
    } else {
      logger.warn("View controller is null!")
    }
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(controlPanel)
    add(controller?.component ?: PdfUnsupportedViewPanel())
  }

  override fun dispose() = Unit

  companion object {
    private val logger = logger<PdfEditorViewComponent>()
  }
}