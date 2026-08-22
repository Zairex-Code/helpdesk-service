package org.softtech.domain.exception;


import lombok.Getter;

import java.time.Instant;
import java.util.Objects;


/**
 * Abstract base exception representing a business invariant violation within the HelpDesk domain.
 *
 * Serving as the root of all domain-specific errors in accordance with Hexagonal Architecture
 * and Domain-Driven Design (DDD), this unchecked exception encapsulates an immutable, standardized
 * business error code and an audit timestamp in UTC. In compliance with ISO/IEC 25010 Reliability
 * (Fault Tolerance) and CMMI Level 2/3 Incident Management standards, this structure guarantees
 * structured propagation across Mutiny reactive streams without leaking infrastructure-specific details.
 *
 *
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final Instant timestamp;



    /**
     * Constructs a new DomainException with a specific business error code and explanatory message.
     *
     * @param errorCode the standardized business error identifier (e.g., "HD-DOM-4001"). Must not be null.
     * @param message the detailed human-readable explanation of the invariant breach. Must not be null.
     * @throws NullPointerException if errorCode or message is null.
     */
    protected DomainException(String errorCode, String message){
        super(Objects.requireNonNull(message, "Exception message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "Error code must not be null");
        this.timestamp = Instant.now();
    }



    /**
     * Constructs a new DomainException with a specific error code, message, and root cause.
     *
     * @param errorCode the standardized business error identifier. Must not be null.
     * @param message the detailed human-readable explanation. Must not be null.
     * @param cause the underlying throwable cause. Can be null.
     * @throws NullPointerException if errorCode or message is null.
     */
    protected DomainException(String errorCode, String message, Throwable cause){
        super(Objects.requireNonNull(message, "Exception message must not be null"), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "Error code must not be null");
        this.timestamp = Instant.now();
    }
}
