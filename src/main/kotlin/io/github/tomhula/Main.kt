package io.github.tomhula

import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import qrcode.QRCode

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args[0]

    val oris: Oris = OrisImpl()

    val userId = oris.getUser(userRegNum)!!.id
    val userEventEntries = oris.getUserEventEntries(userId, dateFrom = LocalDate(2018, 1, 1))

    println("Found ${userEventEntries.size} user event entries for user $userId")

    val eventsDeferred = userEventEntries
        .sortedBy { it.eventDate }
        .map { async { oris.getEvent(it.eventId) } }

    val events = eventsDeferred.awaitAll()

    val eventQrCodes = events.associateWith {
        QRCode.ofSquares()
            .withInnerSpacing(0)
            .build("https://oris.orientacnisporty.cz/Zavod?id=${it.id}")
            .renderToBytes()
    }
}
