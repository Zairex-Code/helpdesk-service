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
import org.softtech.domain.event.TicketClosedEvent;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.CloseTicketUseCase.CloseTicketCommand;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reactive Unit Test Suite for {@link CloseTicketUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CloseTicketUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private TicketCachePort ticketCachePort;

    @Mock
    private TicketEventPublisherPort ticketEventPublisherPort;

    @InjectMocks
    private CloseTicketUseCaseImpl closeTicketUseCase;

    private Ticket resolvedTicket;

    @BeforeEach
    void setUp() {
        resolvedTicket = Ticket.created(
                        TICKET_ID,
                        "TICK-2026-0001",
                        "Database timeout",
                        "Description of the incident",
                        Priority.HIGH,
                        ErpModule.HUMAN_RESOURCES,
                        "USR-CORP-98421",
                        false,
                        NOW
                )
                .assignToAgent("AGT-1", NOW.plusSeconds(1))
                .startInvestigation(NOW.plusSeconds(2))
                .resolve("Applied fix", NOW.plusSeconds(3));
    }

    @Test
    @DisplayName("Should close a RESOLVED ticket with CSAT feedback")
    void shouldCloseResolvedTicketWithFeedback() {
        CloseTicketCommand command = new CloseTicketCommand(TICKET_ID, 5, "Great support");

        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(resolvedTicket));
        when(ticketPersistencePort.update(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.evict(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketClosedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        UniAssertSubscriber<Ticket> subscriber = closeTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        Ticket closed = subscriber.awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TicketStatus.CLOSED, closed.getStatus());
        assertNotNull(closed.getClosedAt());
        assertNotNull(closed.getFeedback());
        assertEquals(5, closed.getFeedback().getRating());
        assertEquals("Great support", closed.getFeedback().getComment());

        verify(ticketPersistencePort, times(1)).update(any(Ticket.class));
        verify(ticketCachePort, times(1)).evict(anyString(), anyString());
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketClosedEvent.class));
    }

    @Test
    @DisplayName("Should close without feedback when rating is null")
    void shouldCloseWithoutFeedbackWhenRatingNull() {
        CloseTicketCommand command = new CloseTicketCommand(TICKET_ID, null, null);

        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(resolvedTicket));
        when(ticketPersistencePort.update(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.evict(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketClosedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Ticket closed = closeTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TicketStatus.CLOSED, closed.getStatus());
        assertNull(closed.getFeedback());
    }

    @Test
    @DisplayName("Should reject closing a non-resolved ticket with IllegalStateException")
    void shouldRejectClosingOpenTicket() {
        Ticket open = Ticket.created(
                TICKET_ID, "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);

        CloseTicketCommand command = new CloseTicketCommand(TICKET_ID, null, null);
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().item(open));

        UniAssertSubscriber<Ticket> subscriber = closeTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2)).assertFailedWith(IllegalStateException.class, null);
        verify(ticketPersistencePort, never()).update(any(Ticket.class));
    }

    @Test
    @DisplayName("Should reject command with out-of-range rating")
    void shouldRejectOutOfRangeRating() {
        assertThrows(IllegalArgumentException.class, () -> new CloseTicketCommand(TICKET_ID, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new CloseTicketCommand(TICKET_ID, 6, null));
    }

    @Test
    @DisplayName("Should fail with TicketNotFoundException when ticket does not exist")
    void shouldFailWithNotFoundException() {
        CloseTicketCommand command = new CloseTicketCommand(TICKET_ID, null, null);
        when(ticketPersistencePort.findById(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());
        when(ticketPersistencePort.findByTicketNumber(TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        UniAssertSubscriber<Ticket> subscriber = closeTicketUseCase.execute(command)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(TicketNotFoundException.class, null);
    }

    @Test
    @DisplayName("Should throw NullPointerException on null command")
    void shouldThrowOnNullCommand() {
        assertThrows(NullPointerException.class, () -> closeTicketUseCase.execute(null));
    }
}
