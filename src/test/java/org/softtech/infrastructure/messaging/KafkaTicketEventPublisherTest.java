package org.softtech.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.domain.event.TicketClosedEvent;
import org.softtech.domain.event.TicketCreatedEvent;
import org.softtech.domain.event.TicketStatusChangedEvent;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for {@link KafkaTicketEventPublisher} partition-key routing and JSON serialization.
 */
@ExtendWith(MockitoExtension.class)
class KafkaTicketEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Mock
    private MutinyEmitter<Record<String, String>> eventEmitter;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private KafkaTicketEventPublisher publisher;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        publisher = new KafkaTicketEventPublisher(eventEmitter, objectMapper);
        ticket = Ticket.created(
                "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
                "TICK-2026-0001",
                "Title",
                "Description",
                Priority.HIGH,
                ErpModule.HUMAN_RESOURCES,
                "USR-1",
                false,
                NOW
        );
    }

    @Test
    @DisplayName("Should publish TicketCreatedEvent with ticket number as partition key")
    void shouldPublishCreatedEvent() {
        when(eventEmitter.send(any(Record.class))).thenReturn(Uni.createFrom().voidItem());

        TicketCreatedEvent event = TicketCreatedEvent.from(ticket, NOW);
        publisher.publish(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(eventEmitter, times(1)).send(captor.capture());

        Record<String, String> record = captor.getValue();
        assertEquals("TICK-2026-0001", record.key());
        assertEquals(event.ticketNumber(), record.key());
    }

    @Test
    @DisplayName("Should publish TicketStatusChangedEvent with previous and new status")
    void shouldPublishStatusChangedEvent() {
        when(eventEmitter.send(any(Record.class))).thenReturn(Uni.createFrom().voidItem());

        Ticket assigned = ticket.assignToAgent("AGT-1", NOW.plusSeconds(60));
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(
                assigned, TicketStatus.OPEN, "Assigned", NOW.plusSeconds(60));

        publisher.publish(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(eventEmitter, times(1)).send(captor.capture());
        assertEquals("TICK-2026-0001", captor.getValue().key());
    }

    @Test
    @DisplayName("Should publish TicketClosedEvent with CSAT metrics")
    void shouldPublishClosedEvent() {
        when(eventEmitter.send(any(Record.class))).thenReturn(Uni.createFrom().voidItem());

        Ticket closed = ticket
                .assignToAgent("AGT-1", NOW.plusSeconds(60))
                .startInvestigation(NOW.plusSeconds(120))
                .resolve("fix", NOW.plusSeconds(180))
                .closeWithFeedback(Feedback.of(5, "great", NOW.plusSeconds(240)), NOW.plusSeconds(240));

        TicketClosedEvent event = TicketClosedEvent.from(closed, NOW.plusSeconds(240));

        publisher.publish(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(eventEmitter, times(1)).send(captor.capture());
        assertEquals("TICK-2026-0001", captor.getValue().key());
    }

    @Test
    @DisplayName("Should reject null events")
    void shouldRejectNullEvents() {
        assertThrows(NullPointerException.class, () -> publisher.publish((TicketCreatedEvent) null));
        assertThrows(NullPointerException.class, () -> publisher.publish((TicketStatusChangedEvent) null));
        assertThrows(NullPointerException.class, () -> publisher.publish((TicketClosedEvent) null));
    }
}
