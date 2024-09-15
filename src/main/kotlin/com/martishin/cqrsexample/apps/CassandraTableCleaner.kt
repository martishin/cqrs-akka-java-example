package com.martishin.cqrsexample.apps

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.stream.alpakka.cassandra.javadsl.CassandraSessionRegistry
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

object CassandraTableCleaner {
    private val system: ActorSystem<Void> = ActorSystem.create(Behaviors.empty<Void>(), "CassandraSystem")
    private val session = CassandraSessionRegistry.get(system).sessionFor("akka.projection.cassandra.session-config")

    private fun clearTables() =
        runBlocking {
            val dates = (0 until 365).map { LocalDate.of(2023, 1, 1).plusDays(it.toLong()) }
            val hotelIds = (1..100).map { "hotel_$it" }
            val roomNumbers = 1..100

            val daysFree =
                Source.from(
                    hotelIds.flatMap { hotelId ->
                        roomNumbers.flatMap { roomNumber ->
                            dates.map { date ->
                                Triple(hotelId, roomNumber, date)
                            }
                        }
                    },
                )

            val clearRoomsDeferred =
                daysFree
                    .mapAsync(8) { (hotelId, roomNumber, day) ->
                        val query =
                            """
                            INSERT INTO hotel.available_rooms_by_hotel_date 
                            (hotel_id, date, room_number, is_available) 
                            VALUES ('$hotelId', '$day', $roomNumber, true)
                            """.trimIndent()
                        session.executeWrite(query)
                    }
                    .runWith(Sink.ignore(), system)
                    .asDeferred()

            val truncates =
                listOf(
                    "TRUNCATE akka.all_persistence_ids",
                    "TRUNCATE akka.messages",
                    "TRUNCATE akka.metadata",
                    "TRUNCATE akka.tag_scanning",
                    "TRUNCATE akka.tag_views",
                    "TRUNCATE akka.tag_write_progress",
                    "TRUNCATE akka_snapshot.snapshots",
                    "TRUNCATE reservation.reservations_by_hotel_date",
                    "TRUNCATE reservation.reservations_by_guest",
                )

            val truncateDeferreds =
                truncates.map { query ->
                    session.executeWrite(query).asDeferred()
                }

            try {
                awaitAll(clearRoomsDeferred, *truncateDeferreds.toTypedArray())
                println("All tables cleared")
            } catch (e: Exception) {
                println("Failed clearing tables: $e")
            } finally {
                system.terminate()
            }
        }

    @JvmStatic
    fun main(args: Array<String>) {
        clearTables()
    }
}
