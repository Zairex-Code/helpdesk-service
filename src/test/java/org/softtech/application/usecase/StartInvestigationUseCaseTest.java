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
import org.softtech.domain.port.in.StartInvestigationUseCase.StartInvestigationCommand;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reactive Unit Test Suite for {@link StartInvestigationUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class StartInvestigationUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private TicketCachePort ticketCachePort;

    @Mock
    private TicketEventPublisherPort ticketEventPublisherPort;

    @InjectMocks
    private StartInvestigationUseCaseImpl startInvestigationUseCase;

    private Ticket assignedTicket;

    @BeforeEach
    void setUp() {
        assignedTicket = Ticket.created(
                        TICKET_ID, "TICK-2026-0001", "Title", "Description",
                        Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW)
                .assignToAgent("AGT-1", NOW.plusSeconds(1));
    }

    @Test
    @DisplayName("Should transition an ASSIGNED ticket to IN_PROGRESS")
    void shouldStartInvestigation() {
        StartInvestigationCommand command = new StartInvestigationCommand(TICKET_ID);

        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(assignedTicket));
        when(ticketPersistencePort.update(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.evict(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketStatusChangedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Ticket result = startInvestigationUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TicketStatus.IN_PROGRESS, result.getStatus());
        verify(ticketPersistencePort, times(1)).update(any(Ticket.class));
        verify(ticketCachePort, times(1)).evict(anyString(), anyString());
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Should reject investigation on an OPEN ticket")
    void shouldRejectInvestigationOnOpenTicket() {
        Ticket open = Ticket.created(
                TICKET_ID, "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);

        StartInvestigationCommand command = new StartInvestigationCommand(TICKET_ID);
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(open));

        UniAssertSubscriber<Ticket> subscriber = startInvestigationUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2)).assertFailedWith(IllegalStateException.class, null);
        verify(ticketPersistencePort, never()).update(any(Ticket.class));
    }

    @Test
    @DisplayName("Should fail with TicketNotFoundException when ticket does not exist")
    void shouldFailWithNotFoundException() {
        StartInvestigationCommand command = new StartInvestigationCommand(TICKET_ID);
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findByTicketNumber(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        UniAssertSubscriber<Ticket> subscriber = startInvestigationUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(TicketNotFoundException.class, null);
    }

    @Test
    @DisplayName("Should reject blank ticket identifier in command")
    void shouldRejectBlankCommand() {
        assertThrows(IllegalArgumentException.class, () -> new StartInvestigationCommand("  "));
        assertThrows(NullPointerException.class, () -> new StartInvestigationCommand(null));
    }

    @Test
    @DisplayName("Should throw NullPointerException on null command")
    void shouldThrowOnNullCommand() {
        assertThrows(NullPointerException.class, () -> startInvestigationUseCase.execute(null));
    }
}
