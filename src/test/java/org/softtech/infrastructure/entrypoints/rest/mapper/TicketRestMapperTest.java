package org.softtech.infrastructure.entrypoints.rest.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.infrastructure.entrypoints.rest.dto.TicketResponseDto;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test suite for {@link TicketRestMapper} domain-to-DTO flattening.
 */
class TicketRestMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    private final TicketRestMapper mapper = Mappers.getMapper(TicketRestMapper.class);

    private Ticket resolvedTicket;

    @BeforeEach
    void setUp() {
        resolvedTicket = Ticket.created(
                        "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
                        "TICK-2026-0001",
                        "Database timeout",
                        "Description of the incident",
                        Priority.HIGH,
                        ErpModule.HUMAN_RESOURCES,
                        "USR-CORP-98421",
                        false,
                        NOW
                )
                .assignToAgent("AGT-1", NOW.plusSeconds(60))
                .startInvestigation(NOW.plusSeconds(120))
                .resolve("Applied the fix", NOW.plusSeconds(180));
    }

    @Test
    @DisplayName("Should flatten domain aggregate into response DTO")
    void shouldFlattenToResponseDto() {
        TicketResponseDto dto = mapper.toResponseDto(resolvedTicket);

        assertEquals(resolvedTicket.getId(), dto.id());
        assertEquals(resolvedTicket.getTicketNumber(), dto.ticketNumber());
        assertEquals(resolvedTicket.getTitle(), dto.title());
        assertEquals(resolvedTicket.getDescription(), dto.description());
        assertEquals(TicketStatus.RESOLVED, dto.status());
        assertEquals(Priority.HIGH, dto.priority());
        assertEquals(ErpModule.HUMAN_RESOURCES, dto.erpModule());
        assertEquals(resolvedTicket.getRequesterId(), dto.requesterId());
        assertEquals("AGT-1", dto.assignedAgentId());
        assertEquals("Applied the fix", dto.resolutionNotes());
        assertEquals(resolvedTicket.getSlaPolicy().getResponseDeadline(), dto.responseDeadline());
        assertEquals(resolvedTicket.getSlaPolicy().getResolutionDeadline(), dto.resolutionDeadline());
        assertEquals(resolvedTicket.getResolvedAt(), dto.resolvedAt());
    }

    @Test
    @DisplayName("Should extract CSAT fields from feedback")
    void shouldExtractCsatFields() {
        Ticket closed = resolvedTicket.closeWithFeedback(Feedback.of(4, "Nice work", NOW.plusSeconds(240)), NOW.plusSeconds(240));

        TicketResponseDto dto = mapper.toResponseDto(closed);

        assertEquals(4, dto.csatRating());
        assertEquals("Nice work", dto.csatComment());
        assertEquals(TicketStatus.CLOSED, dto.status());
        assertEquals(closed.getClosedAt(), dto.closedAt());
    }

    @Test
    @DisplayName("Should compute SLA breach flags")
    void shouldComputeSlaBreachFlags() {
        TicketResponseDto dto = mapper.toResponseDto(resolvedTicket);

        assertFalse(dto.isResponseSlaBreached(), "Response happened within SLA window");
        assertFalse(dto.isResolutionSlaBreached(), "Resolution happened within SLA window");
    }

    @Test
    @DisplayName("Should return null for null input")
    void shouldReturnNullForNullInput() {
        assertNull(mapper.toResponseDto(null));
    }

    @Test
    @DisplayName("Should map a list of tickets")
    void shouldMapList() {
        List<TicketResponseDto> dtos = mapper.toResponseDtoList(List.of(resolvedTicket, resolvedTicket));
        assertEquals(2, dtos.size());
    }

    @Test
    @DisplayName("Should extract resolution notes from audit trail")
    void shouldExtractResolutionNotes() {
        assertEquals("Applied the fix", mapper.extractResolutionNotes(resolvedTicket));
        assertNull(mapper.extractResolutionNotes(null));

        Ticket unresolved = Ticket.created(
                "id-2", "TICK-2026-0002", "Title", "Description",
                Priority.LOW, ErpModule.CRM, "USR-2", false, NOW);
        assertNull(mapper.extractResolutionNotes(unresolved));
    }

    @Test
    @DisplayName("Should evaluate SLA breach helpers on null policies")
    void shouldEvaluateBreachHelpersOnNullPolicy() {
        assertFalse(mapper.mapIsResponseSlaBreached(null));
        assertFalse(mapper.mapIsResolutionSlaBreached(null));
    }
}
