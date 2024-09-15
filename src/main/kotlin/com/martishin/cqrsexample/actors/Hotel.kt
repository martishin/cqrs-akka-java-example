package com.martishin.cqrsexample.actors

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandlerWithReply
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies
import com.martishin.cqrsexample.models.actors.AllReservations
import com.martishin.cqrsexample.models.actors.CancelReservation
import com.martishin.cqrsexample.models.actors.ChangeReservation
import com.martishin.cqrsexample.models.actors.Command
import com.martishin.cqrsexample.models.actors.CommandFailure
import com.martishin.cqrsexample.models.actors.Event
import com.martishin.cqrsexample.models.actors.GetAllReservations
import com.martishin.cqrsexample.models.actors.GetReservation
import com.martishin.cqrsexample.models.actors.MakeReservation
import com.martishin.cqrsexample.models.actors.ReservationAccepted
import com.martishin.cqrsexample.models.actors.ReservationCanceled
import com.martishin.cqrsexample.models.actors.ReservationFound
import com.martishin.cqrsexample.models.actors.ReservationUpdated
import com.martishin.cqrsexample.models.domain.Reservation

class Hotel(
    private val hotelId: String,
) : EventSourcedBehaviorWithEnforcedReplies<Command, Event, Hotel.State>(
        PersistenceId.ofUniqueId(hotelId),
    ) {
    data class State(val reservations: Set<Reservation> = emptySet())

    override fun emptyState(): State = State()

    override fun commandHandler(): CommandHandlerWithReply<Command, Event, State> {
        val builder = newCommandHandlerWithReplyBuilder()

        builder.forAnyState()
            .onCommand(MakeReservation::class.java) { state, command ->
                val tentativeReservation =
                    Reservation.make(
                        command.guestId,
                        hotelId,
                        command.startDate,
                        command.endDate,
                        command.roomNumber,
                    )
                val conflictingReservation = state.reservations.find { it.intersect(tentativeReservation) }

                if (conflictingReservation == null) {
                    Effect().persist(ReservationAccepted(tentativeReservation))
                        .thenReply(command.replyTo) { _: State -> ReservationAccepted(tentativeReservation) }
                } else {
                    Effect().reply(
                        command.replyTo,
                        CommandFailure("Reservation failed: conflict with another reservation"),
                    )
                }
            }
            .onCommand(ChangeReservation::class.java) { state, command ->
                val oldReservation = state.reservations.find { it.confirmationNumber == command.confirmationNumber }
                val newReservation =
                    oldReservation?.copy(
                        startDate = command.startDate,
                        endDate = command.endDate,
                        roomNumber = command.roomNumber,
                    )
                val reservationUpdatedEvent =
                    if (oldReservation != null && newReservation != null) {
                        ReservationUpdated(oldReservation, newReservation)
                    } else {
                        null
                    }
                val conflictingReservation =
                    newReservation?.let { tentativeReservation ->
                        state.reservations.find {
                            it.confirmationNumber != command.confirmationNumber && it.intersect(tentativeReservation)
                        }
                    }

                when {
                    reservationUpdatedEvent == null ->
                        Effect().reply(
                            command.replyTo,
                            CommandFailure("Cannot update reservation ${command.confirmationNumber}: not found"),
                        )
                    conflictingReservation != null ->
                        Effect().reply(
                            command.replyTo,
                            CommandFailure("Cannot update reservation ${command.confirmationNumber}: conflicting reservations"),
                        )
                    else ->
                        Effect().persist(reservationUpdatedEvent)
                            .thenReply(command.replyTo) { _: State -> reservationUpdatedEvent }
                }
            }
            .onCommand(CancelReservation::class.java) { state, command ->
                val reservation = state.reservations.find { it.confirmationNumber == command.confirmationNumber }
                if (reservation != null) {
                    Effect().persist(ReservationCanceled(reservation))
                        .thenReply(command.replyTo) { _: State -> ReservationCanceled(reservation) }
                } else {
                    Effect().reply(
                        command.replyTo,
                        CommandFailure("Cannot cancel reservation ${command.confirmationNumber}: not found"),
                    )
                }
            }
            .onCommand(GetAllReservations::class.java) { state, command ->
                Effect().reply(command.replyTo, AllReservations(state.reservations.toList()))
            }
            .onCommand(GetReservation::class.java) { state, command ->
                val reservation = state.reservations.find { it.confirmationNumber == command.confirmationNumber }
                if (reservation != null) {
                    Effect().reply(command.replyTo, ReservationFound(reservation))
                } else {
                    Effect().reply(command.replyTo, CommandFailure("Reservation not found"))
                }
            }
            .onAnyCommand { _, _ ->
                Effect().none().thenNoReply()
            }

        return builder.build()
    }

    override fun eventHandler(): EventHandler<State, Event> {
        val builder = newEventHandlerBuilder()

        builder.forAnyState()
            .onEvent(ReservationAccepted::class.java) { state, event ->
                val newState = state.copy(reservations = state.reservations + event.reservation)
                println("state changed: $newState")
                newState
            }
            .onEvent(ReservationUpdated::class.java) { state, event ->
                val newState =
                    state.copy(
                        reservations = state.reservations - event.oldReservation + event.newReservation,
                    )
                println("state changed: $newState")
                newState
            }
            .onEvent(ReservationCanceled::class.java) { state, event ->
                val newState = state.copy(reservations = state.reservations - event.reservation)
                println("state changed: $newState")
                newState
            }

        return builder.build()
    }

    companion object {
        fun create(hotelId: String): Behavior<Command> = Hotel(hotelId)
    }
}
