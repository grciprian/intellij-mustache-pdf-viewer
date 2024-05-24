package com.firsttimeinforever.intellij.pdf.viewer.mustache

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileMoveEvent

class DirectoryWatcher(private val directory: VirtualFile) : VirtualFileListener {
  override fun fileCreated(event: VirtualFileEvent) {
    if (isUnderWatchedDirectory(event.file)) {
      println("File created: ${event.file.path}")
    }
  }

  override fun fileDeleted(event: VirtualFileEvent) {
    if (isUnderWatchedDirectory(event.file)) {
      println("File deleted: ${event.file.path}")
    }
  }

  override fun fileMoved(event: VirtualFileMoveEvent) {
    if (isUnderWatchedDirectory(event.file)) {
      println("File modified: ${event.file.path}")
    }
  }

  private fun isUnderWatchedDirectory(file: VirtualFile): Boolean {
    return file.path.startsWith(directory.path)
  }
}
