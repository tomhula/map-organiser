package io.github.tomhula

import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl

suspend fun main(args: Array<String>)
{
    val oris: Oris = OrisImpl()
    
    val tomId = oris.getUser(args[0])!!.id
    val userEventEntries = oris.getUserEventEntries(tomId)

    println("Found ${userEventEntries.size} user event entries for user $tomId")
    
    for (entry in userEventEntries)
    {
        println(entry)
    }
}
