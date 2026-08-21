package org.softtech.domain.event;

import lombok.Builder;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;



/**
 * Immutable Domain Event emitted whenever a Ticket undergoes a verified state transition.
 *
 * Implemented as a native Java Record to guarantee absolute immutability and zero serialization
 * overhead in reactive pipelines. In compliance with ISO/IEC 25010 Reliability (Auditability) and
 * CMMI Level 2/3 Service Management, this event captures the transition trajectory from
 * previousStatus to newStatus, enabling asynchronous SLA calculation, agent tracking,
 * and immutable event streaming via Apache Kafka.
 *
 * @param eventId the unique cryptographic identifier for consumer idempotency
 * @param eventType the versioned schema contract identifier
 * @param ticketId the technical identifier of the modified ticket
 * @param ticketNumber the business-readable tracking sequence (e.g., "TICK-2026-0001")
 * @param previousStatus the originating lifecycle state prior to transition
 * @param newStatus the validated destination lifecycle state
 * @param assignedAgentId the support specialist assigned at the time of change (can be {@code null})
 * @param reason operational context or resolution notes justifying the transition (can be {@code null})
 * @param occurredOn the exact UTC timestamp when the state transition took place
 *
 */
@Builder(toBuilder = true)
public record TicketStatusChangedEvent(
        String eventId,
        String eventType,
        String ticketId,
        String ticketNumber,
        TicketStatus previousStatus,
        TicketStatus newStatus,
        String assignedAgentId,
        String reason,
        Instant occurredOn
) {

    public static final String EVENT_TYPE = "HELP_DESK_TICKET_STATUS_CHANGED_V1";


    /**
     * Compact constructor enforcing strict non-null invariants on mandatory event properties.
     */
    public TicketStatusChangedEvent {
        Objects.requireNonNull(eventId,"Event ID must not be null");
        Objects.requireNonNull(eventType, "Event Type must not be null");
        Objects.requireNonNull(ticketId, "Ticket ID must not be null");
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null");
        Objects.requireNonNull(previousStatus,"Previous status must not be null");
        Objects.requireNonNull(newStatus,"New status must not be null");
        Objects.requireNonNull(occurredOn, "OccurredOn timestamp must not be null");
    }


    /**
     * Static factory method to instantiate a verified TicketStatusChangedEvent
     * capturing an aggregate state transition.
     *
     * @param ticket the post-transition Ticket aggregate root. Must not be null.
     * @param previousStatus the state of the ticket immediately before transition. Must not be null.
     * @param reason the operational explanation or note associated with the transition.
     * @param occurredOn the exact UTC timestamp of the state transition. Must not be null.
     * @return an immutable, validated TicketStatusChangedEvent ready for distribution.
     * @throws NullPointerException if ticket, previousStatus, or occurredOn is null.
     */
    public static TicketStatusChangedEvent from(Ticket ticket,
                                                TicketStatus previousStatus,
                                                String reason,
                                                Instant occurredOn){
        Objects.requireNonNull(ticket, "Ticket must not be null");
        Objects.requireNonNull(previousStatus, "Previous status must not be null");
        Objects.requireNonNull(occurredOn, "OccurredOn timestamp must not be null");

        return TicketStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventId(EVENT_TYPE)
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .previousStatus(previousStatus)
                .newStatus(ticket.getStatus())
                .assignedAgentId(ticket.getAssignedAgentId())
                .reason(reason != null ? reason.trim() : null)
                .occurredOn(occurredOn)
                .build();
    }

}
