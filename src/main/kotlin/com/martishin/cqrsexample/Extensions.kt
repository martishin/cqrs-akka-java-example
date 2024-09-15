@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.martishin.cqrsexample

import akka.actor.typed.ActorSystem
import com.martishin.cqrsexample.models.actors.Command
import com.martishin.cqrsexample.routes.reservationRoutes
import com.martishin.cqrsexample.serialization.LocalDateSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.LocalDate

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule =
                    SerializersModule {
                        contextual(LocalDate::class, LocalDateSerializer)
                    }
                prettyPrint = true
                ignoreUnknownKeys = true
            },
        )
    }
}

fun Application.configureRouting(hotelActorSystem: ActorSystem<Command>) {
    routing {
        reservationRoutes(hotelActorSystem)
    }
}
