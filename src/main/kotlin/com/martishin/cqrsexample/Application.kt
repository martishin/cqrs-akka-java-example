@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.martishin.cqrsexample

import akka.actor.typed.ActorSystem
import com.martishin.cqrsexample.actors.Hotel
import com.martishin.cqrsexample.models.actors.Command
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val hotelId = "hotel_1"
    val hotelActorSystem: ActorSystem<Command> = ActorSystem.create(Hotel.create(hotelId), "HotelSystem")

    embeddedServer(Netty, port = 8080) {
        module(hotelActorSystem)
    }.start(wait = true)
}

fun Application.module(hotelActorSystem: ActorSystem<Command>) {
    configureSerialization()
    configureRouting(hotelActorSystem)
}
