package org.softtech.domain.event;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


/**
 * Domain Event emitted immediately after a new Ticket aggregate root is created and verified.
 *
 * This immutable event serves as the single source of truth for downstream event-driven workflows,
 * including Kafka event streaming, notification dispatching, cache pre-warming, and SLA tracking engines.
 * In accordance with ISO/IEC 25010 Reliability (State Integrity) and CMMI Level 2/3 Service Operations,
 * this record represents an immutable historical fact that cannot be retracted or altered.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TicketCreatedEvent {

    public static final String EVENT_TYPE = "HELP_DESK_TICKET_CREATED_V1";

    private final String eventId;
    private final String eventType;
    private final String ticketId;
    private final String ticketNumber;
    private final String title;
    private final Priority priority;
    private final ErpModule erpModule;
    private final boolean vipCustomer;
    private final Instant responseDeadline;
    private final Instant resolutionDeadline;
    private final Instant occurredOn;


    /**
     * Static factory method to instantiate a TicketCreatedEvent directly from a verified Ticket aggregate.
     *
     * @param ticket the fully constructed Ticket aggregate root. Must not be null.
     * @param occurredOn the exact UTC timestamp when the domain event occurred. Must not be null.
     * @return an immutable, validated TicketCreatedEvent instance ready for distribution.
     * @throws NullPointerException if ticket or occurredOn is null.
     */
    public static TicketCreatedEvent from(Ticket ticket, Instant occurredOn){
        Objects.requireNonNull(ticket, "Ticket aggregate must not be null to generate TicketCreateEvent");
        Objects.requireNonNull(occurredOn, "OccurredOn timestamp must not be null");

        return TicketCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .title(ticket.getTitle())
                .priority(ticket.getPriority())
                .erpModule(ticket.getErpModule())
                .vipCustomer(ticket.isVipCustomer())
                .responseDeadline(ticket.getSlaPolicy().getResponseDeadline())
                .resolutionDeadline(ticket.getSlaPolicy().getResolutionDeadline())
                .occurredOn(occurredOn)
                .build();
    }

}
