package com.firsttimeinforever.intellij.pdf.viewer.jcef

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.utility.PdfResourceLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.Url
import com.intellij.util.Urls
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses
import org.jetbrains.io.response
import org.jetbrains.io.send
import java.lang.IllegalStateException
import java.net.URLEncoder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.extension
import kotlin.random.Random

internal class PdfStaticServer : HttpRequestHandler() {
  private val serverUrl = "http://localhost:${BuiltInServerManager.getInstance().port}/$uuid"

  init {
    logger.debug("Starting static server with url: $serverUrl")
  }

  override fun process(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext
  ): Boolean {
    logger.info("Incoming request:\n\tpath: ${urlDecoder.path()}\n\tparameters: ${urlDecoder.parameters()}")
    // Check if current request is actually ours
    if (!urlDecoder.path().contains(uuid)) {
      logger.debug("Current url is not ours. Passing it to the next handler.")
      return false
    }
    val requestPath = urlDecoder.path().removePrefix("/$uuid")
    logger.info(requestPath)
    if (isExternalFilePath(requestPath)) {
        try {
            sendExternalFile(requestPath.removePrefix("/get-file/"), context, request)
        } catch (e: IllegalStateException) {
            println(e.stackTrace)
        }
    } else {
      sendInternalFile(requestPath, context, request)
    }
    return true
  }

  private fun isExternalFilePath(path: String): Boolean {
    return path.startsWith("/get-file/")
  }

  private fun assertValidExternalPath(path: Path) {
    check(path.extension.lowercase(Locale.getDefault()) == "pdf") { "Only pdf files outside the jar can be served!" }
  }

  private fun sendExternalFile(path: String, context: ChannelHandlerContext, request: FullHttpRequest) {
    val targetFile = Paths.get(path)
    assertValidExternalPath(targetFile)
    logger.info("Sending external file: $targetFile")
    FileResponses.sendFile(request, context.channel(), Paths.get(targetFile.toString()))
  }

  private fun makeInternalPath(path: String): String {
    val actualPath = when {
      path.first() == '/' -> path.drop(1)
      else -> path
    }
    return "/$baseDirectory/$actualPath"
  }

  private fun sendInternalFile(path: String, context: ChannelHandlerContext, request: FullHttpRequest) {
    val targetFile = makeInternalPath(path)
    if (PdfViewerSettings.isDebugMode && targetFile.endsWith(".js.map")) {
      logger.warn("Ignoring sourcemap $targetFile")
      return
    }
    val contentType = FileResponses.getContentType(targetFile)
    logger.info("Sending internal file: $targetFile with contentType: $contentType")
    val resultBuffer = Unpooled.wrappedBuffer(PdfResourceLoader.loadFromRoot(targetFile))
    val response = response(contentType, resultBuffer)
    response.send(context.channel(), request)
  }

  fun getPreviewUrl(filePath: String, withReloadSalt: Boolean = false): String {
    val salt = if (withReloadSalt) Random.nextInt() else 0
//    val encodedPath = URLEncoder.encode(filePath, "utf-8").replace("%2F", "/")
    val encodedPath = URLEncoder.encode(filePath, "utf-8")
    val url = parseEncodedPath("$serverUrl/index.html?__reloadSalt=$salt&file=get-file%2F$encodedPath")
    val server = BuiltInServerManager.getInstance()
    return server.addAuthToken(url).toExternalForm()
  }

  private fun parseEncodedPath(target: String): Url {
    return Urls.parseEncoded(target) ?: error("Could not parse encoded path for \"$target\"")
  }

  companion object {
    private val logger = logger<PdfStaticServer>()

    val instance by lazy { checkNotNull(EP_NAME.findExtension(PdfStaticServer::class.java)) }

    private const val baseDirectory = "web-view"
    private const val uuid = "64fa8636-e686-4c63-9956-132d9471ce77"
  }
}
