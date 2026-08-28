package org.softtech.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.softtech.domain.model.TicketStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test suite for the {@link InvalidStatusTransitionException} domain exception.
 */
class InvalidStatusTransitionExceptionTest {

    @Test
    @DisplayName("Should carry transition context and error code")
    void shouldCarryTransitionContext() {
        InvalidStatusTransitionException exception = new InvalidStatusTransitionException(
                "TICK-2026-0001", TicketStatus.OPEN, TicketStatus.CLOSED);

        assertEquals("HD-DOM-4001", exception.getErrorCode());
        assertEquals("TICK-2026-0001", exception.getTicketNumber());
        assertEquals(TicketStatus.OPEN, exception.getCurrentStatus());
        assertEquals(TicketStatus.CLOSED, exception.getAttemptedStatus());
    }

    @Test
    @DisplayName("Should reject null parameters")
    void shouldRejectNullParameters() {
        assertThrows(NullPointerException.class, () ->
                new InvalidStatusTransitionException(null, TicketStatus.OPEN, TicketStatus.CLOSED));
        assertThrows(NullPointerException.class, () ->
                new InvalidStatusTransitionException("T", null, TicketStatus.CLOSED));
        assertThrows(NullPointerException.class, () ->
                new InvalidStatusTransitionException("T", TicketStatus.OPEN, null));
    }
}
