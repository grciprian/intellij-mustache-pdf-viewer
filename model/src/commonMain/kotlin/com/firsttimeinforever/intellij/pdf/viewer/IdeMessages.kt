package com.firsttimeinforever.intellij.pdf.viewer

import com.firsttimeinforever.intellij.pdf.viewer.model.*
import com.firsttimeinforever.intellij.pdf.viewer.tex.SynctexPreciseLocation
import kotlinx.serialization.Serializable

object IdeMessages {
  @Serializable
  data class Search(val text: String, val direction: SearchDirection)

  @Serializable
  data class SidebarViewModeSetRequest(val mode: SidebarViewMode)

  @Serializable
  data class PageSpreadStateSetRequest(val state: PageSpreadState)

  @Serializable
  data class GotoExactPage(val page: Int)

  @Serializable
  data class GotoPage(val direction: PageGotoDirection)

  @Serializable
  class DocumentInfoRequest

  @Serializable
  data class ChangeScaleStepped(val increase: Boolean)

  @Serializable
  data class RotatePages(val clockwise: Boolean)

  @Serializable
  data class SetScrollDirection(val direction: ScrollDirection)

  @Serializable
  data class UpdateThemeColors(val theme: ViewTheme)

  @Serializable
  data class SynctexForwardSearch(val location: SynctexPreciseLocation)

  @Serializable
  data class SynctexAvailability(val isAvailable: Boolean)

  @Serializable
  data class NavigateTo(val destination: String)
}