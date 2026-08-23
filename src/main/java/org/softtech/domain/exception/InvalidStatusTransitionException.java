package org.softtech.domain.exception;


import lombok.Getter;
import org.softtech.domain.model.TicketStatus;

import java.util.Objects;


/**
 * Domain Exception thrown when an illegal or unsupported lifecycle state transition
 * is attempted on a HelpDesk Support Ticket.
 *
 * This exception encapsulates the affected ticket tracking sequence, the originating state,
 * and the prohibited target state, assigning the standardized enterprise error code {@code "HD-DOM-4001"}.
 * In compliance with ISO/IEC 25010 Reliability (Fault Tolerance and State Integrity) and
 * CMMI Level 2/3 Incident Management standards, this error protects aggregate boundaries
 * from corruption, allowing reactive HTTP entrypoint mappers to produce RFC 7807 422/409 responses.
 *
 */
@Getter
public class InvalidStatusTransitionException extends DomainException {
    public static final String ERROR_CODE = "HD-DOM-4001";

    private final String ticketNumber;
    private final TicketStatus currentStatus;
    private final TicketStatus attemptedStatus;




    /**
     * Constructs a new {@link InvalidStatusTransitionException} capturing the illegal state transition attempt.
     *
     * @param ticketNumber the business tracking sequence (e.g., "TICK-2026-0001"). Must not be {@code null}.
     * @param currentStatus the existing verified state of the ticket aggregate. Must not be {@code null}.
     * @param attemptedStatus the illegal target state that was rejected by the state machine. Must not be {@code null}.
     * @throws NullPointerException if {@code ticketNumber}, {@code currentStatus}, or {@code attemptedStatus} is {@code null}.
     */
    public InvalidStatusTransitionException(
            String ticketNumber,
            TicketStatus currentStatus,
            TicketStatus attemptedStatus
    ){
        super(
                ERROR_CODE,
                String.format("Invalid state transition for ticket [%s]: cannot transition from %s to %s",
                        Objects.requireNonNull(ticketNumber, "Ticket number must not be null"),
                        Objects.requireNonNull(currentStatus, "Current status must not be null"),
                        Objects.requireNonNull(attemptedStatus, "attemptedStatus must not be null")
                )
        );

        this.ticketNumber = ticketNumber;
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }


}
