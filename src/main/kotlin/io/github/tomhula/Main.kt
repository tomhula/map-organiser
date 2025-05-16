package io.github.tomhula

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import io.github.tomhula.orisclient.dto.Event
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.serialization.json.Json
import qrcode.QRCode
import java.io.File
import java.io.StringWriter
import kotlin.collections.map
import kotlin.collections.mapOf
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.writeText
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
    install(ContentNegotiation) {
        json(Json { 
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
}
/** String used to determine if an address is Prague. */
const val OPENSTREETMAP_PRAGUE_NAME = "Hlavní město Praha"
const val NULL_PLACE = "Neznámé místo"
const val NULL_REGION = "Neznámá oblast"

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args.getOrNull(0) ?: throw RuntimeException("User registration number must be provided")
    val eventGridOutputPath = args.getOrNull(1) ?: "event_grid.html"
    val regionIndexOutputPath = args.getOrNull(2) ?: "region_index.html"

    println("Downloading events of user $userRegNum...")
    val events = getUserEvents(userRegNum)
        // Discipline id 10 = "Cups and ranking" (Žebříčky). Cups and ranking disciplines are not races and do not have a map.
        .filterNot { event -> event.discipline?.id == 10 }
    val numberedEvents = numberEvents(events)

    println("Generating event grid...")
    val gridHtml = renderGrid(numberedEvents)
    gridHtml.saveToFile(eventGridOutputPath)
    println("Event grid of ${numberedEvents.size} events has been generated to $eventGridOutputPath")

    println("Generating region index...")

    val eventsByRegion = groupEventsByRegion(events)
    val eventsByPlaceByRegion = eventsByRegion.mapValues { entry ->
        entry.value.groupBy { it.place }
    }
    val eventNumbersByPlaceByRegion = eventsByPlaceByRegion.mapValues { regionEntry ->
        regionEntry.value.mapValues { placeEntry ->
            placeEntry.value.map { event -> numberedEvents[event]!! }
        }
    }

    val eventNumbersByPlaceByRegionOrdered = eventNumbersByPlaceByRegion.mapValues { regionEntry ->
        regionEntry.value.mapValues { placeEntry ->
            placeEntry.value.sorted()
        }.toSortedMap(compareBy { place -> place?.lowercase() })
    }.toSortedMap(compareBy { region -> region?.lowercase() })
    
    val eventNumbersByPlaceByRegionOrderedNonNull = eventNumbersByPlaceByRegionOrdered.mapValues { regionEntry ->
        regionEntry.value.mapKeys { placeEntry -> placeEntry.key ?: NULL_PLACE }
    }.mapKeys { regionEntry -> regionEntry.key ?: NULL_REGION }

    val regionIndexHtml = renderRegionIndex(eventNumbersByPlaceByRegionOrderedNonNull)
    
    File(regionIndexOutputPath).writeText(regionIndexHtml)
}

private fun String.saveToFile(path: String) = File(path).writeText(this)

private suspend fun groupEventsByRegion(events: Iterable<Event>): Map<String?, List<Event>>
{
    // No running in parallel and with a 1-second delay because of the Nominatim policy: https://operations.osmfoundation.org/policies/nominatim/
    val eventsWithRegion = events.associateWith { event ->
        val region = event.getRegion(events)?.replace("obvod ", "")?.replace("okres ", "")
        delay(1.seconds)
        region
    }
    
    val eventsByRegion = eventsWithRegion.keys.groupBy { event -> eventsWithRegion[event] }

    return eventsByRegion
}

private fun Event.getParent(events: Iterable<Event>): Event? = events.find { it.id == parentId }

private fun Event.getCoordinates(): Coordinates?
{
    val lat = gPSLat?.toFloatOrNull()
    val lon = gPSLon?.toFloatOrNull()
 
    if (lat == null || lat == 0f || 
        lon == null || lon == 0f)
        return null
    
    return Coordinates(lat, lon)  
}

private fun numberEvents(events: List<Event>): Map<Event, Int>
{
    return events
        .mapIndexed { index, event -> event to index + 1 }
        .toMap()
}

private suspend fun Event.getRegion(events: Iterable<Event>): String?
{
    val address = getAddress(events) ?: return null
    return determineRegionFromAddress(address)
}

/**
 * Returns the first place for the [query].
 */
private suspend fun nominatimSearch(query: String): NominatimPlace?
{
    val response = nominatimHttpClient
        .get("https://nominatim.openstreetmap.org/search") {
            parameter("format", "json")
            parameter("q", query)
            parameter("addressdetails", 1)
            parameter("limit", 1)
        }
        .body<List<NominatimPlace>>()

    return response.firstOrNull()
}

private suspend fun nominatimReverse(coordinates: Coordinates): NominatimPlace
{
    val response = nominatimHttpClient
        .get("https://nominatim.openstreetmap.org/reverse") {
            parameter("format", "json")
            parameter("lat", coordinates.lat)
            parameter("lon", coordinates.lon)
        }
        .body<NominatimPlace>()
    
    return response
}

private suspend fun Event.getAddress(events: Iterable<Event>): NominatimPlace.Address?
{
    val coordinates = getCoordinates()
    
    if (coordinates != null)
        return nominatimReverse(coordinates).address
    
    if (place != null)
    {
        val address = nominatimSearch(place!!)?.address
        
        if (address != null)
            return address
    }
    
    val parent = getParent(events)
    
    if (parent != null)
        return parent.getAddress(events)
    
    return null
}

/**
 * Returns municipality if available.
 * If municipality is not available and the address is in Prague, city_district is returned.
 */
private fun determineRegionFromAddress(address: NominatimPlace.Address): String?
{
    val municipality = address.municipality
    if (municipality != null)
        return municipality

    val isPrague = address.city == OPENSTREETMAP_PRAGUE_NAME
    if (!isPrague)
        return null

    val pragueDistrict = address.city_district
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

private fun renderRegionIndex(regionIndex: Map<String, Map<String, List<Int>>>): String
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
private fun renderGrid(numberedEvents: Map<Event, Int>): String
{
    val gridEvents = numberedEvents.map { (event, number) ->
        val qrCode = createEventQrCode(event)
        val qrCodeBase64 = Base64.encode(qrCode)
        val qrCodeUrl = "data:image/png;base64,$qrCodeBase64"
        GridEvent(
            number = number,
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

data class Coordinates(val lat: Float, val lon: Float)

data class GridEvent(
    val number: Int,
    val date: String?,
    val name: String,
    val place: String?,
    val qrCodeUrl: String,
    val map: String?
)
