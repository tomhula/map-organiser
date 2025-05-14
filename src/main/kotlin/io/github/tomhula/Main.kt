package io.github.tomhula

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import io.github.tomhula.orisclient.dto.Event
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import qrcode.QRCode
import java.io.File
import java.io.StringWriter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


val oris: Oris = OrisImpl()
val freemarkerConfig = Configuration(Configuration.VERSION_2_3_34).apply {
    setClassLoaderForTemplateLoading(object {}::class.java.classLoader, "templates")
    defaultEncoding = "UTF-8"
    templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
    logTemplateExceptions = false
    wrapUncheckedExceptions = true
}

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args.getOrNull(0) ?: throw RuntimeException("User registration number must be provided")

}

private suspend fun getUserEvents(userRegNum: String): List<Event>
{
    val userId = oris.getUser(userRegNum)?.id

    if (userId == null)
        throw RuntimeException("User with registration number $userRegNum not found")

    val userEventEntries = oris.getUserEventEntries(userId)

    return coroutineScope { 
        val eventsDeferred = userEventEntries.map { userEventEntry ->
            async { oris.getEvent(userEventEntry.eventId) }
        }
        eventsDeferred.awaitAll()
    }
}

private fun createEventQrCode(event: Event): ByteArray
{
    return QRCode.ofSquares()
        .withInnerSpacing(0)
        .build("https://oris.orientacnisporty.cz/Zavod?id=${event.id}")
        .renderToBytes()
}

@OptIn(ExperimentalEncodingApi::class)
private fun renderCell(event: Event): String
{
    val qrCode = createEventQrCode(event)
    val qrCodeBase64 = Base64.encode(qrCode)
    val qrCodeUrl = "data:image/png;base64,$qrCodeBase64"

    val dataModel = mapOf(
        "event" to event,
        "qrCodeUrl" to qrCodeUrl
    )

    val template = freemarkerConfig.getTemplate("cell.ftlh")
    val writer = StringWriter()
    template.process(dataModel, writer)
    val result = writer.toString()
    writer.close()
    return result
}
