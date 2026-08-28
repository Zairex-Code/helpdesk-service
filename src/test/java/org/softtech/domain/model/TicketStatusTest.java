package org.softtech.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link TicketStatus} finite-state machine enumeration.
 */
class TicketStatusTest {

    @Test
    @DisplayName("Should allow valid transitions")
    void shouldAllowValidTransitions() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.ASSIGNED));
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.CANCELLED));

        assertTrue(TicketStatus.ASSIGNED.canTransitionTo(TicketStatus.IN_PROGRESS));
        assertTrue(TicketStatus.ASSIGNED.canTransitionTo(TicketStatus.OPEN));
        assertTrue(TicketStatus.ASSIGNED.canTransitionTo(TicketStatus.CANCELLED));

        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.RESOLVED));
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.ASSIGNED));
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.CANCELLED));

        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED));
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("Should reject invalid and null transitions")
    void shouldRejectInvalidTransitions() {
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED));
        assertFalse(TicketStatus.ASSIGNED.canTransitionTo(TicketStatus.RESOLVED));
        assertFalse(TicketStatus.CLOSED.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.CANCELLED.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.OPEN.canTransitionTo(null));
    }

    @Test
    @DisplayName("Should identify terminal states")
    void shouldIdentifyTerminalStates() {
        assertTrue(TicketStatus.CLOSED.isTerminal());
        assertTrue(TicketStatus.CANCELLED.isTerminal());
        assertFalse(TicketStatus.OPEN.isTerminal());
        assertFalse(TicketStatus.ASSIGNED.isTerminal());
        assertFalse(TicketStatus.IN_PROGRESS.isTerminal());
        assertFalse(TicketStatus.RESOLVED.isTerminal());
    }

    @Test
    @DisplayName("Should identify active states")
    void shouldIdentifyActiveStates() {
        assertTrue(TicketStatus.OPEN.isActive());
        assertTrue(TicketStatus.ASSIGNED.isActive());
        assertTrue(TicketStatus.IN_PROGRESS.isActive());
        assertFalse(TicketStatus.RESOLVED.isActive());
        assertFalse(TicketStatus.CLOSED.isActive());
        assertFalse(TicketStatus.CANCELLED.isActive());
    }
}
