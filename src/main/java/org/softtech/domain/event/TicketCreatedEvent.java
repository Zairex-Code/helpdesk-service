package org.softtech.domain.event;

import lombok.Builder;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Domain Event emitted immediately after a new {@link Ticket} aggregate root is created.
 * <p>
 * Implemented as a native Java Record to guarantee shallow immutability and transparent
 * data carrier semantics. In compliance with ISO/IEC 25010 Reliability (Data Integrity) and
 * CMMI Level 2/3 Service Operations, this record provides canonical serialization for Kafka topics.
 * </p>
 *
 * @param eventId the unique event identifier for consumer idempotency
 * @param eventType the versioned schema contract identifier
 * @param ticketId the unique persistence identifier of the ticket
 * @param ticketNumber the business-readable tracking sequence (e.g., "TICK-2026-0001")
 * @param title the concise summary of the reported incident
 * @param priority the operational urgency and severity level
 * @param erpModule the affected ERP functional module
 * @param requesterId the identifier of the reporting user or corporate tenant
 * @param vipCustomer indicates whether the requester holds high-priority SLA coverage
 * @param responseDeadline the calculated SLA deadline for initial response
 * @param resolutionDeadline the calculated SLA deadline for final ticket resolution
 * @param occurredOn the exact UTC instant when the event occurred
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Builder(toBuilder = true)
public record TicketCreatedEvent(
        String eventId,
        String eventType,
        String ticketId,
        String ticketNumber,
        String title,
        Priority priority,
        ErpModule erpModule,
        String requesterId,
        boolean vipCustomer,
        Instant responseDeadline,
        Instant resolutionDeadline,
        Instant occurredOn) {

    public static final String EVENT_TYPE = "HELP_DESK_TICKET_CREATED_V1";

    /**
     * Compact constructor enforcing strict non-null invariant checks across all required fields.
     */
    public TicketCreatedEvent {
        Objects.requireNonNull(eventId, "Event ID must not be null");
        Objects.requireNonNull(eventType, "Event type must not be null");
        Objects.requireNonNull(ticketId, "Ticket ID must not be null");
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null");
        Objects.requireNonNull(title, "Title must not be null");
        Objects.requireNonNull(priority, "Priority must not be null");
        Objects.requireNonNull(erpModule, "ErpModule must not be null");
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        Objects.requireNonNull(responseDeadline, "Response deadline must not be null");
        Objects.requireNonNull(resolutionDeadline, "Resolution deadline must not be null");
        Objects.requireNonNull(occurredOn, "OccurredOn timestamp must not be null");
    }

    /**
     * Static factory method to construct a validated {@link TicketCreatedEvent} from a {@link Ticket} aggregate.
     *
     * @param ticket the fully initialized {@link Ticket} aggregate. Must not be {@code null}.
     * @param occurredOn the exact UTC timestamp when the domain event occurred. Must not be {@code null}.
     * @return an immutable, validated {@link TicketCreatedEvent} instance.
     * @throws NullPointerException if {@code ticket} or {@code occurredOn} is {@code null}.
     */
    public static TicketCreatedEvent from(Ticket ticket, Instant occurredOn) {
        Objects.requireNonNull(ticket, "Ticket aggregate must not be null to generate TicketCreatedEvent");
        Objects.requireNonNull(occurredOn, "OccurredOn timestamp must not be null");

        return TicketCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .title(ticket.getTitle())
                .priority(ticket.getPriority())
                .erpModule(ticket.getErpModule())
                .requesterId(ticket.getRequesterId())
                .vipCustomer(ticket.isVipCustomer())
                .responseDeadline(ticket.getSlaPolicy().getResponseDeadline())
                .resolutionDeadline(ticket.getSlaPolicy().getResolutionDeadline())
                .occurredOn(occurredOn)
                .build();
    }
}
