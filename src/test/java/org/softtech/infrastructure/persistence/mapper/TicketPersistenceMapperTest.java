package org.softtech.infrastructure.persistence.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.infrastructure.persistence.document.TicketDocument;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test suite for {@link TicketPersistenceMapper} bidirectional document mapping.
 */
class TicketPersistenceMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    private final TicketPersistenceMapper mapper = new TicketPersistenceMapper();

    private Ticket fullTicket;

    @BeforeEach
    void setUp() {
        fullTicket = Ticket.created(
                        "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
                        "TICK-2026-0001",
                        "Database timeout",
                        "Description of the incident",
                        Priority.HIGH,
                        ErpModule.FINANCIAL,
                        "USR-CORP-98421",
                        true,
                        NOW
                )
                .assignToAgent("AGT-1", NOW.plusSeconds(60))
                .startInvestigation(NOW.plusSeconds(120))
                .resolve("Applied fix", NOW.plusSeconds(180))
                .closeWithFeedback(Feedback.of(5, "Great", NOW.plusSeconds(240)), NOW.plusSeconds(240));
    }

    @Test
    @DisplayName("Should map domain aggregate to persistence document")
    void shouldMapToDocument() {
        TicketDocument document = mapper.toDocument(fullTicket);

        assertEquals(fullTicket.getId(), document.getId());
        assertEquals(fullTicket.getTicketNumber(), document.getTicketNumber());
        assertEquals(fullTicket.getTitle(), document.getTitle());
        assertEquals(fullTicket.getDescription(), document.getDescription());
        assertEquals(TicketStatus.CLOSED.name(), document.getStatus());
        assertEquals(Priority.HIGH.name(), document.getPriority());
        assertEquals(ErpModule.FINANCIAL.name(), document.getErpModule());
        assertEquals(fullTicket.getRequesterId(), document.getRequesterId());
        assertEquals("AGT-1", document.getAssignedAgentId());
        assertEquals(true, document.isVipCustomer());
        assertEquals(fullTicket.getResolvedAt(), document.getResolveAt());
        assertEquals(fullTicket.getClosedAt(), document.getClosedAt());
        assertEquals(fullTicket.getFirstResponseAt(), document.getFirstResponseAt());

        assertNotNull(document.getSlaPolicy());
        assertEquals(fullTicket.getSlaPolicy().getResponseDeadline(), document.getSlaPolicy().responseDeadline());
        assertEquals(fullTicket.getSlaPolicy().getMaxResponseDuration().toMillis(),
                document.getSlaPolicy().maxResponseDurationMillis());

        assertNotNull(document.getFeedback());
        assertEquals(5, document.getFeedback().rating());
        assertEquals("Great", document.getFeedback().comment());
    }

    @Test
    @DisplayName("Should map persistence document back to domain aggregate")
    void shouldMapToDomain() {
        TicketDocument document = mapper.toDocument(fullTicket);
        Ticket domain = mapper.toDomain(document);

        assertEquals(fullTicket.getId(), domain.getId());
        assertEquals(fullTicket.getTicketNumber(), domain.getTicketNumber());
        assertEquals(TicketStatus.CLOSED, domain.getStatus());
        assertEquals(Priority.HIGH, domain.getPriority());
        assertEquals(ErpModule.FINANCIAL, domain.getErpModule());
        assertEquals(fullTicket.getSlaPolicy().getMaxResponseDuration(), domain.getSlaPolicy().getMaxResponseDuration());
        assertEquals(fullTicket.getSlaPolicy().getMaxResolutionDuration(), domain.getSlaPolicy().getMaxResolutionDuration());
        assertEquals(5, domain.getFeedback().getRating());
        assertEquals(fullTicket.getResolvedAt(), domain.getResolvedAt());
        assertEquals(fullTicket.getClosedAt(), domain.getClosedAt());
    }

    @Test
    @DisplayName("Should map null feedback and null slaPolicy to null sub-documents")
    void shouldMapNullSubDocuments() {
        Ticket open = Ticket.created(
                "id-2", "TICK-2026-0002", "Title", "Description",
                Priority.LOW, ErpModule.CRM, "USR-2", false, NOW);

        TicketDocument document = mapper.toDocument(open);
        assertNull(document.getFeedback());

        TicketDocument docWithoutSla = TicketDocument.builder()
                .id("id-2")
                .ticketNumber("TICK-2026-0002")
                .title("Title")
                .description("Description")
                .status(TicketStatus.OPEN.name())
                .priority(Priority.LOW.name())
                .erpModule(ErpModule.CRM.name())
                .requesterId("USR-2")
                .vipCustomer(false)
                .slaPolicy(null)
                .feedback(null)
                .notes(java.util.Collections.emptyList())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        Ticket domain = mapper.toDomain(docWithoutSla);
        assertNull(domain.getSlaPolicy());
        assertNull(domain.getFeedback());
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> mapper.toDocument(null));
        assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
    }
}
