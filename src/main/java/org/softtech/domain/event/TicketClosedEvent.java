package org.softtech.domain.event;

import lombok.Builder;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Ticket;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


/**
 * Immutable Domain Event emitted when a Ticket enters the terminal CLOSED state.
 *
 * Implemented as a native Java Record to guarantee absolute immutability and thread safety
 * across asynchronous messaging pipelines. In compliance with ISO/IEC 25010 Reliability
 * (State Auditing) and CMMI Level 2/3 Service Quality Measurement, this event encapsulates
 * the final resolution lifecycle metrics, SLA breach verdicts, and optional customer satisfaction (CSAT) feedback.
 *
 *
 * @param eventId the unique cryptographic identifier for consumer idempotency
 * @param eventType the versioned schema contract identifier
 * @param ticketId the unique persistence identifier of the closed ticket
 * @param ticketNumber the business-readable tracking sequence (e.g., "TICK-2026-0001")
 * @param requesterId the user or enterprise tenant who initiated the ticket
 * @param assignedAgentId the support engineer who delivered the final resolution (can be null)
 * @param totalLifeCycleDuration the total duration elapsed from creation to closure
 * @param resolvedAt the timestamp when technical resolution was achieved (can be null if cancelled)
 * @param closedAt the exact timestamp of permanent closure
 * @param feedback the customer satisfaction (CSAT) rating and commentary (can be null)
 * @param isResolutionSlaBreached indicates whether the final resolution exceeded SLA limits
 * @param occurredOn the exact UTC timestamp when the domain event occurred

 */
@Builder(toBuilder = true)
public record TicketClosedEvent(
        String eventId,
        String eventType,
        String ticketId,
        String ticketNumber,
        String requesterId,
        String assignedAgentId,
        Duration totalLifeCycleDuration,
        Instant resolvedAt,
        Instant closedAt,
        Feedback feedback,
        boolean isResolutionSlaBreached,
        Instant occurredOn
) {

    public  final static String EVENT_TYPE = "HELP_DESK_TICKET_CLOSED_V1";

    /**
     * Compact constructor enforcing strict non-null invariants on mandatory lifecycle attributes.
     */
    public TicketClosedEvent{
        Objects.requireNonNull(eventId, "Event Id must not be null");
        Objects.requireNonNull(eventType,"Ticket type must not be null");
        Objects.requireNonNull(ticketId, "Ticket ID must not be null");
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null");
        Objects.requireNonNull(requesterId, "Request Id must not be null");
        Objects.requireNonNull(totalLifeCycleDuration, "Total Life cycle must not be null");
        Objects.requireNonNull(closedAt, "ClosedAt timestamp must not be null");
        Objects.requireNonNull(occurredOn, "Occurred on timestamp must not be null");
    }


    /**
     * Static factory method to instantiate a validated TicketClosedEvent directly
     * from a terminated Ticket aggregate root.
     *
     * @param ticket the finalized Ticket aggregate in CLOSED status. Must not be null.
     * @param occurredOn the exact UTC timestamp when the event occurred. Must not be null.
     * @return an immutable, validated TicketClosedEvent instance.
     * @throws NullPointerException if ticket or occurredOn is null.
     * @throws IllegalStateException if ticket.getClosedAt() is null.
     */
    public static TicketClosedEvent from(Ticket ticket, Instant occurredOn){
        Objects.requireNonNull(ticket, "Ticket must not be null");
        Objects.requireNonNull(occurredOn, "OccurredOn must not be null");

        Instant closedAt = ticket.getClosedAt();

        if (closedAt == null){
            throw new IllegalStateException(
                    String.format("Cannot construct TicketClosedEvent for ticket [%s]: closedAt timestamp is null", ticket.getTicketNumber()));

        }

        Duration totalDuration = Duration.between(ticket.getCreatedAt(), closedAt);
        boolean slaBreached = ticket.isResolutionBreached(closedAt);

        return TicketClosedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .requesterId(ticket.getRequesterId())
                .assignedAgentId(ticket.getAssignedAgentId())
                .totalLifeCycleDuration(totalDuration)
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(closedAt)
                .feedback(ticket.getFeedback())
                .isResolutionSlaBreached(slaBreached)
                .occurredOn(occurredOn)
                .build();
    }
}
