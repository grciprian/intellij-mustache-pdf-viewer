package com.firsttimeinforever.intellij.pdf.viewer.settings

import com.firsttimeinforever.intellij.pdf.viewer.PdfViewerBundle
import com.firsttimeinforever.intellij.pdf.viewer.model.SidebarViewMode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.AnActionButton
import com.intellij.ui.ColorPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.TableView
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.table.TableModelEditor
import exceptions.FileNotFoundException
import exceptions.ModuleNotFoundException
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nullable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import kotlin.collections.toList

class PdfViewerSettingsForm(val project: Project) : JPanel() {
  private val settings
    get() = PdfViewerSettings.instance

  private val properties = PropertyGraph()

  val enableDocumentAutoReload = properties.property(settings.enableDocumentAutoReload)
  val defaultSidebarViewMode = properties.property(settings.defaultSidebarViewMode)

  private val generalSettingsGroup = panel {
    group(PdfViewerBundle.message("pdf.viewer.settings.group.general")) {
      row {
        checkBox(PdfViewerBundle.message("pdf.viewer.settings.reload.document"))
          .bindSelected(enableDocumentAutoReload)
      }
      row(PdfViewerBundle.message("pdf.viewer.settings.sidebar.viewer.default")) {
        val renderer = SimpleListCellRenderer.create<SidebarViewMode> { label, value, _ ->
          label.text = when (value) {
            SidebarViewMode.NONE -> "Closed"
            SidebarViewMode.THUMBNAILS -> "Thumbnails"
            // SidebarViewMode.OUTLINE -> "Outline (document structure)"
            SidebarViewMode.ATTACHMENTS -> "Attachments"
            else -> "Outline (document structure)"
          }
        }
        comboBox(DefaultComboBoxModel(SidebarViewMode.entries.toTypedArray()), renderer)
          .bindItem(defaultSidebarViewMode)
      }
    }
  }

  val isVerticalSplit = properties.property(settings.isVerticalSplit)
  val moduleContexts = properties.property(settings.moduleContexts).apply {
    afterPropagation {
      if (moduleMustacheContextTable.items != this.get()) {
        loadScheme()
      }
    }
  }
  private lateinit var moduleMustacheContextTable: TableView<ModuleMustacheContext>

  private fun loadScheme() {
    // make sure to make a copy so changes to the elements are transferred explicitly
    val items = moduleContexts.get().map { l -> l.copy() }
    val model =
      ListTableModel(arrayOf(moduleColumnInfo, templateDirectoryColumnInfo, suffixColumnInfo, fontsDirectoryColumnInfo), items).apply {
        this.addTableModelListener(object : TableModelListener {
          override fun tableChanged(e: TableModelEvent?) {
            if (e?.type == TableModelEvent.UPDATE) {
              moduleContexts.set((e.source as ListTableModel<ModuleMustacheContext>).items.toList())
            }
          }
        })
      }
    moduleMustacheContextTable.model = model
  }

  @Nullable
  private fun checkValidDirectoryUnderProject(text: String?, textField: JTextField?, allowEmpty: Boolean?): ValidationInfo? {
    if (text == null || text.isBlank()) {
      return if (allowEmpty == false) ValidationInfo("Empty templates path", textField) else null
    }

    try {
      val path = Paths.get(text)
      if (!path.toFile().exists()) {
        return ValidationInfo("The directory path does not exist", textField)
      } else if (!path.toFile().isDirectory()) {
        return ValidationInfo("Path is not a directory", textField)
      }
    } catch (e: Exception) {
      when (e) {
        is InvalidPathException, is IOException -> {
          return ValidationInfo("Invalid directory path", textField)
        }

        else -> throw e
      }
    }
    return null
  }

