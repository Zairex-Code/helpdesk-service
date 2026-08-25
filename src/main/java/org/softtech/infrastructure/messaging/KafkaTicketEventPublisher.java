package org.softtech.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.softtech.domain.event.TicketClosedEvent;
import org.softtech.domain.event.TicketCreatedEvent;
import org.softtech.domain.event.TicketStatusChangedEvent;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import io.smallrye.reactive.messaging.kafka.Record;
import java.util.Objects;



/**
 * Secondary (Driven) Infrastructure Adapter implementing the TicketEventPublisherPort outbound port.
 * <p>
 * Bridges the pure Domain Event lifecycle with Apache Kafka distributed event streaming infrastructure
 * using SmallRye Reactive Messaging (MutinyEmitter).
 * It enforces deterministic, partition-aware message routing by binding the record component {@code ticketNumber()}
 * as the Kafka Record Key, ensuring strict chronological ordering (FIFO) for all lifecycle events emitted
 * by a specific ticket aggregate.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Fault Tolerance, Auditability) and CMMI Level 2/3
 * Service Quality Measurement standards, all event payloads are serialized to canonical JSON CloudEvent representations
 * and dispatched asynchronously without blocking the Netty Event Loop.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
@ApplicationScoped
public class KafkaTicketEventPublisher implements TicketEventPublisherPort {

    private static final String CHANNEL_TICKET_EVENTS = "ticket-events-out";

    private final MutinyEmitter<Record<String, String>> eventEmitter;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the reactive Kafka event publisher adapter with CDI-managed dependencies.
     *
     * @param eventEmitter the SmallRye Mutiny reactive channel emitter for ticket events. Must not be {@code null}.
     * @param objectMapper the enterprise JSON object mapper. Must not be {@code null}.
     */
    @Inject
    public KafkaTicketEventPublisher(
            @Channel(CHANNEL_TICKET_EVENTS) MutinyEmitter<Record<String, String>> eventEmitter,
            ObjectMapper objectMapper) {
        this.eventEmitter = Objects.requireNonNull(eventEmitter, "Kafka MutinyEmitter must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }


    /**
     * Publishes a TicketCreatedEvent to downstream event streams (e.g., Kafka topic)
     * upon the initial creation and persistence of a new ticket aggregate root.
     *
     * @param event the immutable domain event capturing the creation snapshot and SLA limits. Must not be null.
     * @return a Uni emitting null (completion) once the message is acknowledged by the broker.
     * @throws NullPointerException if event is null.
     */
    @Override
    public Uni<Void> publish(TicketCreatedEvent event) {
        Objects.requireNonNull(event, "TicketCreatedEvent must not be null for Kafka dispatch");

        String partitionKey = event.ticketNumber();
        String jsonPayload = serializeEvent(event);

        log.debug("Publishing TicketCreatedEvent to Kafka topic for ticket {} with key {}",
                event.ticketNumber(), partitionKey);


        return eventEmitter.send(Record.of(partitionKey,jsonPayload)).invoke(() -> log.info("Successfully published TicketCreatedEvent for ticket {} (Status: {} ,  Module {})",
                event.ticketNumber(), event.priority(), event.erpModule())).onFailure().invoke(throwable -> log.error(
                        "Failed to publish TicketCreatedEvent for ticket {} to Kafka: {}",
                event.ticketNumber(), throwable.getMessage(), throwable));

    }

    /**
     * Publishes a TicketStatusChangedEvent to notify distributed consumers of a verified
     * lifecycle state transition (e.g., assignment, investigation start, or resolution).
     *
     * @param event the immutable domain event capturing previous and new lifecycle states. Must not be null.
     * @return a Uni emitting null (completion) once the message is acknowledged by the broker.
     * @throws NullPointerException if event is null.
     */
    @Override
    public Uni<Void> publish(TicketStatusChangedEvent event) {
        Objects.requireNonNull(event, "TicketStatusChangedEvent must not be null for Kafka");

        String partitionKey = event.ticketNumber();
        String jsonPayload = serializeEvent(event);

        log.debug("Publishing TicketStatusChangedEvent {} to Kafka for ticket {} ({} -> {})",
                event.eventId(), partitionKey, event.previousStatus(), event.newStatus());

        return eventEmitter.send(Record.of(partitionKey, jsonPayload))
                .invoke(() -> log.info("Successfully published TicketStatusChangedEvent for ticket {} ({} -> {})",
                        event.ticketNumber(), event.previousStatus(), event.newStatus()));
    }

    /**
     * Publishes a TicketClosedEvent to finalize the event stream for a completed or canceled ticket,
     * broadcasting final MTTR durations, SLA breach verdicts, and CSAT customer satisfaction metrics.
     *
     * @param event the immutable domain event capturing terminal lifecycle audit metrics. Must not be null.
     * @return a Uni emitting null (completion) once the message is acknowledged by the broker.
     * @throws NullPointerException if event is null.
     */
    @Override
    public Uni<Void> publish(TicketClosedEvent event) {
        Objects.requireNonNull(event, "TicketClosedEvent must not be null for Kafka dispatch");

        String partitionKey = event.ticketNumber();
        String jsonPayload = serializeEvent(event);

        long durationMinutes = event.totalLifecycleDuration() != null
                ? event.totalLifecycleDuration().toMinutes()
                : 0L;

        String csatDisplay = event.feedback() != null ? event.feedback().getRating() + "/5" : "N/A";

        log.debug("Publishing TicketClosedEvent {} to Kafka for ticket {} (CSAT {}, SLA Breached: {})",
                event.eventId(), partitionKey, csatDisplay, event.isResolutionSlaBreached());

        return eventEmitter.send(Record.of(partitionKey, jsonPayload)).invoke(() -> log.info("Successfully published TicketClosedEvent for ticket {} (Lifecycle: {} min, CSAT: {})",
                event.ticketNumber(), durationMinutes, csatDisplay)).onFailure().invoke(throwable -> log.error("Failed to publish TicketClosedEvent for ticket {} to Kafka topic: {}",
                event.ticketNumber(), throwable.getMessage(), throwable));
    }


    /**
     * Serializes any immutable domain event record into a standard JSON string payload.
     *
     * @param event the domain event record to serialize. Must not be {@code null}.
     * @return the serialized JSON string.
     * @throws IllegalStateException if JSON serialization fails.
     */
    private String serializeEvent(Object event){
        try {
            return objectMapper.writeValueAsString(event);
        }catch (JsonProcessingException e){
            log.error("Critical error serializing domain event {} to JSON: {}",
                    event.getClass().getSimpleName(), e.getMessage(), e);

            throw new IllegalStateException("Kafka event JSON serialization failed", e);
        }
    }
}


