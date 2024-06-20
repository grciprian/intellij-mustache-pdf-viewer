rootProject.name = "intellij-pdf-viewer-mustache-enhanced"

include(":web-view")
include(":web-view:viewer")
include(":web-view:bootstrap")
include(":plugin")
include(":mpi")
include(":model")

pluginManagement {
  plugins {
    val kotlinVersion: String by settings
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("multiplatform") version kotlinVersion
    kotlin("js") version kotlinVersion
  }
}
