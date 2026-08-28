package org.softtech.infrastructure.persistence.adapter;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.infrastructure.persistence.document.TicketDocument;
import org.softtech.infrastructure.persistence.mapper.TicketPersistenceMapper;
import org.softtech.infrastructure.persistence.repository.ReactiveTicketPanacheRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for {@link TicketMongoAdapter} coordinating Panache repository and persistence mapper.
 */
@ExtendWith(MockitoExtension.class)
class TicketMongoAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";

    @Mock
    private ReactiveTicketPanacheRepository panacheRepository;

    private final TicketPersistenceMapper persistenceMapper = new TicketPersistenceMapper();

    private TicketMongoAdapter adapter;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        adapter = new TicketMongoAdapter(panacheRepository, persistenceMapper);
        ticket = Ticket.created(
                TICKET_ID, "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
    }

    @Test
    @DisplayName("Should persist a new ticket document")
    void shouldPersistTicket() {
        when(panacheRepository.persist(any(TicketDocument.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((TicketDocument) invocation.getArgument(0)));

        Ticket result = adapter.save(ticket)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TICKET_ID, result.getId());
        assertEquals(TicketStatus.OPEN, result.getStatus());
        verify(panacheRepository, times(1)).persist(any(TicketDocument.class));
    }

    @Test
    @DisplayName("Should update an existing ticket document")
    void shouldUpdateTicket() {
        when(panacheRepository.update(any(TicketDocument.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((TicketDocument) invocation.getArgument(0)));

        Ticket result = adapter.update(ticket)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TICKET_ID, result.getId());
        verify(panacheRepository, times(1)).update(any(TicketDocument.class));
    }

    @Test
    @DisplayName("Should find a ticket by id")
    void shouldFindById() {
        TicketDocument document = persistenceMapper.toDocument(ticket);
        when(panacheRepository.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(document));

        Ticket result = adapter.findById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TICKET_ID, result.getId());
    }

    @Test
    @DisplayName("Should return null when ticket not found by id")
    void shouldReturnNullWhenNotFoundById() {
        when(panacheRepository.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        Ticket result = adapter.findById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertNull(result);
    }

    @Test
    @DisplayName("Should find a ticket by ticket number")
    void shouldFindByTicketNumber() {
        TicketDocument document = persistenceMapper.toDocument(ticket);
        when(panacheRepository.findByTicketNumber("TICK-2026-0001")).thenReturn(Uni.createFrom().item(document));

        Ticket result = adapter.findByTicketNumber("TICK-2026-0001")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TICKET_ID, result.getId());
    }

    @Test
    @DisplayName("Should stream tickets by status")
    void shouldStreamByStatus() {
        when(panacheRepository.streamByStatus(TicketStatus.OPEN.name()))
                .thenReturn(Multi.createFrom().item(persistenceMapper.toDocument(ticket)));

        List<Ticket> result = adapter.findByStatus(TicketStatus.OPEN)
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
        assertEquals(TICKET_ID, result.get(0).getId());
    }

    @Test
    @DisplayName("Should stream tickets by requester")
    void shouldStreamByRequester() {
        when(panacheRepository.streamByRequesterId("USR-1"))
                .thenReturn(Multi.createFrom().item(persistenceMapper.toDocument(ticket)));

        List<Ticket> result = adapter.findByRequesterId("USR-1")
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should stream all tickets")
    void shouldStreamAll() {
        when(panacheRepository.streamAllTicket())
                .thenReturn(Multi.createFrom().item(persistenceMapper.toDocument(ticket)));

        List<Ticket> result = adapter.findAll()
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should check existence by ticket number")
    void shouldCheckExistence() {
        when(panacheRepository.existsByTicketNumber("TICK-2026-0001")).thenReturn(Uni.createFrom().item(true));

        Boolean result = adapter.existsByTicketNumber("TICK-2026-0001")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertTrue(result);
        verify(panacheRepository, times(1)).existsByTicketNumber(eq("TICK-2026-0001"));
    }

    @Test
    @DisplayName("Should reject null and blank inputs")
    void shouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> adapter.save(null));
        assertThrows(NullPointerException.class, () -> adapter.update(null));
        assertThrows(NullPointerException.class, () -> adapter.findById(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.findById("  "));
        assertThrows(NullPointerException.class, () -> adapter.findByTicketNumber(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.findByTicketNumber(" "));
        assertThrows(NullPointerException.class, () -> adapter.findByStatus(null));
        assertThrows(NullPointerException.class, () -> adapter.findByRequesterId(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.findByRequesterId(" "));
        assertThrows(NullPointerException.class, () -> adapter.existsByTicketNumber(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.existsByTicketNumber(" "));
    }
}
