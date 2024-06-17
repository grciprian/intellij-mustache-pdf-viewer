package com.firsttimeinforever.intellij.pdf.viewer.settings

import com.firsttimeinforever.intellij.pdf.viewer.model.SidebarViewMode
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil.copyBean
import java.util.*

@State(name = "PdfViewerSettings", storages = [(Storage("pdf_viewer.xml"))])
class PdfViewerSettings : PersistentStateComponent<PdfViewerSettings> {

  var useCustomColors = false
  var customBackgroundColor: Int = defaultBackgroundColor.rgb
  var customForegroundColor: Int = defaultForegroundColor.rgb
  var customIconColor: Int = defaultIconColor.rgb
  var enableDocumentAutoReload = true
  var documentColorsInvertIntensity: Int = DEFAULT_DOCUMENT_COLORS_INVERT_INTENSITY
  var invertDocumentColors = false
  var invertColorsWithTheme = false

  var defaultSidebarViewMode: SidebarViewMode = SidebarViewMode.THUMBNAILS

  var customMustacheFontsPath: String = Optional.ofNullable(ProjectUtil.getActiveProject()?.basePath)
    .map { "$it/$DEFAULT_MUSTACHE_FONTS_PATH" }
//    .map {
//      val filePath = Path.of(it)
//      return@map if (Files.exists(filePath) && Files.isDirectory(filePath)) it else ""
//    }
    .orElse("")
  var customMustachePrefix = DEFAULT_MUSTACHE_PREIFX
  var customMustacheSuffix = DEFAULT_MUSTACHE_SUFFIX
  var isVerticalSplit = true

  fun notifySettingsListeners() {
    ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC_SETTINGS).settingsChanged(this)
  }

  fun notifyMustacheFontsPathSettingsListeners() {
    ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC_MUSTACHE_FONTS_PATH).fontsPathChanged(this)
  }

  fun notifyMustacheFilePropsSettingsListeners() {
    ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC_MUSTACHE_FILE_PROPS).filePropsChanged(this)
  }

  override fun getState() = this

  override fun loadState(state: PdfViewerSettings) {
    copyBean(state, this)
  }

  companion object {
    val TOPIC_SETTINGS = Topic(PdfViewerSettingsListener::class.java)
    val TOPIC_MUSTACHE_FONTS_PATH = Topic(PdfViewerMustacheFontsPathSettingsListener::class.java)
    val TOPIC_MUSTACHE_FILE_PROPS = Topic(PdfViewerMustacheFilePropsSettingsListener::class.java)

    val instance: PdfViewerSettings
      get() = service()

    val defaultBackgroundColor
      get() = EditorColorsManager.getInstance().globalScheme.defaultBackground

    val defaultForegroundColor
      get() = EditorColorsManager.getInstance().globalScheme.defaultForeground

    val defaultIconColor
      get() = defaultForegroundColor

    const val DEFAULT_DOCUMENT_COLORS_INVERT_INTENSITY = 85

    const val DEFAULT_MUSTACHE_FONTS_PATH = "fonts"

    const val DEFAULT_MUSTACHE_PREIFX = "templates"

    const val DEFAULT_MUSTACHE_SUFFIX = "mustache"

//    val ILLEGAL_CHARACTERS_REGEX: Pattern = Pattern.compile("[\\/\\n\\r\\t\\u0000\\f`\\?\\*\\\\<>|\\\":]\n")

    val enableExperimentalFeatures: Boolean
      get() = Registry.`is`("pdf.viewer.enableExperimentalFeatures")

    val isDebugMode: Boolean
      get() = Registry.`is`("pdf.viewer.debug", false)
  }
}
