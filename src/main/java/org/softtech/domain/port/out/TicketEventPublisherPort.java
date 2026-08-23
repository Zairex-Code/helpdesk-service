package org.softtech.domain.port.out;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.event.TicketClosedEvent;
import org.softtech.domain.event.TicketCreatedEvent;
import org.softtech.domain.event.TicketStatusChangedEvent;


/**
 * Outbound Port (Driven / Secondary Port) defining the reactive event publishing contract
 * for domain events emitted across the Ticket lifecycle.
 *
 * In strict compliance with Hexagonal Architecture (Ports and Adapters), Domain-Driven Design (DDD),
 * and Event-Driven Architecture (EDA) principles, this interface decouples core domain orchestration
 * from message brokers and transport mechanisms (such as Apache Kafka via SmallRye Reactive Messaging).
 * All publishing methods execute asynchronously and non-blockingly, returning a Uni that emits
 * completion signals upon successful message acknowledgment by downstream messaging infrastructure.
 *
 *
 */
public interface TicketEventPublisherPort {

    /**
     * Publishes a TicketCreatedEvent to downstream event streams (e.g., Kafka topic)
     * upon the initial creation and persistence of a new ticket aggregate root.
     *
     * @param event the immutable domain event capturing the creation snapshot and SLA limits. Must not be null.
     * @return a Uni emitting null (completion) once the message is acknowledged by the broker.
     * @throws NullPointerException if event is null.
     */
    Uni<Void> publish(TicketCreatedEvent event);


    /**
     * Publishes a TicketStatusChangedEvent to notify distributed consumers of a verified
     * lifecycle state transition (e.g., assignment, investigation start, or resolution).
     *
     * @param event the immutable domain event capturing previous and new lifecycle states. Must not be null.
     * @return a Uni emitting null (completion) once the message is acknowledged by the broker.
     * @throws NullPointerException if event is null.
     */
    Uni<Void> publish(TicketStatusChangedEvent event);


    /**
     * Publishes a TicketClosedEvent to finalize the event stream for a completed or canceled ticket,
     * broadcasting final MTTR durations, SLA breach verdicts, and CSAT customer satisfaction metrics.
     *
     * @param event the immutable domain event capturing terminal lifecycle audit metrics. Must not be null.
     * @return a Uni emitting null (completion) once the message is acknowledged by the broker.
     * @throws NullPointerException if event is null.
     */
    Uni<Void> publish(TicketClosedEvent event);
}
