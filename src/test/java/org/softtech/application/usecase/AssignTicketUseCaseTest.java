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
import org.softtech.domain.exception.InvalidStatusTransitionException;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.AssignTicketUseCase.AssignTicketCommand;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reactive Unit Test Suite for {@link AssignTicketUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AssignTicketUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private TicketCachePort ticketCachePort;

    @Mock
    private TicketEventPublisherPort ticketEventPublisherPort;

    @InjectMocks
    private AssignTicketUseCaseImpl assignTicketUseCase;

    private Ticket openTicket;

    @BeforeEach
    void setUp() {
        openTicket = Ticket.created(
                TICKET_ID,
                "TICK-2026-0001",
                "Database timeout",
                "Description of the incident",
                Priority.HIGH,
                ErpModule.HUMAN_RESOURCES,
                "USR-CORP-98421",
                false,
                NOW
        );
    }

    @Test
    @DisplayName("Should assign an OPEN ticket, persist, evict cache and publish event")
    void shouldAssignOpenTicket() {
        AssignTicketCommand command = new AssignTicketCommand(TICKET_ID, "AGT-TI-5042");

        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(openTicket));
        when(ticketPersistencePort.update(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.evict(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketStatusChangedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        UniAssertSubscriber<Ticket> subscriber = assignTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        Ticket assigned = subscriber.awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TicketStatus.ASSIGNED, assigned.getStatus());
        assertEquals("AGT-TI-5042", assigned.getAssignedAgentId());
        assertNotNull(assigned.getFirstResponseAt());

        verify(ticketPersistencePort, times(1)).update(any(Ticket.class));
        verify(ticketCachePort, times(1)).evict(eq(TICKET_ID), eq("TICK-2026-0001"));
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Should reject assignment over a terminal ticket with IllegalStateException")
    void shouldRejectAssignmentOnTerminalTicket() {
        Ticket closed = openTicket
                .assignToAgent("AGT-1", NOW.plusSeconds(1))
                .startInvestigation(NOW.plusSeconds(2))
                .resolve("fix", NOW.plusSeconds(3))
                .closeWithFeedback(null, NOW.plusSeconds(4));

        AssignTicketCommand command = new AssignTicketCommand(TICKET_ID, "AGT-TI-5042");
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(closed));

        UniAssertSubscriber<Ticket> subscriber = assignTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(InvalidStatusTransitionException.class, null);

        verify(ticketPersistencePort, never()).update(any(Ticket.class));
    }

    @Test
    @DisplayName("Should fail with TicketNotFoundException when ticket does not exist")
    void shouldFailWithNotFoundException() {
        AssignTicketCommand command = new AssignTicketCommand(TICKET_ID, "AGT-TI-5042");
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findByTicketNumber(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        UniAssertSubscriber<Ticket> subscriber = assignTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(TicketNotFoundException.class, null);
    }

    @Test
    @DisplayName("Should degrade gracefully when cache eviction fails")
    void shouldDegradeGracefullyOnCacheFailure() {
        AssignTicketCommand command = new AssignTicketCommand(TICKET_ID, "AGT-TI-5042");

        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(openTicket));
        when(ticketPersistencePort.update(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.evict(anyString(), anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Redis unavailable")));
        when(ticketEventPublisherPort.publish(any(TicketStatusChangedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        UniAssertSubscriber<Ticket> subscriber = assignTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        Ticket assigned = subscriber.awaitItem(Duration.ofSeconds(2)).getItem();
        assertEquals(TicketStatus.ASSIGNED, assigned.getStatus());
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Should throw NullPointerException on null command")
    void shouldThrowOnNullCommand() {
        assertThrows(NullPointerException.class, () -> assignTicketUseCase.execute(null));
    }

    @Test
    @DisplayName("Should reject command with blank agentId")
    void shouldRejectBlankAgentId() {
        assertThrows(IllegalArgumentException.class, () -> new AssignTicketCommand(TICKET_ID, "  "));
        assertThrows(NullPointerException.class, () -> new AssignTicketCommand(TICKET_ID, null));
    }
}
