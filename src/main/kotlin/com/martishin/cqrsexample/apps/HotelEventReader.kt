package com.martishin.cqrsexample.apps

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.alpakka.cassandra.javadsl.CassandraSessionRegistry
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import com.martishin.cqrsexample.models.actors.ReservationAccepted
import com.martishin.cqrsexample.models.actors.ReservationCanceled
import com.martishin.cqrsexample.models.actors.ReservationUpdated
import com.martishin.cqrsexample.models.domain.Reservation
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

object HotelEventReader {
    @JvmStatic
    fun main(args: Array<String>) {
        val system: ActorSystem<Any> = ActorSystem.create(Behaviors.empty(), "HotelEventReaderSystem")
        val executionContext = system.executionContext()

        // Read journal
        val readJournal =
            PersistenceQuery.get(system)
                .getReadJournalFor(CassandraReadJournal::class.java, CassandraReadJournal.Identifier())

        // Cassandra session
        val session =
            CassandraSessionRegistry.get(system)
                .sessionFor("akka.projection.cassandra.session-config")

        // All persistence IDs
        val persistenceIds: Source<String, NotUsed> = readJournal.persistenceIds()
        val consumptionSink = Sink.foreach<String> { println(it) }
        val connectedGraph = persistenceIds.to(consumptionSink)

        // Define makeReservation function
        fun makeReservation(reservation: Reservation): CompletionStage<Void> {
            val guestId = reservation.guestId
            val hotelId = reservation.hotelId
            val startDate = reservation.startDate
            val endDate = reservation.endDate
            val roomNumber = reservation.roomNumber
            val confirmationNumber = reservation.confirmationNumber

            val daysBlocked = ChronoUnit.DAYS.between(startDate, endDate).toInt()

            val blockedDaysFutures =
                (0 until daysBlocked).map { day ->
                    val date = startDate.plusDays(day.toLong())
                    val query =
                        """
                        UPDATE hotel.available_rooms_by_hotel_date SET is_available = false WHERE 
                        hotel_id='$hotelId' and date='$date' and room_number=$roomNumber
                        """.trimIndent()
                    session.executeWrite(query)
                        .exceptionally { e ->
                            println("Room day blocking failed: $e")
                            null
                        }
                }

            val reservationGuestDateFuture =
                session.executeWrite(
                    """
                    INSERT INTO reservation.reservations_by_hotel_date 
                    (hotel_id, start_date, end_date, room_number, confirm_number, guest_id) VALUES 
                    ('$hotelId', '$startDate', '$endDate', $roomNumber, '$confirmationNumber', '$guestId')
                    """.trimIndent(),
                ).exceptionally { e ->
                    println("Reservation for date failed: $e")
                    null
                }

            val reservationGuestFuture =
                session.executeWrite(
                    """
                    INSERT INTO reservation.reservations_by_guest 
                    (guest_last_name, hotel_id, start_date, end_date, room_number, confirm_number, guest_id) VALUES 
                    ('ROCKTHEJVM', '$hotelId', '$startDate', '$endDate', $roomNumber, '$confirmationNumber', '$guestId')
                    """.trimIndent(),
                ).exceptionally { e ->
                    println("Reservation for guest failed: $e")
                    null
                }

            val allFutures = blockedDaysFutures + reservationGuestDateFuture + reservationGuestFuture
            val allCompletableFutures = allFutures.map { it.toCompletableFuture() }

            return CompletableFuture.allOf(*allCompletableFutures.toTypedArray())
                .thenAccept { }
        }

        // Define removeReservation function
        fun removeReservation(reservation: Reservation): CompletionStage<Void> {
            val guestId = reservation.guestId
            val hotelId = reservation.hotelId
            val startDate = reservation.startDate
            val endDate = reservation.endDate
            val roomNumber = reservation.roomNumber
            val confirmationNumber = reservation.confirmationNumber

            val daysBlocked = ChronoUnit.DAYS.between(startDate, endDate).toInt()

            val blockedDaysFutures =
                (0 until daysBlocked).map { day ->
                    val date = startDate.plusDays(day.toLong())
                    val query =
                        """
                        UPDATE hotel.available_rooms_by_hotel_date SET is_available = true WHERE 
                        hotel_id='$hotelId' and date='$date' and room_number=$roomNumber
                        """.trimIndent()
                    session.executeWrite(query)
                        .exceptionally { e ->
                            println("Room day unblocking failed: $e")
                            null
                        }
                }

            val reservationGuestDateFuture =
                session.executeWrite(
                    """
                    DELETE FROM reservation.reservations_by_hotel_date WHERE 
                    hotel_id='$hotelId' and start_date='$startDate' and room_number=$roomNumber
                    """.trimIndent(),
                ).exceptionally { e ->
                    println("Reservation removal for date failed: $e")
                    null
                }

            val reservationGuestFuture =
                session.executeWrite(
                    """
                    DELETE FROM reservation.reservations_by_guest WHERE 
                    guest_last_name='ROCKTHEJVM' and confirm_number='$confirmationNumber'
                    """.trimIndent(),
                ).exceptionally { e ->
                    println("Reservation removal for guest failed: $e")
                    null
                }

            val allFutures = blockedDaysFutures + reservationGuestDateFuture + reservationGuestFuture
            val allCompletableFutures = allFutures.map { it.toCompletableFuture() }

            return CompletableFuture.allOf(*allCompletableFutures.toTypedArray())
                .thenAccept { }
        }

        // All events for a persistence ID
        val eventsForTestHotel =
            readJournal
                .eventsByPersistenceId("hotel_82", 0, Long.MAX_VALUE)
                .map { it.event() }
                .mapAsync(8) { event ->
                    when (event) {
                        is ReservationAccepted -> {
                            println("MAKING RESERVATION: ${event.reservation}")
                            makeReservation(event.reservation)
                        }
                        is ReservationUpdated -> {
                            println("CHANGING RESERVATION: from ${event.oldReservation} to ${event.newReservation}")
                            removeReservation(event.oldReservation)
                                .thenCompose { makeReservation(event.newReservation) }
                        }
                        is ReservationCanceled -> {
                            println("CANCELLING RESERVATION: ${event.reservation}")
                            removeReservation(event.reservation)
                        }
                        else -> {
                            CompletableFuture.completedFuture(null)
                        }
                    }
                }

        // Run the stream
        eventsForTestHotel.runWith(Sink.ignore(), system)
    }
}
