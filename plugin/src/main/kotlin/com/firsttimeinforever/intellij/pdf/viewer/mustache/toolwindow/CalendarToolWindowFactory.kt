package com.firsttimeinforever.intellij.pdf.viewer.mustache.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.util.*
import javax.swing.*

class CalendarToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
    val toolWindowContent = CalendarToolWindowContent(toolWindow)
    val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class CalendarToolWindowContent(toolWindow: ToolWindow) {

    private val _contentPanel = JPanel()
    private val currentDate = JLabel()
    private val timeZone = JLabel()
    private val currentTime = JLabel()

    init {
      _contentPanel.layout = BorderLayout(0, 20)
      _contentPanel.border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
      _contentPanel.add(createCalendarPanel(), BorderLayout.PAGE_START)
      _contentPanel.add(createControlsPanel(toolWindow), BorderLayout.CENTER)
      updateCurrentDateTime()
    }

    @NotNull
    private fun createCalendarPanel(): JPanel {
      val calendarPanel = JPanel()
      setIconLabel(currentDate, CALENDAR_ICON_PATH)
      setIconLabel(timeZone, TIME_ZONE_ICON_PATH)
      setIconLabel(currentTime, TIME_ICON_PATH)
      calendarPanel.add(currentDate)
      calendarPanel.add(timeZone)
      calendarPanel.add(currentTime)
      return calendarPanel
    }

    private fun setIconLabel(label: JLabel, imagePath: String) {
      label.icon = ImageIcon(javaClass.getResource(imagePath))
    }

    @NotNull
    private fun createControlsPanel(toolWindow: ToolWindow): JPanel {
      val controlsPanel = JPanel()
      val refreshDateAndTimeButton = JButton("Refresh")
      refreshDateAndTimeButton.addActionListener { updateCurrentDateTime() }
      controlsPanel.add(refreshDateAndTimeButton)
      val hideToolWindowButton = JButton("Hide")
      hideToolWindowButton.addActionListener { toolWindow.hide(null) }
      controlsPanel.add(hideToolWindowButton)
      return controlsPanel
    }

    private fun updateCurrentDateTime() {
      val calendar = Calendar.getInstance()
      currentDate.text = getCurrentDate(calendar)
      timeZone.text = getTimeZone(calendar)
      currentTime.text = getCurrentTime(calendar)
    }

    private fun getCurrentDate(calendar: Calendar): String {
      return "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
    }

    private fun getTimeZone(calendar: Calendar): String {
      val gmtOffset = calendar.get(Calendar.ZONE_OFFSET) // offset from GMT in milliseconds
      val gmtOffsetString = (gmtOffset / 3600000).toString()
      return if (gmtOffset > 0) "GMT + $gmtOffsetString" else "GMT - $gmtOffsetString"
    }

    private fun getCurrentTime(calendar: Calendar): String {
      return "${getFormattedValue(calendar, Calendar.HOUR_OF_DAY)}:${getFormattedValue(calendar, Calendar.MINUTE)}"
    }

    private fun getFormattedValue(calendar: Calendar, calendarField: Int): String {
      val value = calendar.get(calendarField)
      return StringUtils.leftPad(value.toString(), 2, "0")
    }

    val contentPanel: JPanel
      get() = _contentPanel

    companion object {
      private const val CALENDAR_ICON_PATH = "/icons/toolwindow/Calendar-icon.png"
      private const val TIME_ZONE_ICON_PATH = "/icons/toolwindow/Time-zone-icon.png"
      private const val TIME_ICON_PATH = "/icons/toolwindow/Time-icon.png"
    }
  }
}