  private fun validateModuleAwareBrowserField(
    text: String?,
    textField: JTextField?,
    allowEmpty: Boolean?,
    bypassOnePerModuleValidation: Boolean? = true,
    guessedBrowsedFieldModule: ((module: Module) -> Unit)?
  ): ValidationInfo? {
    if (allowEmpty == true && (text == null || text.isBlank())) return null
    val dirCheck = checkValidDirectoryUnderProject(text, textField, allowEmpty)
    if (dirCheck != null) return dirCheck
    val browsedFile =
      VfsUtil.findFile(Paths.get(text!!), true) ?: throw FileNotFoundException("File not found with path $text")
    val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(browsedFile) ?: return ValidationInfo(
      "Could not get the module for the chosen path. Maybe the path is not under the open project?",
      textField
    )
    guessedBrowsedFieldModule?.invoke(module)
    val moduleDir = module.guessModuleDir()
      ?: throw ModuleNotFoundException("Could not get moduleDir")
    if (bypassOnePerModuleValidation == false && moduleContexts.get().find { it.modulePath == moduleDir.path } != null) {
      return ValidationInfo("Chosen path belongs to an already set up module", textField)
    }
    return null
  }

  private fun validateModuleAwareBrowserField(
    text: String?,
    textField: JTextField?,
    allowEmpty: Boolean?,
    bypassOnePerModuleValidation: Boolean? = true,
  ): ValidationInfo? = validateModuleAwareBrowserField(text, textField, allowEmpty, bypassOnePerModuleValidation, null)

  private fun isValidSuffix(text: String?, textField: JTextField?, allowEmpty: Boolean? = false): ValidationInfo? {
    if (text == null || text.isBlank()) {
      return if (allowEmpty == false) ValidationInfo("Empty suffix", textField) else null
    }
    val regex = Regex("""\w*""")
    if (!regex.matches(text)) {
      return ValidationInfo("Not a valid suffix", textField)
    }
    return null
  }

  /**
   * In order to make it easier for the user to enter new values, we use a dialog before adding the entry to the list (might be out of view if the list is long).
   */
  private fun createAddMustacheContextDialog() {
    val dialog = DialogBuilder()

    val moduleReference = AtomicReference<Module>(null)
    val templateDirField = textFieldWithBrowseButton(project, "Select Path", FileChooserDescriptorFactory.createSingleFolderDescriptor())
      .apply {
        preferredSize = Dimension(200, preferredSize.height)
      }
    val suffixField = JBTextField().apply { preferredSize = Dimension(200, preferredSize.height) }
    val fontsDirField =
      textFieldWithBrowseButton(project, "Select Path", FileChooserDescriptorFactory.createSingleFolderDescriptor()).apply {
        preferredSize = Dimension(200, preferredSize.height)
      }

    dialog.apply {
      setCenterPanel(
        panel {
          row("Templates Directory:") {
            cell(templateDirField).validationOnApply {
              validateModuleAwareBrowserField(it.text, it.textField, false, false) { module ->
                module.let { moduleReference.set(it) }
              }
            }
          }
          row("Suffix:") {
            cell(suffixField).validationOnApply {
              isValidSuffix(it.text, it)
            }
          }
          row("Fonts Directory(optional):") {
            cell(fontsDirField).validationOnApply {
              validateModuleAwareBrowserField(it.text, it.textField, true)
            }
          }
        }
      )

      addOkAction()
      addCancelAction()
      title("Add Mustache Module Context")

      if (show() == DialogWrapper.OK_EXIT_CODE) {
        val module = moduleReference.get()
        val moduleDir = module.guessModuleDir()
          ?: throw ModuleNotFoundException("Could not get moduleDir")
        val moduleMustacheContext = ModuleMustacheContext(
          moduleName = module.name,
          modulePath = moduleDir.path,
          templatesDir = Path.of(templateDirField.text.trim()).systemIndependentPath,
          suffix = suffixField.text.trim(),
          fontsDir = Path.of(fontsDirField.text.trim()).systemIndependentPath,
        )
        moduleContexts.set(moduleContexts.get().toMutableList().apply { add(moduleMustacheContext) })
        moduleMustacheContextTable.setRowSelectionInterval(
          moduleMustacheContextTable.rowCount - 1,
          moduleMustacheContextTable.rowCount - 1
        )
      }
    }
  }

