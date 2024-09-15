package com.martishin.cqrsexample.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import com.martishin.cqrsexample.models.actors.CancelReservation
import com.martishin.cqrsexample.models.actors.ChangeReservation
import com.martishin.cqrsexample.models.actors.Command
import com.martishin.cqrsexample.models.actors.Generate
import com.martishin.cqrsexample.models.actors.HotelProtocol
import com.martishin.cqrsexample.models.actors.MakeReservation
import com.martishin.cqrsexample.models.actors.ManageHotel
import com.martishin.cqrsexample.models.actors.ReservationAccepted
import com.martishin.cqrsexample.models.actors.ReservationCanceled
import com.martishin.cqrsexample.models.actors.ReservationUpdated
import com.martishin.cqrsexample.models.domain.Reservation
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

object ReservationAgent {
    private const val DELETE_PROB = 0.05
    private const val CHANGE_PROB = 0.2
    private val START_DATE: LocalDate = LocalDate.of(2023, 1, 1)

    /**
     * Generates and sends commands to hotel actors based on probabilities.
     */
    private fun generateAndSend(
        hotels: List<ActorRef<Command>>,
        state: Map<String, Reservation>,
        replyTo: ActorRef<HotelProtocol>,
    ) {
        val prob = Random.nextDouble()
        if (prob <= DELETE_PROB && state.isNotEmpty()) {
            // Generate cancellation
            val confNumbers = state.keys.toList()
            val confNumber = confNumbers.random()
            val reservation = state[confNumber]!!
            val hotel = hotels.find { it.path().name() == reservation.hotelId }
            hotel?.tell(CancelReservation(confNumber, replyTo))
        } else if (prob <= CHANGE_PROB && state.isNotEmpty()) {
            // Generate a reservation change
            val confNumbers = state.keys.toList()
            val confNumber = confNumbers.random()
            val reservation = state[confNumber]!!

            val guestId = reservation.guestId
            val hotelId = reservation.hotelId
            val startDate = reservation.startDate
            val endDate = reservation.endDate
            val roomNumber = reservation.roomNumber
            val confirmationNumber = reservation.confirmationNumber

            val isDurationChange = Random.nextBoolean()
            val newReservation =
                if (isDurationChange) {
                    // Change duration
                    val newLocalStart = startDate.plusDays((Random.nextInt(5) - 2).toLong())
                    val tentativeLocalEnd = endDate.plusDays((Random.nextInt(5) - 2).toLong())
                    val newLocalEnd =
                        if (!tentativeLocalEnd.isAfter(newLocalStart)) {
                            newLocalStart.plusDays((Random.nextInt(5) + 1).toLong())
                        } else {
                            tentativeLocalEnd
                        }

                    Reservation(
                        guestId,
                        hotelId,
                        newLocalStart,
                        newLocalEnd,
                        roomNumber,
                        confirmationNumber,
                    )
                } else {
                    // Change room number
                    val newRoomNumber = Random.nextInt(1, 101)
                    Reservation(
                        guestId,
                        hotelId,
                        startDate,
                        endDate,
                        newRoomNumber,
                        confirmationNumber,
                    )
                }

            val hotel = hotels.find { it.path().name() == hotelId }
            hotel?.tell(
                ChangeReservation(
                    confirmationNumber,
                    newReservation.startDate,
                    newReservation.endDate,
                    newReservation.roomNumber,
                    replyTo,
                ),
            )
        } else {
            // Generate new reservation
            if (hotels.isNotEmpty()) {
                val hotel = hotels.random()
                val startDate = START_DATE.plusDays(Random.nextLong(0, 365))
                val endDate = startDate.plusDays(Random.nextLong(1, 14))
                val roomNumber = Random.nextInt(1, 101)
                hotel.tell(
                    MakeReservation(
                        UUID.randomUUID().toString(),
                        startDate,
                        endDate,
                        roomNumber,
                        replyTo,
                    ),
                )
            }
        }
    }

    /**
     * Actor behavior handling hotel protocol messages.
     */
    private fun active(
        hotels: List<ActorRef<Command>>,
        state: Map<String, Reservation>,
    ): Behavior<HotelProtocol> {
        return Behaviors.receive { context, message ->
            val self: ActorRef<HotelProtocol> = context.self

            when (message) {
                is Generate -> {
                    repeat(message.nCommands) {
                        generateAndSend(hotels, state, self)
                    }
                    Behaviors.same()
                }
                is ManageHotel -> {
                    context.log.info("Managing hotel ${message.hotel.path().name()}")
                    val newHotels = hotels + message.hotel
                    active(newHotels, state)
                }
                is ReservationAccepted -> {
                    context.log.info("Reservation accepted: ${message.reservation.confirmationNumber}")
                    val newState = state + (message.reservation.confirmationNumber to message.reservation)
                    active(hotels, newState)
                }
                is ReservationUpdated -> {
                    context.log.info("Reservation updated: ${message.newReservation.confirmationNumber}")
                    val newState = state + (message.newReservation.confirmationNumber to message.newReservation)
                    active(hotels, newState)
                }
                is ReservationCanceled -> {
                    context.log.info("Reservation cancelled: ${message.reservation.confirmationNumber}")
                    val newState = state - message.reservation.confirmationNumber
                    active(hotels, newState)
                }
                else -> {
                    Behaviors.same()
                }
            }
        }
    }

    /**
     * Creates the ReservationAgent behavior.
     */
    fun create(): Behavior<HotelProtocol> = active(emptyList(), emptyMap())
}
