package org.softtech.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test suite for the {@link TicketNotFoundException} domain exception.
 */
class TicketNotFoundExceptionTest {

    @Test
    @DisplayName("Should carry error code, identifier and message")
    void shouldCarryErrorCodeAndIdentifier() {
        TicketNotFoundException exception = new TicketNotFoundException("TICK-2026-0001");

        assertEquals("HD-DOM-4040", exception.getErrorCode());
        assertEquals("TICK-2026-0001", exception.getTicketIdentifier());
        assertNotNull(exception.getTimestamp());
        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("Should expose static factories")
    void shouldExposeStaticFactories() {
        assertEquals("abc", TicketNotFoundException.forId("abc").getTicketIdentifier());
        assertEquals("TICK-1", TicketNotFoundException.forTicketNumber("TICK-1").getTicketIdentifier());
    }

    @Test
    @DisplayName("Should reject null identifier")
    void shouldRejectNullIdentifier() {
        assertThrows(NullPointerException.class, () -> new TicketNotFoundException(null));
    }
}
