package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.view.PdfEditorViewComponent
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTabbedPane
import generate.PdfGenerationService
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JTabbedPane

class PdfFileEditorWrapper(project: Project, virtualFile: VirtualFile) : FileEditorBase(), DumbAware {

  private val fileIncludeProps = processFileIncludesProps(virtualFile)
  val jbTabbedPane = JBTabbedPane()

  init {
    if (fileIncludeProps.rootParents.isNotEmpty()) {
      val fileResourcesPathWithPrefix = PdfGenerationService.getFileResourcesPathWithPrefix(file)
      val rootMustacheFile = VfsUtil.findFile(Path.of(fileResourcesPathWithPrefix + fileIncludeProps.rootParents.first()), true)
      Objects.requireNonNull(rootMustacheFile)
      val processedPdfFile = getProcessedPdfFile(rootMustacheFile!!)
    }
  }

  override fun getComponent(): JComponent {
    TODO("Not yet implemented")
  }

  override fun getName(): String {
    TODO("Not yet implemented")
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    TODO("Not yet implemented")
  }

  private fun processFileIncludesProps(file: VirtualFile): IncludeProps {
    val includeMap = HashMap<String, IncludeProps>()

    val fileResourcesPathWithPrefix = PdfGenerationService.getFileResourcesPathWithPrefix(file)
    val root = VfsUtil.findFile(Path.of(fileResourcesPathWithPrefix), true)
    Objects.requireNonNull(root)

    // TODO tratare daca nu e gasit root resources with prefix
    VfsUtil.processFileRecursivelyWithoutIgnored(root!!) { f: VirtualFile ->
      if (f.isDirectory) return@processFileRecursivelyWithoutIgnored true
      var indexOfSuffix = f.canonicalPath!!.indexOf(PdfGenerationService.DEFAULT_SUFFIX)
      if (indexOfSuffix == -1) indexOfSuffix = f.canonicalPath!!.length
      val relativePathFromResourcesPath = f.canonicalPath!!.substring(fileResourcesPathWithPrefix.length, indexOfSuffix)
      if (!includeMap.containsKey(relativePathFromResourcesPath)) {
        includeMap[relativePathFromResourcesPath] = IncludeProps.empty
      }
      try {
        val contents = VfsUtil.loadText(f)
        Arrays.stream(contents.split("(\\{\\{>)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()).skip(1)
          .forEach { include: String ->
            val indexOfIncludeEnd = include.indexOf("}}")
            if (indexOfIncludeEnd == -1) throw RuntimeException("Malformed include found!")
            val cleanedUpInclude = include.substring(0, indexOfIncludeEnd)
            val maybeExistingEntry = includeMap.getOrDefault(cleanedUpInclude, IncludeProps.empty)
            maybeExistingEntry.directParents.plus(relativePathFromResourcesPath)
            includeMap[cleanedUpInclude] = IncludeProps(maybeExistingEntry.numberOfIncludes + 1, maybeExistingEntry.directParents)
          }
      } catch (e: IOException) {
        throw RuntimeException(e)
      }
      true
    }


    //process and filter main directParents into rootParents
    includeMap.values.forEach { includeProps: IncludeProps ->
      includeProps.processRootParentsBasedOn(
        includeMap
      )
    }

    var indexOfSuffix = file.canonicalPath!!.indexOf(PdfGenerationService.DEFAULT_SUFFIX)
    if (indexOfSuffix == -1) indexOfSuffix = file.canonicalPath!!.length
    val relativePathFromResourcesPath = file.canonicalPath!!.substring(fileResourcesPathWithPrefix.length, indexOfSuffix)
    return includeMap.getOrDefault(relativePathFromResourcesPath, IncludeProps.empty)
  }

  private class IncludeProps(val numberOfIncludes: Int, val directParents: Set<String>) {
    val rootParents: MutableSet<String> = HashSet()

    fun processRootParentsBasedOn(includeMap: HashMap<String, IncludeProps>) {
      var dp: Set<String> = HashSet(this.directParents)
      while (dp.isNotEmpty()) {
        // adauga la rootParents toti directParents care nu mai au directParents
        rootParents.addAll(
          dp.stream()
            .filter { directParent: String? ->
              includeMap.getOrDefault(
                directParent,
                empty
              ).directParents.isEmpty()
            }
            .collect(Collectors.toUnmodifiableSet())
        )

        // filtreaza directParents care au directParents si devine noul directParents pentru a se duce pe flow in sus pana ajunge la rootParents
        dp = dp.stream()
          .filter { directParent: String? ->
            includeMap.getOrDefault(
              directParent,
              empty
            ).directParents.isNotEmpty()
          }
          .map { directParentWithDirectParents: String? ->
            includeMap.getOrDefault(
              directParentWithDirectParents,
              empty
            ).directParents
          }
          .flatMap { obj: Set<String> -> obj.stream() }
          .collect(Collectors.toUnmodifiableSet())
      }
    }

    companion object {
      val empty: IncludeProps
        get() = IncludeProps(0, HashSet())
    }
  }

  companion object {
    private val TEMP_PDF_NAME = "temp.pdf"

    fun getProcessedPdfFile(file: VirtualFile): VirtualFile? {
      val pdfByteArray = PdfGenerationService.getInstance(file).generatePdf(HashMap<String, String>(), VfsUtil.loadText(file))
      val outputPath = Path.of(TEMP_PDF_NAME)
      if (!Files.exists(outputPath)) {
        Files.createFile(outputPath)
      }
      Files.write(outputPath, pdfByteArray)
      return VfsUtil.findFile(outputPath, true)
    }
  }
}
