package org.softtech.application.usecase;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reactive Unit Test Suite for {@link GetTicketUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class GetTicketUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";
    private static final String TICKET_NUMBER = "TICK-2026-0001";

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private TicketCachePort ticketCachePort;

    @InjectMocks
    private GetTicketUseCaseImpl getTicketUseCase;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = Ticket.created(
                TICKET_ID, TICKET_NUMBER, "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
    }

    @Test
    @DisplayName("Should return ticket from cache without hitting persistence (cache hit)")
    void shouldReturnFromCacheHit() {
        when(ticketCachePort.getById(TICKET_ID)).thenReturn(Uni.createFrom().item(ticket));

        Ticket result = getTicketUseCase.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(ticket, result);
        verify(ticketPersistencePort, never()).findById(any());
        verify(ticketCachePort, never()).put(any(), any());
    }

    @Test
    @DisplayName("Should fall back to persistence and warm cache on cache miss")
    void shouldFallBackToPersistenceOnCacheMiss() {
        when(ticketCachePort.getById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(ticket));
        when(ticketCachePort.put(any(Ticket.class), any(Duration.class))).thenReturn(Uni.createFrom().voidItem());

        Ticket result = getTicketUseCase.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(ticket, result);
        verify(ticketPersistencePort, times(1)).findById(TICKET_ID);
        verify(ticketCachePort, times(1)).put(any(Ticket.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should fail with TicketNotFoundException when absent from cache and persistence")
    void shouldFailWithNotFoundException() {
        when(ticketCachePort.getById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        UniAssertSubscriber<Ticket> subscriber = getTicketUseCase.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(TicketNotFoundException.class, null);
    }

    @Test
    @DisplayName("Should degrade gracefully when cache read fails")
    void shouldDegradeGracefullyOnCacheReadFailure() {
        when(ticketCachePort.getById(TICKET_ID))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Redis unavailable")));
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(ticket));
        when(ticketCachePort.put(any(Ticket.class), any(Duration.class))).thenReturn(Uni.createFrom().voidItem());

        Ticket result = getTicketUseCase.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(ticket, result);
    }

    @Test
    @DisplayName("Should retrieve by ticket number with cache fallback")
    void shouldRetrieveByTicketNumber() {
        when(ticketCachePort.getByTicketNumber(TICKET_NUMBER)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findByTicketNumber(TICKET_NUMBER)).thenReturn(Uni.createFrom().item(ticket));
        when(ticketCachePort.put(any(Ticket.class), any(Duration.class))).thenReturn(Uni.createFrom().voidItem());

        Ticket result = getTicketUseCase.getByTicketNumber(TICKET_NUMBER)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(ticket, result);
        verify(ticketPersistencePort, times(1)).findByTicketNumber(TICKET_NUMBER);
    }

    @Test
    @DisplayName("Should stream tickets by status via Multi")
    void shouldStreamByStatus() {
        when(ticketPersistencePort.findByStatus(TicketStatus.OPEN))
                .thenReturn(Multi.createFrom().items(ticket));

        List<Ticket> result = getTicketUseCase.listByStatus(TicketStatus.OPEN)
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
        verify(ticketPersistencePort, times(1)).findByStatus(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("Should stream tickets by requester via Multi")
    void shouldStreamByRequester() {
        when(ticketPersistencePort.findByRequesterId("USR-1"))
                .thenReturn(Multi.createFrom().items(ticket));

        List<Ticket> result = getTicketUseCase.listByRequesterId("USR-1")
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should stream all tickets via Multi")
    void shouldStreamAll() {
        when(ticketPersistencePort.findAll()).thenReturn(Multi.createFrom().items(ticket, ticket));

        List<Ticket> result = getTicketUseCase.listAll()
                .collect().asList().await().atMost(Duration.ofSeconds(2));

        assertEquals(2, result.size());
        verify(ticketPersistencePort, times(1)).findAll();
    }

    @Test
    @DisplayName("Should reject null and blank identifiers")
    void shouldRejectInvalidIdentifiers() {
        assertThrows(NullPointerException.class, () -> getTicketUseCase.getById(null));
        assertThrows(IllegalArgumentException.class, () -> getTicketUseCase.getById("   "));
        assertThrows(NullPointerException.class, () -> getTicketUseCase.getByTicketNumber(null));
        assertThrows(IllegalArgumentException.class, () -> getTicketUseCase.getByTicketNumber("  "));
        assertThrows(NullPointerException.class, () -> getTicketUseCase.listByStatus(null));
        assertThrows(NullPointerException.class, () -> getTicketUseCase.listByRequesterId(null));
        assertThrows(IllegalArgumentException.class, () -> getTicketUseCase.listByRequesterId(" "));
    }

    @Test
    @DisplayName("Should verify cache put is not invoked when ticket absent in persistence")
    void shouldNotWarmCacheWhenAbsent() {
        when(ticketCachePort.getById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        getTicketUseCase.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(Duration.ofSeconds(2));

        verify(ticketCachePort, never()).put(any(), any());
    }
}