  private val moduleColumnInfo =
    object : ColumnInfo<ModuleMustacheContext, String>("Module") {
      override fun valueOf(item: ModuleMustacheContext): String = item.moduleName
      override fun getTooltipText(): String = "The module of the mustache templates directory"
    }

  private class CustomTableCellEditor(val textFieldWithBrowseButton: TextFieldWithBrowseButton) : AbstractTableCellEditor() {
    init {
      textFieldWithBrowseButton.apply {
        textField.addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) {
              e.consume()
              stopCellEditing()
            }
          }
        })
      }
    }

    override fun getCellEditorValue() = textFieldWithBrowseButton.text
    override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int) =
      textFieldWithBrowseButton.apply {
        text = value as? String ?: ""
      }
  }

  private val templateDirectoryColumnInfo =
    object : TableModelEditor.EditableColumnInfo<ModuleMustacheContext, String>("Templates Directory") {
      private val textFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
          "Select Path",
          "Choose the directory for the mustache templates.",
          project,
          FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
      }

      override fun valueOf(item: ModuleMustacheContext): String = item.templatesDir
      override fun getTooltipText(): String = "Browse to select a path for the mustache templates directory"
      override fun getColumnClass(): Class<*> = TextFieldWithBrowseButton::class.java
      override fun setValue(item: ModuleMustacheContext, value: String?) = item.run {
        val validationInfo = validateModuleAwareBrowserField(value, textFieldWithBrowseButton.textField, false)
        if (validationInfo != null) {
          Messages.showErrorDialog(
            textFieldWithBrowseButton,
            validationInfo.message,
            "Validation Error"
          )
        } else {
          item.templatesDir = value?.let { Path.of(it.trim()).systemIndependentPath } ?: ""
        }
      }

      override fun getEditor(row: ModuleMustacheContext): TableCellEditor {
        return CustomTableCellEditor(textFieldWithBrowseButton)
      }
    }

  private val suffixColumnInfo = object : TableModelEditor.EditableColumnInfo<ModuleMustacheContext, String>("Suffix") {
    override fun valueOf(item: ModuleMustacheContext): String = item.suffix
    override fun getTooltipText(): String = "The suffix for the mustache templates"
    override fun getColumnClass(): Class<*> = String::class.java
    override fun setValue(item: ModuleMustacheContext, value: String?) {
      val validationInfo = isValidSuffix(value, null)
      if (validationInfo != null) {
        Messages.showErrorDialog(
          validationInfo.message,
          "Validation Error"
        )
      } else {
        item.suffix = value?.trim() ?: ""
      }
    }
  }

  private val fontsDirectoryColumnInfo =
    object : TableModelEditor.EditableColumnInfo<ModuleMustacheContext, String>("Fonts Directory") {
      private val textFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
          "Select Path",
          "Choose the directory for the mustache fonts.",
          project,
          FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
      }

      override fun valueOf(item: ModuleMustacheContext): String = item.fontsDir
      override fun getTooltipText(): String = "Browse to select a path for the mustache fonts directory"
      override fun getColumnClass(): Class<*> = String::class.java
      override fun setValue(item: ModuleMustacheContext, value: String?) = item.run {
        val validationInfo = validateModuleAwareBrowserField(value, textFieldWithBrowseButton.textField, true)
        if (validationInfo != null) {
          Messages.showErrorDialog(
            textFieldWithBrowseButton,
            validationInfo.message,
            "Validation Error"
          )
        } else {
          item.fontsDir = value?.let { Path.of(it.trim()).systemIndependentPath } ?: ""
        }
      }

      override fun getEditor(row: ModuleMustacheContext): TableCellEditor {
        return CustomTableCellEditor(textFieldWithBrowseButton)
      }
    }

  private fun createModuleMustacheContextTable(): TableView<ModuleMustacheContext> {
    val model =
      ListTableModel<ModuleMustacheContext>(moduleColumnInfo, templateDirectoryColumnInfo, suffixColumnInfo, fontsDirectoryColumnInfo)
    return TableView(model).apply {
      val moduleColumn = columnModel.getColumn(0)
      moduleColumn.cellRenderer = object : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
          table: JTable,
          value: Any?,
          isSelected: Boolean,
          hasFocus: Boolean,
          row: Int,
          column: Int
        ): Component {
          val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
          val context = (table.model as ListTableModel<ModuleMustacheContext>).getRowValue(row) as ModuleMustacheContext
          toolTipText = context.modulePath
          return component
        }
      }
    }.also { repaint() }
  }

  /**
   * This creates that header you see at the top of the table with the plus minus, etc
   */
  private fun createMappingsTableDecorator(): JComponent {
    val panelForTable = ToolbarDecorator.createDecorator(moduleMustacheContextTable, null)
      .setAddActionUpdater { _: AnActionEvent? -> true }
      .setAddAction { _: AnActionButton? ->
        createAddMustacheContextDialog()
      }
      .setRemoveActionUpdater { _: AnActionEvent? -> moduleMustacheContextTable.selection.isNotEmpty() }
      .setRemoveAction { _: AnActionButton? ->
        moduleContexts.set(moduleContexts.get().filterNot { moduleMustacheContextTable.selectedObjects.contains(it) })
      }
      .setMoveUpAction(null)
      .setMoveDownAction(null)
      .createPanel()
    panelForTable.preferredSize = JBDimension(-1, 100)
    return panelForTable
  }

  private val mustacheSettingsGroup = panel {
    group(PdfViewerBundle.message("pdf.viewer.settings.group.mustache")) {
      row(PdfViewerBundle.message("pdf.viewer.settings.mustache.preview.layout.label")) {
        comboBox(
          model = DefaultComboBoxModel(arrayOf(false, true)),
          renderer = SimpleListCellRenderer.create("", ::presentSplitLayout)
        ).bindItem(isVerticalSplit)
      }

      moduleMustacheContextTable = createModuleMustacheContextTable()
      group("Module Contexts") {
        row {
          cell(
            createMappingsTableDecorator()
          )
            .resizableColumn()
            .gap(RightGap.SMALL)
            .align(AlignX.FILL)
        }
      }
    }
  }

  val invertDocumentColorsWithTheme = properties.property(settings.invertColorsWithTheme).apply {
    afterPropagation {
      // Automatically toggle the invertDocumentColors checkbox so the pdf color switched to the current theme.
      if (this.get()) invertDocumentColors.set(EditorColorsManager.getInstance().isDarkEditor)
    }
  }
  val invertDocumentColors = properties.property(settings.invertDocumentColors)
  val documentColorsInvertIntensity = properties.property(settings.documentColorsInvertIntensity)

  private val invertColorsGroup = panel {
    group(PdfViewerBundle.message("pdf.viewer.settings.group.colors.document")) {
      row {
        checkBox(PdfViewerBundle.message("pdf.viewer.settings.colors.document.with.theme"))
          .bindSelected(invertDocumentColorsWithTheme)
          .comment(PdfViewerBundle.message("pdf.viewer.settings.colors.document.with.theme.comment"))
      }
      row {
        checkBox(PdfViewerBundle.message("pdf.viewer.settings.colors.document.invert"))
          .bindSelected(invertDocumentColors)
          .enabledIf(invertDocumentColorsWithTheme.not())
      }
      row(PdfViewerBundle.message("pdf.viewer.settings.colors.document.invert.intensity")) {
        intTextField(1..100, 1)
          .bindIntText(documentColorsInvertIntensity)
        rowComment(PdfViewerBundle.message("pdf.viewer.settings.colors.document.invert.intensity.comment"))
      }
    }
  }

  val useCustomColors = properties.property(settings.useCustomColors)
  val customBackgroundColor = properties.property(settings.customBackgroundColor)
  val customForegroundColor = properties.property(settings.customForegroundColor)
  val customIconColor = properties.property(settings.customIconColor)

  private val backgroundColorPanel = ColorPanel().apply {
    selectedColor = Color(customBackgroundColor.get())
    addActionListener {
      selectedColor?.let { customBackgroundColor.set(it.rgb) }
    }
  }
  private val foregroundColorPanel = ColorPanel().apply {
    addActionListener {
      selectedColor?.let { customForegroundColor.set(it.rgb) }
    }
  }
  private val iconColorPanel = ColorPanel().apply {
    addActionListener {
      selectedColor?.let { customIconColor.set(it.rgb) }
    }
  }

  private val customColorsGroup = panel {
    group(PdfViewerBundle.message("pdf.viewer.settings.group.colors.viewer")) {
      row {
        checkBox(PdfViewerBundle.message("pdf.viewer.settings.viewer.colors"))
          .bindSelected(useCustomColors)
          .comment(PdfViewerBundle.message("pdf.viewer.settings.group.colors.viewer.comment"))
      }
      indent {
        panel {
          row(PdfViewerBundle.message("pdf.viewer.settings.foreground")) {
            cell(foregroundColorPanel)
          }
          row(PdfViewerBundle.message("pdf.viewer.settings.background")) {
            cell(backgroundColorPanel)
          }
          row(PdfViewerBundle.message("pdf.viewer.settings.icons")) {
            cell(iconColorPanel)
            rowComment(PdfViewerBundle.message("pdf.viewer.settings.icons.color.notice"))
          }
          row {
            link(PdfViewerBundle.message("pdf.viewer.settings.set.current.theme")) {
              resetViewerColorsToTheme()
            }
          }
        }.enabledIf(useCustomColors)
      }
    }
  }

  init {
    layout = BorderLayout()
    add(panel {
      row { cell(generalSettingsGroup).align(AlignX.FILL) }
      row { cell(mustacheSettingsGroup).align(AlignX.FILL) }
      row { cell(invertColorsGroup).align(AlignX.FILL) }
      row { cell(customColorsGroup).align(AlignX.FILL) }
    })
  }

  fun reset() {
    enableDocumentAutoReload.set(settings.enableDocumentAutoReload)
    defaultSidebarViewMode.set(settings.defaultSidebarViewMode)
    invertDocumentColorsWithTheme.set(settings.invertColorsWithTheme)
    invertDocumentColors.set(settings.invertDocumentColors)
    documentColorsInvertIntensity.set(settings.documentColorsInvertIntensity)
    useCustomColors.set(settings.useCustomColors)
    customForegroundColor.set(settings.customForegroundColor)
    customBackgroundColor.set(settings.customBackgroundColor)
    customIconColor.set(settings.customIconColor)
    moduleContexts.set(settings.moduleContexts)
  }

  private fun resetViewerColorsToTheme() {
    PdfViewerSettings.run {
      backgroundColorPanel.selectedColor = defaultBackgroundColor
      customBackgroundColor.set(defaultBackgroundColor.rgb)
      foregroundColorPanel.selectedColor = defaultForegroundColor
      customForegroundColor.set(defaultForegroundColor.rgb)
      iconColorPanel.selectedColor = defaultIconColor
      customIconColor.set(defaultIconColor.rgb)
    }
  }

  private fun presentSplitLayout(splitLayout: Boolean?): @Nls String {
    return when (splitLayout) {
      false -> PdfViewerBundle.message("pdf.viewer.settings.mustache.preview.layout.horizontal")
      true -> PdfViewerBundle.message("pdf.viewer.settings.mustache.preview.layout.vertical")
      else -> ""
    }
  }
}
