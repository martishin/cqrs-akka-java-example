package com.martishin.cqrsexample.models.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class MakeReservationRequest(
    val guestId: String,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate,
    val roomNumber: Int,
)
