package com.firsttimeinforever.intellij.pdf.viewer.ui.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.Url
import com.intellij.util.Urls.parseEncoded
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses
import org.jetbrains.io.response
import org.jetbrains.io.send
import java.io.File


class StaticServer: HttpRequestHandler() {
    companion object {
        fun getInstance(): StaticServer? {
            return EP_NAME.findExtension(StaticServer::class.java)
        }

        val BASE_DIRECTORY = File("/web-view/")

//        private val URL_UUID = UUID.randomUUID().toString()
        private val URL_UUID = "64fa8636-e686-4c63-9956-132d9471ce77"
    }

    private val serverUrl = "http://localhost:${BuiltInServerManager.getInstance().port}/$URL_UUID"
    private val logger = logger<StaticServer>();

    override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
        logger.debug(urlDecoder.path())
        logger.debug(urlDecoder.parameters().toString())
        // Check if current request is actually ours
        if (!urlDecoder.path().contains(URL_UUID)) {
            logger.debug("Current url is not ours. Passing it to the next handler.")
            return false
        }
        val requestPath = urlDecoder.path().removePrefix("/$URL_UUID")
        if (requestPath == "/get-file") {
            val targetFile = File(urlDecoder.parameters()["localFile"]!!.first())
            logger.debug("Trying to send file: $targetFile")
            FileResponses.sendFile(request, context.channel(), targetFile.toPath())
            return true
        }
        val targetFile = File(BASE_DIRECTORY, requestPath)
        val contentType = FileResponses.getContentType(targetFile.toString())
        val resultBuffer = Unpooled.wrappedBuffer(ResourceLoader.load(targetFile))
        val response = response(contentType, resultBuffer)
        response.send(context.channel(), request)
        return true
    }

    fun getFilePreviewUrl(path: String): Url? {
        return getIndexUrl()?.addParameters(mapOf(Pair("path", getLocalFileUrl(path).toString())))
    }

    private fun getLocalFileUrl(path: String): Url? {
        return parseEncoded("$serverUrl/get-file")!!.addParameters(mapOf(Pair("localFile", path)))
    }

    private fun getIndexUrl(): Url? {
        return parseEncoded("$serverUrl/index.html")
    }
}