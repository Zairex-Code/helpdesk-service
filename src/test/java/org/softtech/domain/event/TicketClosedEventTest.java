package org.softtech.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test suite for the {@link TicketClosedEvent} domain event.
 */
class TicketClosedEventTest {

    private static final Instant OCCURRED_ON = Instant.parse("2026-08-25T12:00:00Z");

    private Ticket closedTicket() {
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

        Ticket resolved = open
                .assignToAgent("AGT-1", OCCURRED_ON.plusSeconds(60))
                .startInvestigation(OCCURRED_ON.plusSeconds(120))
                .resolve("fix", OCCURRED_ON.plusSeconds(180));

        return resolved.closeWithFeedback(Feedback.of(5, "great", OCCURRED_ON.plusSeconds(240)), OCCURRED_ON.plusSeconds(240));
    }

    @Test
    @DisplayName("Should construct event from a closed ticket")
    void shouldConstructFromTicket() {
        Ticket ticket = closedTicket();
        TicketClosedEvent event = TicketClosedEvent.from(ticket, OCCURRED_ON.plusSeconds(240));

        assertEquals(TicketClosedEvent.EVENT_TYPE, event.eventType());
        assertEquals(ticket.getId(), event.ticketId());
        assertEquals(ticket.getTicketNumber(), event.ticketNumber());
        assertEquals(ticket.getRequesterId(), event.requesterId());
        assertEquals("AGT-1", event.assignedAgentId());
        assertEquals(Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()), event.totalLifecycleDuration());
        assertEquals(ticket.getResolvedAt(), event.resolvedAt());
        assertEquals(ticket.getClosedAt(), event.closedAt());
        assertEquals(ticket.getFeedback(), event.feedback());
        assertNotNull(event.eventId());
    }

    @Test
    @DisplayName("Should reject ticket without closedAt")
    void shouldRejectTicketWithoutClosedAt() {
        Ticket open = Ticket.created(
                "id", "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.FINANCIAL, "USR-1", false, OCCURRED_ON);
        assertThrows(IllegalStateException.class, () -> TicketClosedEvent.from(open, OCCURRED_ON));
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> TicketClosedEvent.from(null, OCCURRED_ON));
        assertThrows(NullPointerException.class, () -> TicketClosedEvent.from(closedTicket(), null));
    }
}
