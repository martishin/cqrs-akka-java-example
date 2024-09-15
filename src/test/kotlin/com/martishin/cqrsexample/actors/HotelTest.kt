package com.martishin.cqrsexample.actors

import akka.actor.testkit.typed.javadsl.ActorTestKit
import akka.actor.testkit.typed.javadsl.TestProbe
import akka.actor.typed.ActorRef
import com.martishin.cqrsexample.models.actors.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HotelTest {
    private lateinit var testKit: ActorTestKit
    private lateinit var hotelActor: ActorRef<Command>

    @BeforeEach
    fun setUp() {
        // Initialize the test environment
        testKit = ActorTestKit.create("application-test.conf")
        hotelActor = testKit.spawn(Hotel.create("hotel_1"))
    }

    @AfterEach
    fun tearDown() {
        // Shutdown the test kit to clean up resources after each test
        testKit.shutdownTestKit()
    }

    @Test
    fun `should accept a new reservation`() {
        val probe: TestProbe<HotelProtocol> = testKit.createTestProbe()

        val reservationCommand =
            MakeReservation(
                guestId = "guest123",
                startDate = LocalDate.of(2023, 11, 1),
                endDate = LocalDate.of(2023, 11, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )

        hotelActor.tell(reservationCommand)

        val response = probe.expectMessageClass(ReservationAccepted::class.java)
        assertEquals("guest123", response.reservation.guestId)
    }

    @Test
    fun `should not accept a conflicting reservation`() {
        val probe: TestProbe<HotelProtocol> = testKit.createTestProbe()

        // Make the first reservation
        val firstReservation =
            MakeReservation(
                guestId = "guest123",
                startDate = LocalDate.of(2023, 11, 1),
                endDate = LocalDate.of(2023, 11, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )
        hotelActor.tell(firstReservation)
        probe.expectMessageClass(ReservationAccepted::class.java)

        // Attempt a conflicting reservation (same room, same dates)
        val conflictingReservation =
            MakeReservation(
                guestId = "guest456",
                startDate = LocalDate.of(2023, 11, 1),
                endDate = LocalDate.of(2023, 11, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )
        hotelActor.tell(conflictingReservation)

        // Assert that the actor replies with a failure due to conflict
        val response = probe.expectMessageClass(CommandFailure::class.java)
        assertTrue(response.reason.contains("conflict with another reservation"))
    }

    @Test
    fun `should update an existing reservation`() {
        val probe: TestProbe<HotelProtocol> = testKit.createTestProbe()

        // Make the first reservation
        val firstReservation =
            MakeReservation(
                guestId = "guest123",
                startDate = LocalDate.of(2023, 11, 1),
                endDate = LocalDate.of(2023, 11, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )
        hotelActor.tell(firstReservation)
        val acceptedReservation = probe.expectMessageClass(ReservationAccepted::class.java)

        // Update the reservation
        val updateReservation =
            ChangeReservation(
                confirmationNumber = acceptedReservation.reservation.confirmationNumber,
                startDate = LocalDate.of(2023, 12, 1),
                endDate = LocalDate.of(2023, 12, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )
        hotelActor.tell(updateReservation)

        // Assert that the actor replies with ReservationUpdated
        val response = probe.expectMessageClass(ReservationUpdated::class.java)
        assertEquals(LocalDate.of(2023, 12, 1), response.newReservation.startDate)
    }

    @Test
    fun `should return all reservations`() {
        val probe: TestProbe<HotelProtocol> = testKit.createTestProbe()

        // Make a reservation
        val reservation =
            MakeReservation(
                guestId = "guest123",
                startDate = LocalDate.of(2023, 11, 1),
                endDate = LocalDate.of(2023, 11, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )
        hotelActor.tell(reservation)
        probe.expectMessageClass(ReservationAccepted::class.java)

        // Request all reservations
        hotelActor.tell(GetAllReservations(probe.ref))

        // Assert that the actor replies with the list of all reservations
        val allReservations = probe.expectMessageClass(AllReservations::class.java)
        assertEquals(1, allReservations.reservations.size)
    }

    @Test
    fun `should return a single reservation`() {
        val probe: TestProbe<HotelProtocol> = testKit.createTestProbe()

        // Make a reservation
        val reservation =
            MakeReservation(
                guestId = "guest123",
                startDate = LocalDate.of(2023, 11, 1),
                endDate = LocalDate.of(2023, 11, 5),
                roomNumber = 101,
                replyTo = probe.ref,
            )
        hotelActor.tell(reservation)
        val acceptedReservation = probe.expectMessageClass(ReservationAccepted::class.java)

        // Request the reservation
        hotelActor.tell(GetReservation(acceptedReservation.reservation.confirmationNumber, probe.ref))

        // Assert that the actor replies with ReservationFound
        val reservationFound = probe.expectMessageClass(ReservationFound::class.java)
        assertEquals("guest123", reservationFound.reservation.guestId)
    }
}
