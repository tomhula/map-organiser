package io.github.tomhula

import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args[0]
    
    val oris: Oris = OrisImpl()

    val tomId = oris.getUser(userRegNum)!!.id
    val userEventEntries = oris.getUserEventEntries(tomId, dateFrom = LocalDate(2018, 1, 1))

    println("Found ${userEventEntries.size} user event entries for user $tomId")

    val eventsDeferred = userEventEntries
        .sortedBy { it.eventDate }
        .map { async { oris.getEvent(it.eventId) } }

    val events = eventsDeferred.awaitAll()

    for (event in events)
    {
        println("${event.date} ${event.name} - ${event.map}")
    }
}
