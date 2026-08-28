package org.softtech.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.domain.event.TicketCreatedEvent;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.CreateTicketUseCase.CreateTicketCommand;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

/**
 * Reactive Unit Test Suite for {@link CreateTicketUseCaseImpl}.
 * <p>
 * Verifies non-blocking Mutiny orchestration, command encapsulation invariants, MongoDB transactional write,
 * resilient Cache-Aside warming in Redis, and asynchronous Kafka domain event dispatching.
 * Tests utilize {@link UniAssertSubscriber} to validate emission and error propagation without blocking OS threads.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 (Reliability & Fault Tolerance) and CMMI Level 2/3 Verification (VER),
 * this suite validates both nominal pipelines and resilient fallback degradation scenarios.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CreateTicketUseCaseTest {

    @Mock
    private TicketPersistencePort ticketPersistencePort;

    @Mock
    private TicketCachePort ticketCachePort;

    @Mock
    private TicketEventPublisherPort ticketEventPublisherPort;

    @InjectMocks
    private CreateTicketUseCaseImpl createTicketUseCase;

    private CreateTicketCommand validCommand;

    /**
     * Initializes nominal test fixtures prior to each isolated test execution.
     */
    @BeforeEach
    void setUp() {
        this.validCommand = new CreateTicketCommand(
                "Database timeout in batch payroll worker",
                "PostgreSQL deadlock detected during concurrent batch payroll execution in ERP-RRHH.",
                Priority.CRITICAL,
                ErpModule.HUMAN_RESOURCES,
                "USR-CORP-98421",
                true
        );
    }

    @Test
    @DisplayName("Should successfully create, persist, warm cache, and publish event when command is valid")
    void shouldCreatePersistCacheAndPublishEventWhenCommandIsValid() {
        // Arrange
        when(ticketPersistencePort.save(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.put(any(Ticket.class), any(Duration.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketCreatedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // Act
        UniAssertSubscriber<Ticket> subscriber = createTicketUseCase.execute(validCommand)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Assert
        Ticket createdTicket = subscriber
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        assertNotNull(createdTicket, "Created ticket aggregate must not be null");
        assertNotNull(createdTicket.getId(), "Generated ticket UUID persistence ID must not be null");
        assertNotNull(createdTicket.getTicketNumber(), "Generated tracking sequence must not be null");
        assertTrue(createdTicket.getTicketNumber().startsWith("TICK-"), "Tracking sequence must adhere to TICK-YYYY-XXXXXXXX pattern");
        assertEquals(validCommand.title(), createdTicket.getTitle());
        assertEquals(validCommand.description(), createdTicket.getDescription());
        assertEquals(TicketStatus.OPEN, createdTicket.getStatus(), "Initial lifecycle state must be OPEN");
        assertEquals(Priority.CRITICAL, createdTicket.getPriority());
        assertEquals(ErpModule.HUMAN_RESOURCES, createdTicket.getErpModule());
        assertEquals(validCommand.requesterId(), createdTicket.getRequesterId());
        assertTrue(createdTicket.isVipCustomer(), "VIP customer flag must be preserved");
        assertNotNull(createdTicket.getSlaPolicy(), "Dynamic SLA Policy must be calculated and assigned");
        assertNotNull(createdTicket.getSlaPolicy().getResponseDeadline(), "Response deadline must be calculated");
        assertNotNull(createdTicket.getSlaPolicy().getResolutionDeadline(), "Resolution deadline must be calculated");

        // Verify Secondary Port Invocations
        verify(ticketPersistencePort, times(1)).save(any(Ticket.class));
        verify(ticketCachePort, times(1)).put(any(Ticket.class), any(Duration.class));
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketCreatedEvent.class));
    }

    @Test
    @DisplayName("Should abort pipeline immediately when MongoDB persistence fails")
    void shouldAbortPipelineWhenPersistenceFails() {
        // Arrange
        RuntimeException databaseException = new RuntimeException("MongoDB replica set primary unavailable");
        when(ticketPersistencePort.save(any(Ticket.class)))
                .thenReturn(Uni.createFrom().failure(databaseException));

        // Act
        UniAssertSubscriber<Ticket> subscriber = createTicketUseCase.execute(validCommand)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Assert
        subscriber
                .awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(RuntimeException.class, "MongoDB replica set primary unavailable");

        verify(ticketPersistencePort, times(1)).save(any(Ticket.class));
        verify(ticketCachePort, never()).put(any(Ticket.class), any(Duration.class));
        verify(ticketEventPublisherPort, never()).publish(any(TicketCreatedEvent.class));
    }

    @Test
    @DisplayName("Should gracefully recover from Redis cache failure and proceed to publish Kafka event")
    void shouldGracefullyRecoverAndPublishEventWhenCacheFails() {
        // Arrange
        RuntimeException redisException = new RuntimeException("Redis connection refused on port 6380");
        when(ticketPersistencePort.save(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.put(any(Ticket.class), any(Duration.class)))
                .thenReturn(Uni.createFrom().failure(redisException));
        when(ticketEventPublisherPort.publish(any(TicketCreatedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // Act
        UniAssertSubscriber<Ticket> subscriber = createTicketUseCase.execute(validCommand)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Assert
        Ticket createdTicket = subscriber
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        assertNotNull(createdTicket);
        assertEquals(TicketStatus.OPEN, createdTicket.getStatus());

        // Verify that secondary operations were called despite cache failure
        verify(ticketPersistencePort, times(1)).save(any(Ticket.class));
        verify(ticketCachePort, times(1)).put(any(Ticket.class), any(Duration.class));
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketCreatedEvent.class));
    }

    @Test
    @DisplayName("Should propagate error when Kafka event publication fails")
    void shouldPropagateErrorWhenKafkaPublishFails() {
        // Arrange
        RuntimeException kafkaException = new RuntimeException("Kafka broker leader not available for topic partition");
        when(ticketPersistencePort.save(any(Ticket.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Ticket) invocation.getArgument(0)));
        when(ticketCachePort.put(any(Ticket.class), any(Duration.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(TicketCreatedEvent.class)))
                .thenReturn(Uni.createFrom().failure(kafkaException));

        // Act
        UniAssertSubscriber<Ticket> subscriber = createTicketUseCase.execute(validCommand)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Assert
        subscriber
                .awaitFailure(Duration.ofSeconds(2))
                .assertFailedWith(RuntimeException.class, "Kafka broker leader not available for topic partition");

        verify(ticketPersistencePort, times(1)).save(any(Ticket.class));
        verify(ticketCachePort, times(1)).put(any(Ticket.class), any(Duration.class));
        verify(ticketEventPublisherPort, times(1)).publish(any(TicketCreatedEvent.class));
    }

    @Test
    @DisplayName("Should throw NullPointerException when execute is invoked with null command")
    void shouldThrowNullPointerExceptionWhenCommandIsNull() {
        assertThrows(NullPointerException.class, () -> createTicketUseCase.execute(null));

        verify(ticketPersistencePort, never()).save(any(Ticket.class));
        verify(ticketCachePort, never()).put(any(Ticket.class), any(Duration.class));
        verify(ticketEventPublisherPort, never()).publish(any(TicketCreatedEvent.class));
    }

    @Test
    @DisplayName("Should reject Command instantiation when title is blank")
    void shouldThrowIllegalArgumentExceptionWhenCommandTitleIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                new CreateTicketCommand(
                        "   ",
                        "Valid description with technical context",
                        Priority.HIGH,
                        ErpModule.FINANCIAL,
                        "USR-101",
                        false
                ));
    }

    @Test
    @DisplayName("Should reject Command instantiation when requesterId is null")
    void shouldThrowNullPointerExceptionWhenCommandRequesterIdIsNull() {
        assertThrows(NullPointerException.class, () ->
                new CreateTicketCommand(
                        "Valid title",
                        "Valid description with technical context",
                        Priority.HIGH,
                        ErpModule.FINANCIAL,
                        null,
                        false
                ));
    }
}