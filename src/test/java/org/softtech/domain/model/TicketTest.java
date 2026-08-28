package org.softtech.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.softtech.domain.exception.InvalidStatusTransitionException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link Ticket} aggregate root and its deterministic state machine.
 */
class TicketTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T12:00:00Z");

    private Ticket openTicket() {
        return Ticket.created(
                "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
                "TICK-2026-0001",
                "Database timeout in payroll batch",
                "PostgreSQL deadlock detected during concurrent payroll execution.",
                Priority.HIGH,
                ErpModule.HUMAN_RESOURCES,
                "USR-CORP-98421",
                false,
                CREATED_AT
        );
    }

    @Nested
    @DisplayName("Factory method Ticket.created")
    class FactoryMethod {

        @Test
        @DisplayName("Should initialize ticket in OPEN status with calculated SLA and immutable notes")
        void shouldInitializeTicketInOpenStatus() {
            Ticket ticket = openTicket();

            assertEquals(TicketStatus.OPEN, ticket.getStatus());
            assertEquals("TICK-2026-0001", ticket.getTicketNumber());
            assertEquals("Database timeout in payroll batch", ticket.getTitle());
            assertEquals(Priority.HIGH, ticket.getPriority());
            assertEquals(ErpModule.HUMAN_RESOURCES, ticket.getErpModule());
            assertNull(ticket.getAssignedAgentId());
            assertNull(ticket.getFeedback());
            assertNull(ticket.getFirstResponseAt());
            assertNull(ticket.getResolvedAt());
            assertNull(ticket.getClosedAt());
            assertNotNullSlaPolicy(ticket);
            assertTrue(ticket.getNotes().isEmpty());
            assertEquals(CREATED_AT, ticket.getCreatedAt());
            assertEquals(CREATED_AT, ticket.getUpdatedAt());
        }

        @Test
        @DisplayName("Should trim string fields on creation")
        void shouldTrimStringFields() {
            Ticket ticket = Ticket.created(
                    "id-1",
                    "  TICK-2026-0002  ",
                    "  Title  ",
                    "  Description  ",
                    Priority.LOW,
                    ErpModule.CRM,
                    "  USR-1  ",
                    false,
                    CREATED_AT
            );

            assertEquals("TICK-2026-0002", ticket.getTicketNumber());
            assertEquals("Title", ticket.getTitle());
            assertEquals("Description", ticket.getDescription());
            assertEquals("USR-1", ticket.getRequesterId());
        }

        @Test
        @DisplayName("Should throw NullPointerException when mandatory fields are null")
        void shouldThrowNullPointerExceptionWhenFieldsAreNull() {
            assertThrows(NullPointerException.class, () ->
                    Ticket.created(null, "T", "title", "desc", Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", null, "title", "desc", Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", "T", null, "desc", Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", "T", "title", null, Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", "T", "title", "desc", null, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", "T", "title", "desc", Priority.LOW, null, "r", false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", "T", "title", "desc", Priority.LOW, ErpModule.CRM, null, false, CREATED_AT));
            assertThrows(NullPointerException.class, () ->
                    Ticket.created("id", "T", "title", "desc", Priority.LOW, ErpModule.CRM, "r", false, null));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when string fields are blank")
        void shouldThrowIllegalArgumentExceptionWhenFieldsAreBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    Ticket.created("id", "   ", "title", "desc", Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(IllegalArgumentException.class, () ->
                    Ticket.created("id", "T", "   ", "desc", Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(IllegalArgumentException.class, () ->
                    Ticket.created("id", "T", "title", "   ", Priority.LOW, ErpModule.CRM, "r", false, CREATED_AT));
            assertThrows(IllegalArgumentException.class, () ->
                    Ticket.created("id", "T", "title", "desc", Priority.LOW, ErpModule.CRM, "   ", false, CREATED_AT));
        }
    }

    @Nested
    @DisplayName("Finite State Machine transitions")
    class StateMachine {

        @Test
        @DisplayName("Should traverse the nominal lifecycle OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED")
        void shouldTraverseNominalLifecycle() {
            Instant t1 = CREATED_AT.plusSeconds(60);
            Instant t2 = CREATED_AT.plusSeconds(120);
            Instant t3 = CREATED_AT.plusSeconds(180);
            Instant t4 = CREATED_AT.plusSeconds(240);

            Ticket assigned = openTicket().assignToAgent("AGT-1", t1);
            assertEquals(TicketStatus.ASSIGNED, assigned.getStatus());
            assertEquals("AGT-1", assigned.getAssignedAgentId());
            assertEquals(t1, assigned.getFirstResponseAt());

            Ticket inProgress = assigned.startInvestigation(t2);
            assertEquals(TicketStatus.IN_PROGRESS, inProgress.getStatus());

            Ticket resolved = inProgress.resolve("Applied fix", t3);
            assertEquals(TicketStatus.RESOLVED, resolved.getStatus());
            assertEquals(t3, resolved.getResolvedAt());

            Feedback feedback = Feedback.of(5, "Great", t4);
            Ticket closed = resolved.closeWithFeedback(feedback, t4);
            assertEquals(TicketStatus.CLOSED, closed.getStatus());
            assertEquals(t4, closed.getClosedAt());
            assertEquals(feedback, closed.getFeedback());
        }

        @Test
        @DisplayName("Should reject illegal transitions OPEN -> CLOSED and OPEN -> RESOLVED")
        void shouldRejectIllegalTransitions() {
            assertThrows(InvalidStatusTransitionException.class, () -> openTicket().resolve("fix", CREATED_AT));
            assertThrows(InvalidStatusTransitionException.class, () ->
                    openTicket().closeWithFeedback(null, CREATED_AT));
        }

        @Test
        @DisplayName("Should reject terminal transitions from CLOSED and CANCELLED")
        void shouldRejectTerminalTransitions() {
            Ticket closed = openTicket()
                    .assignToAgent("AGT-1", CREATED_AT.plusSeconds(1))
                    .startInvestigation(CREATED_AT.plusSeconds(2))
                    .resolve("fix", CREATED_AT.plusSeconds(3))
                    .closeWithFeedback(null, CREATED_AT.plusSeconds(4));

            assertThrows(InvalidStatusTransitionException.class, () -> closed.assignToAgent("AGT-2", CREATED_AT.plusSeconds(5)));
            assertThrows(InvalidStatusTransitionException.class, () -> closed.resolve("fix", CREATED_AT.plusSeconds(5)));

            Ticket cancelled = openTicket().cancel("duplicate", CREATED_AT.plusSeconds(5));
            assertThrows(InvalidStatusTransitionException.class, () -> cancelled.startInvestigation(CREATED_AT.plusSeconds(6)));
        }

        @Test
        @DisplayName("Should reject reassigning an already ASSIGNED ticket")
        void shouldRejectReassign() {
            Ticket assigned = openTicket().assignToAgent("AGT-1", CREATED_AT.plusSeconds(1));
            assertThrows(InvalidStatusTransitionException.class, () -> assigned.assignToAgent("AGT-2", CREATED_AT.plusSeconds(2)));
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("Should return independent instances without mutating the origin")
        void shouldNotMutateOrigin() {
            Ticket origin = openTicket();
            Ticket assigned = origin.assignToAgent("AGT-1", CREATED_AT.plusSeconds(60));

            assertNotSame(origin, assigned);
            assertEquals(TicketStatus.OPEN, origin.getStatus());
            assertEquals(TicketStatus.ASSIGNED, assigned.getStatus());
            assertNull(origin.getAssignedAgentId());
            assertEquals("AGT-1", assigned.getAssignedAgentId());
            assertTrue(origin.getNotes().isEmpty());
            assertEquals(1, assigned.getNotes().size());
        }

        @Test
        @DisplayName("Should return unmodifiable notes list")
        void shouldReturnUnmodifiableNotes() {
            Ticket ticket = openTicket().assignToAgent("AGT-1", CREATED_AT.plusSeconds(60));
            assertThrows(UnsupportedOperationException.class, () -> ticket.getNotes().add("boom"));
        }
    }

    @Nested
    @DisplayName("Other domain operations")
    class OtherOperations {

        @Test
        @DisplayName("Should cancel an OPEN ticket into CANCELLED terminal state")
        void shouldCancelOpenTicket() {
            Instant cancelledAt = CREATED_AT.plusSeconds(60);
            Ticket cancelled = openTicket().cancel("duplicate", cancelledAt);

            assertEquals(TicketStatus.CANCELLED, cancelled.getStatus());
            assertEquals(cancelledAt, cancelled.getUpdatedAt());
        }

        @Test
        @DisplayName("Should reject cancel with blank reason")
        void shouldRejectBlankCancelReason() {
            assertThrows(IllegalArgumentException.class, () -> openTicket().cancel("  ", CREATED_AT));
            assertThrows(NullPointerException.class, () -> openTicket().cancel(null, CREATED_AT));
        }

        @Test
        @DisplayName("Should append an internal note without changing status")
        void shouldAddInternalNote() {
            Ticket ticket = openTicket().addInternalNote("diagnostic", CREATED_AT.plusSeconds(60));
            assertEquals(TicketStatus.OPEN, ticket.getStatus());
            assertEquals(1, ticket.getNotes().size());
            assertTrue(ticket.getNotes().get(0).contains("diagnostic"));
        }

        @Test
        @DisplayName("Should reject blank internal notes")
        void shouldRejectBlankInternalNote() {
            assertThrows(IllegalArgumentException.class, () -> openTicket().addInternalNote(" ", CREATED_AT));
        }

        @Test
        @DisplayName("Should reject resolve with null or blank notes")
        void shouldRejectInvalidResolutionNotes() {
            Ticket inProgress = openTicket()
                    .assignToAgent("AGT-1", CREATED_AT.plusSeconds(1))
                    .startInvestigation(CREATED_AT.plusSeconds(2));

            assertThrows(NullPointerException.class, () -> inProgress.resolve(null, CREATED_AT.plusSeconds(3)));
            assertThrows(IllegalArgumentException.class, () -> inProgress.resolve("  ", CREATED_AT.plusSeconds(3)));
        }
    }

    @Nested
    @DisplayName("SLA breach evaluation")
    class SlaBreach {

        @Test
        @DisplayName("Should report first response breach when firstResponseAt exceeds deadline")
        void shouldEvaluateFirstResponseBreach() {
            Ticket ticket = openTicket();
            Instant responseDeadline = ticket.getSlaPolicy().getResponseDeadline();

            assertFalse(ticket.isFirstResponseBreached(responseDeadline.minusSeconds(1)));
            assertTrue(ticket.isFirstResponseBreached(responseDeadline.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should report resolution breach when resolvedAt exceeds deadline")
        void shouldEvaluateResolutionBreach() {
            Ticket ticket = openTicket();
            Instant resolutionDeadline = ticket.getSlaPolicy().getResolutionDeadline();

            Ticket resolvedBefore = ticket
                    .assignToAgent("AGT-1", CREATED_AT.plusSeconds(1))
                    .startInvestigation(CREATED_AT.plusSeconds(2))
                    .resolve("fix", resolutionDeadline.minusSeconds(1));
            assertFalse(resolvedBefore.isResolutionBreached(resolutionDeadline));

            Ticket resolvedAfter = ticket
                    .assignToAgent("AGT-1", CREATED_AT.plusSeconds(1))
                    .startInvestigation(CREATED_AT.plusSeconds(2))
                    .resolve("fix", resolutionDeadline.plusSeconds(1));
            assertTrue(resolvedAfter.isResolutionBreached(resolutionDeadline));
        }
    }

    private void assertNotNullSlaPolicy(Ticket ticket) {
        assertNotNull(ticket.getSlaPolicy(), "SLA policy must be calculated");
        assertNotNull(ticket.getSlaPolicy().getResponseDeadline());
        assertNotNull(ticket.getSlaPolicy().getResolutionDeadline());
    }
}
