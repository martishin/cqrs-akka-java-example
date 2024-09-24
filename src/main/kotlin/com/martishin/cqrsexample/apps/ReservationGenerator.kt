@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.martishin.cqrsexample.apps

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import com.martishin.cqrsexample.actors.Hotel
import com.martishin.cqrsexample.actors.ReservationAgent
import com.martishin.cqrsexample.models.actors.Generate
import com.martishin.cqrsexample.models.actors.ManageHotel

object ReservationGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root: Behavior<Void> =
            Behaviors.setup { context ->
                val agent = context.spawn(ReservationAgent.create(), "agent")
                val hotels =
                    (1..100)
                        .map { i -> "hotel_$i" }
                        .map { hotelId -> context.spawn(Hotel.create(hotelId), hotelId) }
                hotels.forEach { hotel ->
                    agent.tell(ManageHotel(hotel))
                }

                (1..100).forEach { _ ->
                    agent.tell(Generate(10))
                    Thread.sleep(100)
                }

                Behaviors.empty()
            }

        val system: ActorSystem<Void> = ActorSystem.create(root, "ReservationGenerator")
    }
}
