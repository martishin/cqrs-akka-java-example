package com.martishin.cqrsexample.models.actors

import akka.actor.typed.ActorRef
import com.martishin.cqrsexample.models.domain.Reservation
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

// Commands
sealed interface Command

@Serializable
data class MakeReservation(
    val guestId: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val roomNumber: Int,
    val replyTo: ActorRef<HotelProtocol>,
) : Command

@Serializable
data class ChangeReservation(
    val confirmationNumber: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val roomNumber: Int,
    val replyTo: ActorRef<HotelProtocol>,
) : Command

@Serializable
data class CancelReservation(
    val confirmationNumber: String,
    val replyTo: ActorRef<HotelProtocol>,
) : Command

@Serializable
data class GetAllReservations(val replyTo: ActorRef<HotelProtocol>) : Command

@Serializable
data class GetReservation(val confirmationNumber: String, val replyTo: ActorRef<HotelProtocol>) : Command

@Serializable
data class AllReservations(val reservations: List<Reservation>) : HotelProtocol

// Events
sealed interface Event

@Serializable
data class ReservationAccepted(val reservation: Reservation) : Event, HotelProtocol

@Serializable
data class ReservationUpdated(
    val oldReservation: Reservation,
    val newReservation: Reservation,
) : Event, HotelProtocol

@Serializable
data class ReservationCanceled(val reservation: Reservation) : Event, HotelProtocol

// Communication with the "agent"
@Serializable
data class CommandFailure(val reason: String) : HotelProtocol

interface HotelProtocol

@Serializable
data class ManageHotel(val hotel: ActorRef<Command>) : HotelProtocol

@Serializable
data class Generate(val nCommands: Int) : HotelProtocol

@Serializable
data class ReservationFound(val reservation: Reservation) : HotelProtocol
