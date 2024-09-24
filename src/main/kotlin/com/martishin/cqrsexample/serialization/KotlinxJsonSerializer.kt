package com.martishin.cqrsexample.serialization

import akka.serialization.SerializerWithStringManifest
import com.martishin.cqrsexample.models.actors.MakeReservation
import com.martishin.cqrsexample.models.actors.ReservationAccepted
import com.martishin.cqrsexample.models.actors.ReservationCanceled
import com.martishin.cqrsexample.models.actors.ReservationUpdated
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.LocalDate

class KotlinxJsonSerializer : SerializerWithStringManifest() {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            serializersModule =
                SerializersModule {
                    contextual(LocalDate::class, LocalDateSerializer)
                }
        }

    override fun identifier(): Int = 1234567

    override fun manifest(o: Any): String = o::class.qualifiedName ?: throw IllegalArgumentException("Unknown class: $o")

    override fun toBinary(o: Any): ByteArray =
        when (o) {
            is MakeReservation -> json.encodeToString(MakeReservation.serializer(), o).toByteArray()
            is ReservationAccepted -> json.encodeToString(ReservationAccepted.serializer(), o).toByteArray()
            is ReservationUpdated -> json.encodeToString(ReservationUpdated.serializer(), o).toByteArray()
            is ReservationCanceled -> json.encodeToString(ReservationCanceled.serializer(), o).toByteArray()
            else -> throw IllegalArgumentException("Unknown object type: ${o::class}")
        }

    override fun fromBinary(
        bytes: ByteArray,
        manifest: String,
    ): Any {
        val jsonString = String(bytes)
        return when (manifest) {
            "com.martishin.cqrsexample.models.actors.MakeReservation" ->
                json.decodeFromString(MakeReservation.serializer(), jsonString)
            "com.martishin.cqrsexample.models.actors.ReservationAccepted" ->
                json.decodeFromString(ReservationAccepted.serializer(), jsonString)
            "com.martishin.cqrsexample.models.actors.ReservationUpdated" ->
                json.decodeFromString(ReservationUpdated.serializer(), jsonString)
            "com.martishin.cqrsexample.models.actors.ReservationCanceled" ->
                json.decodeFromString(ReservationCanceled.serializer(), jsonString)
            else -> throw IllegalArgumentException("Unknown manifest: $manifest")
        }
    }
}
