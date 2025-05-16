package io.github.tomhula

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import io.github.tomhula.orisclient.dto.Event
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
import qrcode.QRCode
import java.io.File
import java.io.StringWriter
import kotlin.collections.mapOf
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlin.to


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
val nominatimHttpClient = HttpClient(Java) {
    defaultRequest {
        header(HttpHeaders.UserAgent, "https://github.com/tomhula/map-organiser")
    }
}
/** String used to determine if an address is Prague. */
const val OPENSTREETMAP_PRAGUE_NAME = "Hlavní město Praha"

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args.getOrNull(0) ?: throw RuntimeException("User registration number must be provided")
    val eventGridOutputPath = args.getOrNull(1) ?: "event_grid.html"
    val regionIndexOutputPath = args.getOrNull(2) ?: "region_index.html"

    println("Downloading events of user $userRegNum...")
    val events = getUserEvents(userRegNum)

    println("Generating event grid...")
    val gridHtml = renderGrid(events)
    File(eventGridOutputPath).writeText(gridHtml)
    println("Event grid of ${events.size} events has been generated to $eventGridOutputPath")
    
    
    println("Generating region index...")
    val eventsByRegion = groupEventsByRegion(events)
    val eventsByRegionOrdered = eventsByRegion
        .mapValues { entry -> entry.value.sortedBy { it.place?.lowercase() } }
        .toSortedMap(compareBy { it?.lowercase() })
    
    val eventsByPlaceByRegion = eventsByRegionOrdered.mapValues { entry ->
        entry.value
            .groupBy { it.place?.lowercase() }
            .toSortedMap(compareBy { it?.lowercase() })
    }
    
    val eventsRegionIndex = eventsByPlaceByRegion.mapValues { entry -> 
        entry.value.mapValues { 
            it.value
                .map { event -> events.indexOf(event) + 1 }
                .sorted()
        }
    }
    
    File(regionIndexOutputPath).writeText(renderRegionIndex(eventsRegionIndex))
}

private suspend fun groupEventsByRegion(events: Iterable<Event>): Map<String?, List<Event>>
{
    val eventsWithRegion = events.associateWith { event ->
        val coords = event.getCoordinates(events)
        val region = if (coords != null)
            getRegion(coords.first, coords.second)
        else
            event.place?.let { getRegion(it) }
        
        delay(1.seconds)
        region
    }
    
    val eventsByRegion = eventsWithRegion.keys.groupBy { event -> eventsWithRegion[event] }

    return eventsByRegion
}

/**
 * Returns gps coordinates from the event.
 * If they are missing, returns those of a parent event if it exists. 
 * The parent event is searched by id in the [events] collection.
 */
private fun Event.getCoordinates(events: Iterable<Event> = listOf()): Pair<Float, Float>?
{
    val lat = gPSLat?.toFloatOrNull()
    val lon = gPSLon?.toFloatOrNull()

    if (lat != null && lat != 0f && 
        lon != null && lon != 0f)
        return lat to lon
    
    val parentEvent = events.find { it.id == parentId }
    
    return parentEvent?.getCoordinates(events)
}

private suspend fun getRegion(lat: Float, lon: Float): String?
{
    val response = nominatimHttpClient
        .get("https://nominatim.openstreetmap.org/reverse") {
            parameter("format", "json")
            parameter("lat", lat)
            parameter("lon", lon)
        }
        .bodyAsText()
    val responseJson = JsonParser.parseString(response).asJsonObject
    val addressJson = responseJson["address"]?.asJsonObject ?: return null
    
    return parseRegionFromAddress(addressJson)?.replace("obvod ", "")?.replace("okres ", "")
}

private suspend fun getRegion(place: String): String?
{
    val response = nominatimHttpClient
        .get("https://nominatim.openstreetmap.org/search") {
            parameter("format", "json")
            parameter("q", place)
            parameter("addressdetails", 1)
            parameter("limit", 1)
        }
        .bodyAsText()
    
    // Note that 'search' endpoint, unline 'reverse' endpoint returns an array of results
    val json = JsonParser.parseString(response).asJsonArray.firstOrNull()?.asJsonObject ?: return null
    val addressJson = json["address"]?.asJsonObject ?: return null
    
    return parseRegionFromAddress(addressJson)
}

/**
 * Returns municipality if available.
 * If municipality is not available and the address is in Prague, city_district is returned.
 */
private fun parseRegionFromAddress(addressJson: JsonObject): String?
{
    val municipality = addressJson["municipality"]?.asString
    if (municipality != null)
        return municipality

    val isPrague = addressJson["city"]?.asString == OPENSTREETMAP_PRAGUE_NAME
    if (!isPrague)
        return null

    val pragueDistrict = addressJson["city_district"]?.asString
    return pragueDistrict
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

private fun renderRegionIndex(regionIndex: Map<String?, Map<String?, List<Int>>>): String
{
    val dataModel = mapOf(
        "regionIndex" to regionIndex
    )
    
    val template = freemarkerConfig.getTemplate("region_index.ftlh")
    val writer = StringWriter()
    template.process(dataModel, writer)
    val result = writer.toString()
    writer.close()
    return result
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
