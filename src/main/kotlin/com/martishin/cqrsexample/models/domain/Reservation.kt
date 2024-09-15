package com.martishin.cqrsexample.models.domain

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Reservation(
    val guestId: String,
    val hotelId: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val roomNumber: Int,
    val confirmationNumber: String,
) {
    fun intersect(another: Reservation): Boolean {
        return this.hotelId == another.hotelId && this.roomNumber == another.roomNumber &&
            ((startDate in another.startDate..another.endDate) || (another.startDate in startDate..endDate))
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is Reservation -> this.confirmationNumber == other.confirmationNumber
            else -> false
        }
    }

    override fun hashCode(): Int {
        return confirmationNumber.hashCode()
    }

    companion object {
        fun make(
            guestId: String,
            hotelId: String,
            startDate: LocalDate,
            endDate: LocalDate,
            roomNumber: Int,
        ): Reservation {
            val chars = ('A'..'Z') + ('0'..'9')
            val confirmationNumber =
                (1..10)
                    .map { chars.random() }
                    .joinToString("")
            return Reservation(guestId, hotelId, startDate, endDate, roomNumber, confirmationNumber)
        }
    }
}
