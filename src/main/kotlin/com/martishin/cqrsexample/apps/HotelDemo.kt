package com.martishin.cqrsexample.apps

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import com.martishin.cqrsexample.actors.Hotel
import com.martishin.cqrsexample.models.actors.CancelReservation
import com.martishin.cqrsexample.models.actors.ChangeReservation
import com.martishin.cqrsexample.models.actors.HotelProtocol
import java.time.Duration
import java.time.LocalDate

object HotelDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val simpleLogger: Behavior<HotelProtocol> =
            Behaviors.receive { ctx, message ->
                ctx.log.info("[logger] $message")
                Behaviors.same()
            }

        val root: Behavior<Void> =
            Behaviors.setup { ctx ->
                val logger = ctx.spawn(simpleLogger, "logger") // child actor
                val hotel = ctx.spawn(Hotel.create("testHotel"), "testHotel")

                // Uncomment the following line to make a reservation
                // hotel.tell(MakeReservation(UUID.randomUUID().toString(), Date.valueOf("2022-07-14"), Date.valueOf("2022-07-21"), 101, logger))

                hotel.tell(
                    ChangeReservation(
                        "9B0KK6ABQR",
                        LocalDate.of(2022, 7, 14),
                        LocalDate.of(2022, 7, 28),
                        101,
                        logger,
                    ),
                )
                hotel.tell(CancelReservation("9B0KK6ABQR", logger))
                Behaviors.empty()
            }

        val system: ActorSystem<Void> = ActorSystem.create(root, "DemoHotel")
        system.scheduler().scheduleOnce(
            Duration.ofSeconds(30),
            { system.terminate() },
            system.executionContext(),
        )
    }
}
