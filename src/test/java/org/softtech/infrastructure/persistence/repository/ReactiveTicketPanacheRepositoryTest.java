package org.softtech.infrastructure.persistence.repository;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.infrastructure.persistence.document.TicketDocument;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for {@link ReactiveTicketPanacheRepository} delegating Panache queries.
 */
@ExtendWith(MockitoExtension.class)
class ReactiveTicketPanacheRepositoryTest {

    private ReactiveTicketPanacheRepository repository;
    private ReactivePanacheQuery<TicketDocument> query;

    @BeforeEach
    void setUp() {
        repository = spy(new ReactiveTicketPanacheRepository());
        query = mock(ReactivePanacheQuery.class);
    }

    private TicketDocument document(String ticketNumber) {
        return TicketDocument.builder()
                .id("id-" + ticketNumber)
                .ticketNumber(ticketNumber)
                .title("Title")
                .description("Description")
                .status("OPEN")
                .priority("HIGH")
                .erpModule("CRM")
                .requesterId("USR-1")
                .assignedAgentId(null)
                .vipCustomer(false)
                .notes(List.of())
                .createdAt(Instant.parse("2026-08-25T12:00:00Z"))
                .updatedAt(Instant.parse("2026-08-25T12:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("Should find by ticket number")
    void shouldFindByTicketNumber() {
        TicketDocument expected = document("TICK-2026-0001");
        doReturn(query).when(repository).find(eq("ticket_number"), eq("TICK-2026-0001"));
        when(query.firstResult()).thenReturn(Uni.createFrom().item(expected));

        TicketDocument result = repository.findByTicketNumber("TICK-2026-0001")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals("TICK-2026-0001", result.getTicketNumber());
    }

    @Test
    @DisplayName("Should stream by status")
    void shouldStreamByStatus() {
        doReturn(query).when(repository).find(eq("status"), eq("OPEN"));
        when(query.stream()).thenReturn(Multi.createFrom().item(document("TICK-2026-0001")));

        List<TicketDocument> result = repository.streamByStatus("OPEN")
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should stream by requester id")
    void shouldStreamByRequesterId() {
        doReturn(query).when(repository).find(eq("requester_id"), eq("USR-1"));
        when(query.stream()).thenReturn(Multi.createFrom().item(document("TICK-2026-0001")));

        List<TicketDocument> result = repository.streamByRequesterId("USR-1")
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should stream all tickets")
    void shouldStreamAll() {
        doReturn(query).when(repository).findAll();
        when(query.stream()).thenReturn(Multi.createFrom().item(document("TICK-2026-0001")));

        List<TicketDocument> result = repository.streamAllTicket()
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should check existence by ticket number")
    void shouldCheckExistence() {
        doReturn(Uni.createFrom().item(1L)).when(repository).count(eq("ticket_number"), eq("TICK-2026-0001"));

        Boolean result = repository.existsByTicketNumber("TICK-2026-0001")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertTrue(result);
    }

    @Test
    @DisplayName("Should report non-existence when count is zero")
    void shouldReportNonExistence() {
        doReturn(Uni.createFrom().item(0L)).when(repository).count(eq("ticket_number"), eq("TICK-2026-0001"));

        Boolean result = repository.existsByTicketNumber("TICK-2026-0001")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject null and blank inputs")
    void shouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> repository.findByTicketNumber(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByTicketNumber(" "));
        assertThrows(NullPointerException.class, () -> repository.streamByStatus(null));
        assertThrows(IllegalArgumentException.class, () -> repository.streamByStatus(" "));
        assertThrows(NullPointerException.class, () -> repository.streamByRequesterId(null));
        assertThrows(IllegalArgumentException.class, () -> repository.streamByRequesterId(" "));
        assertThrows(NullPointerException.class, () -> repository.existsByTicketNumber(null));
        assertThrows(IllegalArgumentException.class, () -> repository.existsByTicketNumber(" "));
    }
}
