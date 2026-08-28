package org.softtech.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test suite for the {@link TicketStatusChangedEvent} domain event.
 */
class TicketStatusChangedEventTest {

    private static final Instant OCCURRED_ON = Instant.parse("2026-08-25T12:00:00Z");

    private Ticket assignedTicket() {
        Ticket open = Ticket.created(
                "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
                "TICK-2026-0001",
                "Title",
                "Description",
                Priority.HIGH,
                ErpModule.FINANCIAL,
                "USR-1",
                false,
                OCCURRED_ON
        );
        return open.assignToAgent("AGT-1", OCCURRED_ON.plusSeconds(60));
    }

    @Test
    @DisplayName("Should construct event with previous and new status")
    void shouldConstructFromTicket() {
        Ticket ticket = assignedTicket();
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(
                ticket, TicketStatus.OPEN, "Assigned to specialist AGT-1", OCCURRED_ON.plusSeconds(60));

        assertEquals(TicketStatusChangedEvent.EVENT_TYPE, event.eventType());
        assertEquals(ticket.getId(), event.ticketId());
        assertEquals(ticket.getTicketNumber(), event.ticketNumber());
        assertEquals(TicketStatus.OPEN, event.previousStatus());
        assertEquals(TicketStatus.ASSIGNED, event.newStatus());
        assertEquals("AGT-1", event.assignedAgentId());
        assertEquals("Assigned to specialist AGT-1", event.reason());
        assertEquals(OCCURRED_ON.plusSeconds(60), event.occurredOn());
        assertNotNull(event.eventId());
    }

    @Test
    @DisplayName("Should trim null reason to null")
    void shouldHandleNullReason() {
        Ticket ticket = assignedTicket();
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(ticket, TicketStatus.OPEN, null, OCCURRED_ON);
        assertNull(event.reason());
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThrows(NullPointerException.class, () ->
                TicketStatusChangedEvent.from(null, TicketStatus.OPEN, "r", OCCURRED_ON));
        assertThrows(NullPointerException.class, () ->
                TicketStatusChangedEvent.from(assignedTicket(), null, "r", OCCURRED_ON));
        assertThrows(NullPointerException.class, () ->
                TicketStatusChangedEvent.from(assignedTicket(), TicketStatus.OPEN, "r", null));
    }
}
