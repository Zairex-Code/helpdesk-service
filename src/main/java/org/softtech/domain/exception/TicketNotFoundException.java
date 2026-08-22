package org.softtech.domain.exception;


import lombok.Getter;

import java.util.Objects;


/**
 * Domain Exception thrown when an operation targets a HelpDesk Support Ticket that does not exist.
 *
 * This exception encapsulates the missing entity identifier (technical ID or business-readable
 * ticket number) and assigns the standardized enterprise error code {@code "HD-DOM-4040"}.
 * In accordance with ISO/IEC 25010 Reliability (Fault Tolerance) and CMMI Level 2/3 Incident
 * Management standards, this domain error travels across Mutiny reactive streams without leaking
 * persistence details, allowing HTTP entrypoint adapters to map it directly to an RFC 7807 404 response.
 *
 */
@Getter
public class TicketNotFoundException extends DomainException {

    public static final String ERROR_CODE = "HD-DOM-4040";

    private final String ticketIdentifier;


    /**
     * Constructs a new TicketNotFoundException identifying the missing ticket.
     *
     * @param ticketIdentifier the unique database ID or business sequence of the ticket. Must not be null.
     * @throws NullPointerException if ticketIdentifier is null.
     */
    public TicketNotFoundException(String ticketIdentifier) {
        super(
                ERROR_CODE,
                String.format("Ticket with identifier [%s] was not found in the HelpDesk domain",
                        Objects.requireNonNull(ticketIdentifier, "Ticket identifier must not be null"))
        );
        this.ticketIdentifier = ticketIdentifier;
    }


    /**
     * Static factory method to instantiate a TicketNotFoundException for technical UUID/BSON lookups.
     *
     * @param id the technical identifier. Must not be null.
     * @return a fully populated TicketNotFoundException instance.
     */
    public static TicketNotFoundException forId(String id){
        return new TicketNotFoundException(id);
    }


    /**
     * Static factory method to instantiate a TicketNotFoundException for business sequence lookups.
     *
     * @param ticketNumber the business tracking number (e.g., "TICK-2026-0001"). Must not be null.
     * @return a fully populated TicketNotFoundException instance.
     */
    public static TicketNotFoundException forTicketNumber(String ticketNumber){
        return new TicketNotFoundException(ticketNumber);
    }
}
