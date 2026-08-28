package org.softtech.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link TicketCreatedEvent} domain event.
 */
class TicketCreatedEventTest {

    private static final Instant OCCURRED_ON = Instant.parse("2026-08-25T12:00:00Z");

    private Ticket ticket() {
        return Ticket.created(
                "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
                "TICK-2026-0001",
                "Title",
                "Description",
                Priority.HIGH,
                ErpModule.FINANCIAL,
                "USR-1",
                true,
                OCCURRED_ON
        );
    }

    @Test
    @DisplayName("Should construct event from ticket aggregate")
    void shouldConstructFromTicket() {
        Ticket ticket = ticket();
        TicketCreatedEvent event = TicketCreatedEvent.from(ticket, OCCURRED_ON);

        assertEquals(TicketCreatedEvent.EVENT_TYPE, event.eventType());
        assertEquals(ticket.getId(), event.ticketId());
        assertEquals(ticket.getTicketNumber(), event.ticketNumber());
        assertEquals(ticket.getTitle(), event.title());
        assertEquals(Priority.HIGH, event.priority());
        assertEquals(ErpModule.FINANCIAL, event.erpModule());
        assertEquals(ticket.getRequesterId(), event.requesterId());
        assertTrue(event.vipCustomer());
        assertEquals(ticket.getSlaPolicy().getResponseDeadline(), event.responseDeadline());
        assertEquals(ticket.getSlaPolicy().getResolutionDeadline(), event.resolutionDeadline());
        assertEquals(OCCURRED_ON, event.occurredOn());
        assertNotNull(event.eventId());
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> TicketCreatedEvent.from(null, OCCURRED_ON));
        assertThrows(NullPointerException.class, () -> TicketCreatedEvent.from(ticket(), null));
    }

    @Test
    @DisplayName("Should enforce non-null invariants via compact constructor")
    void shouldEnforceCompactConstructorInvariants() {
        TicketCreatedEvent valid = TicketCreatedEvent.from(ticket(), OCCURRED_ON);

        assertThrows(NullPointerException.class, () -> TicketCreatedEvent.builder()
                .eventType(valid.eventType()).ticketId(valid.ticketId()).ticketNumber(valid.ticketNumber())
                .title(valid.title()).priority(valid.priority()).erpModule(valid.erpModule())
                .requesterId(valid.requesterId()).responseDeadline(valid.responseDeadline())
                .resolutionDeadline(valid.resolutionDeadline()).occurredOn(valid.occurredOn())
                .build());
    }
}
