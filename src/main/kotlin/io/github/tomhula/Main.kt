package io.github.tomhula

import com.google.gson.JsonParser
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import io.github.tomhula.orisclient.dto.Event
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import qrcode.QRCode
import java.io.File
import java.io.StringWriter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds


val oris: Oris = OrisImpl()
val freemarkerConfig = Configuration(Configuration.VERSION_2_3_34).apply {
    setClassLoaderForTemplateLoading(object {}::class.java.classLoader, "templates")
    defaultEncoding = "UTF-8"
    templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
    logTemplateExceptions = false
    wrapUncheckedExceptions = true
}
val dateFormat = LocalDate.Format { 
    dayOfMonth(Padding.NONE)
    char('.')
    monthNumber(Padding.NONE)
    char('.')
    year()
}
val httpClient = HttpClient(Java)

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args.getOrNull(0) ?: throw RuntimeException("User registration number must be provided")
    val outputFile = args.getOrNull(1) ?: "event_grid.html"

    println("Downloading events of user $userRegNum...")
    val events = getUserEvents(userRegNum)

    println("Generating event grid...")
    val gridHtml = renderGrid(events)
    File(outputFile).writeText(gridHtml)
    println("Event grid of ${events.size} events has been generated to $outputFile")
    
    
    println("Generating municipality index...")
    val eventsByMunicipality = groupEventsByMunicipality(events)
    val eventsByMunicipalityOrdered = eventsByMunicipality.toSortedMap(compareBy { it?.lowercase() })

    val eventsMunicipalityIndex = buildString {
        for ((municipality, municipalityEvents) in eventsByMunicipalityOrdered)
        {
            appendLine("## ${municipality ?: "N/A"}")
            for (event in municipalityEvents)
            {
                val eventNumber = events.indexOf(event) + 1
                appendLine("$eventNumber: ${event.place}  ")
            }
        }
    }

    File("events_by_municipality.md").writeText(eventsMunicipalityIndex)
}

private suspend fun groupEventsByMunicipality(events: Iterable<Event>): Map<String?, List<Event>>
{
    val eventsWithMunicipality = events.associateWith { event ->
        if (event.gPSLat == null || event.gPSLat == "0" || event.gPSLon == null || event.gPSLon == "0")
            return@associateWith null

        val municipality = runCatching {
            val response =
                httpClient.get("https://nominatim.openstreetmap.org/reverse?format=json&lat=${event.gPSLat}&lon=${event.gPSLon}") {
                    header(HttpHeaders.UserAgent, "https://github.com/tomhula/map-organiser")
                }.bodyAsText()
            val json = JsonParser.parseString(response).asJsonObject
            json["address"].asJsonObject["municipality"].asString
        }
        
        if (municipality.isFailure)
            println("Failed to get municipality for https://nominatim.openstreetmap.org/reverse?format=json&lat=${event.gPSLat}&lon=${event.gPSLon}")

        delay(1.seconds)
        municipality.getOrNull()
    }


    val eventsByMunicipality = eventsWithMunicipality.keys.groupBy { event -> eventsWithMunicipality[event] }

    return eventsByMunicipality
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
private fun renderGrid(events: List<Event>): String
{
    val gridEvents = events.map { event ->
        val qrCode = createEventQrCode(event)
        val qrCodeBase64 = Base64.encode(qrCode)
        val qrCodeUrl = "data:image/png;base64,$qrCodeBase64"
        GridEvent(
            date = event.date?.format(dateFormat),
            name = event.name,
            place = event.place,
            qrCodeUrl = qrCodeUrl,
            map = event.map
        )
    }

    val dataModel = mapOf(
        "events" to gridEvents,
    )
    
    val template = freemarkerConfig.getTemplate("grid.ftlh")
    val writer = StringWriter()
    template.process(dataModel, writer)
    val result = writer.toString()
    writer.close()
    return result
}

data class GridEvent(
    val date: String?,
    val name: String,
    val place: String?,
    val qrCodeUrl: String,
    val map: String?
)
