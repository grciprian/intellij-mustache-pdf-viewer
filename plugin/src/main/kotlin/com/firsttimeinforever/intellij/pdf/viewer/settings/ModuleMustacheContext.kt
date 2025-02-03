package com.firsttimeinforever.intellij.pdf.viewer.settings

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import exceptions.ModuleNotFoundException
import java.nio.file.Paths

data class ModuleMustacheContext(
  var modulePath: String = "",
  var moduleName: String = "",
  var templatesDir: String = "",
  var suffix: String = "",
  var fontsDir: String = "",
) {

  companion object {

    fun getDefault(module: Module): ModuleMustacheContext {
      val projectDir = module.project.guessProjectDir()?.path
      val templatesDir = projectDir
        ?.let { "$it/$DEFAULT_MUSTACHE_TEMPLATES_PATH" }
        ?.let { if (Paths.get(it).toFile().exists()) it else null } ?: ""
      val fontsDir = projectDir
        ?.let { "$it/$DEFAULT_MUSTACHE_FONTS_PATH" }
        ?.let { if (Paths.get(it).toFile().exists()) it else null } ?: ""
      val moduleDir = module.guessModuleDir()
        ?: throw ModuleNotFoundException("Could not get moduleDir")
      return ModuleMustacheContext(moduleDir.path, module.name, templatesDir, DEFAULT_MUSTACHE_SUFFIX, fontsDir)
    }

    const val DEFAULT_MUSTACHE_TEMPLATES_PATH = "src/main/resources/templates"

    const val DEFAULT_MUSTACHE_SUFFIX = ".mustache"

    const val DEFAULT_MUSTACHE_FONTS_PATH = "fonts"
  }
}
