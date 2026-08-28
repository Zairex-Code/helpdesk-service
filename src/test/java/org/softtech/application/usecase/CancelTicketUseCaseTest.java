package org.softtech.application.usecase;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.domain.event.TicketStatusChangedEvent;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.CancelTicketUseCase.CancelTicketCommand;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reactive Unit Test Suite for {@link CancelTicketUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CancelTicketUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private TicketCachePort ticketCachePort;

    @Mock
    private TicketEventPublisherPort ticketEventPublisherPort;

    @InjectMocks
    private CancelTicketUseCaseImpl cancelTicketUseCase;

    private Ticket openTicket;

    @BeforeEach
    void setUp() {
        openTicket = Ticket.created(
                TICKET_ID, "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
    }

    @Test
    @DisplayName("Should cancel an OPEN ticket into CANCELLED terminal state")
    void shouldCancelOpenTicket() {
        CancelTicketCommand command = new CancelTicketCommand(TICKET_ID, "duplicate ticket");

        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(openTicket));
        when(ticketPersistencePort.update(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.evict(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketStatusChangedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Ticket result = cancelTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TicketStatus.CANCELLED, result.getStatus());
        verify(ticketPersistencePort, times(1)).update(any(Ticket.class));
        verify(ticketCachePort, times(1)).evict(anyString(), anyString());
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Should fail with TicketNotFoundException when ticket does not exist")
    void shouldFailWithNotFoundException() {
        CancelTicketCommand command = new CancelTicketCommand(TICKET_ID, "duplicate");
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findByTicketNumber(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        UniAssertSubscriber<Ticket> subscriber = cancelTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(TicketNotFoundException.class, null);
    }

    @Test
    @DisplayName("Should reject blank command fields")
    void shouldRejectBlankCommand() {
        assertThrows(IllegalArgumentException.class, () -> new CancelTicketCommand(TICKET_ID, "  "));
        assertThrows(IllegalArgumentException.class, () -> new CancelTicketCommand("  ", "reason"));
        assertThrows(NullPointerException.class, () -> new CancelTicketCommand(TICKET_ID, null));
    }

    @Test
    @DisplayName("Should throw NullPointerException on null command")
    void shouldThrowOnNullCommand() {
        assertThrows(NullPointerException.class, () -> cancelTicketUseCase.execute(null));
    }
}
