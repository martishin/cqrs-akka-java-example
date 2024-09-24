@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.martishin.cqrsexample.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.AskPattern
import com.martishin.cqrsexample.models.actors.AllReservations
import com.martishin.cqrsexample.models.actors.CancelReservation
import com.martishin.cqrsexample.models.actors.ChangeReservation
import com.martishin.cqrsexample.models.actors.Command
import com.martishin.cqrsexample.models.actors.CommandFailure
import com.martishin.cqrsexample.models.actors.GetAllReservations
import com.martishin.cqrsexample.models.actors.GetReservation
import com.martishin.cqrsexample.models.actors.HotelProtocol
import com.martishin.cqrsexample.models.actors.MakeReservation
import com.martishin.cqrsexample.models.actors.ReservationAccepted
import com.martishin.cqrsexample.models.actors.ReservationCanceled
import com.martishin.cqrsexample.models.actors.ReservationFound
import com.martishin.cqrsexample.models.actors.ReservationUpdated
import com.martishin.cqrsexample.models.dto.ChangeReservationRequest
import com.martishin.cqrsexample.models.dto.MakeReservationRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.future.await
import java.time.Duration

fun Route.reservationRoutes(hotelActorSystem: ActorSystem<Command>) {
    get("/reservations") {
        val reply: HotelProtocol =
            AskPattern
                .ask(
                    hotelActorSystem,
                    { replyTo: ActorRef<HotelProtocol> ->
                        GetAllReservations(replyTo)
                    },
                    Duration.ofSeconds(5),
                    hotelActorSystem.scheduler(),
                ).await()

        when (reply) {
            is AllReservations -> call.respond(HttpStatusCode.OK, reply.reservations)
            else -> call.respond(HttpStatusCode.InternalServerError, "Unable to fetch reservations")
        }
    }

    get("/reservations/{confirmationNumber}") {
        val confirmationNumber =
            call.parameters["confirmationNumber"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing confirmation number")

        val reply: HotelProtocol =
            AskPattern
                .ask(
                    hotelActorSystem,
                    { replyTo: ActorRef<HotelProtocol> ->
                        GetReservation(confirmationNumber, replyTo)
                    },
                    Duration.ofSeconds(5),
                    hotelActorSystem.scheduler(),
                ).await()

        when (reply) {
            is ReservationFound -> call.respond(HttpStatusCode.OK, reply.reservation)
            is CommandFailure -> call.respond(HttpStatusCode.NotFound, reply.reason)
            else -> call.respond(HttpStatusCode.InternalServerError, "Unknown error")
        }
    }

    route("/reservations") {
        post {
            val request = call.receive<MakeReservationRequest>()

            val reply: HotelProtocol =
                AskPattern
                    .ask(
                        hotelActorSystem,
                        { replyTo: ActorRef<HotelProtocol> ->
                            MakeReservation(
                                guestId = request.guestId,
                                startDate = request.startDate,
                                endDate = request.endDate,
                                roomNumber = request.roomNumber,
                                replyTo = replyTo,
                            )
                        },
                        Duration.ofSeconds(5),
                        hotelActorSystem.scheduler(),
                    ).await()

            when (reply) {
                is ReservationAccepted -> call.respond(HttpStatusCode.Created, reply)
                is CommandFailure -> call.respond(HttpStatusCode.BadRequest, reply.reason)
                else -> call.respond(HttpStatusCode.InternalServerError, "Unknown error")
            }
        }

        put("/{confirmationNumber}") {
            val confirmationNumber =
                call.parameters["confirmationNumber"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing confirmation number")

            val request = call.receive<ChangeReservationRequest>()

            val reply: HotelProtocol =
                AskPattern
                    .ask(
                        hotelActorSystem,
                        { replyTo: ActorRef<HotelProtocol> ->
                            ChangeReservation(
                                confirmationNumber = confirmationNumber,
                                startDate = request.startDate,
                                endDate = request.endDate,
                                roomNumber = request.roomNumber,
                                replyTo = replyTo,
                            )
                        },
                        Duration.ofSeconds(5),
                        hotelActorSystem.scheduler(),
                    ).await()

            when (reply) {
                is ReservationUpdated -> call.respond(HttpStatusCode.OK, reply)
                is CommandFailure -> call.respond(HttpStatusCode.BadRequest, reply.reason)
                else -> call.respond(HttpStatusCode.InternalServerError, "Unknown error")
            }
        }

        delete("/{confirmationNumber}") {
            val confirmationNumber =
                call.parameters["confirmationNumber"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing confirmation number")

            val reply: HotelProtocol =
                AskPattern
                    .ask(
                        hotelActorSystem,
                        { replyTo: ActorRef<HotelProtocol> ->
                            CancelReservation(
                                confirmationNumber = confirmationNumber,
                                replyTo = replyTo,
                            )
                        },
                        Duration.ofSeconds(5),
                        hotelActorSystem.scheduler(),
                    ).await()

            when (reply) {
                is ReservationCanceled -> call.respond(HttpStatusCode.OK, reply)
                is CommandFailure -> call.respond(HttpStatusCode.BadRequest, reply.reason)
                else -> call.respond(HttpStatusCode.InternalServerError, "Unknown error")
            }
        }
    }
}
