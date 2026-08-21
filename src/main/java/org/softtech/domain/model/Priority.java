package org.softtech.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;


/**
 * represents the operational urgency and business impact level of a HelpDesk Ticket
 *
 * Encapsulates deterministic Service Level Agreement thresholds for first-response
 * and full-resolution deadlines in accordance with ISO/IEC 25010 Performance efficiency
 */
@Getter
@AllArgsConstructor
public enum Priority {

    /**
     * Minor cosmetic issues, non-blocking technical queries, or lew-impact feature questions.
     * Response Target: Within 24 hours | Resolution Target: Within 72 hours.
     */
    LOW(Duration.ofHours(24), Duration.ofHours(72), 1 , false),


    /**
     * Standard functional issues with operational workarounds available.
     * Response Target: Within 8 hours | Resolution Target: Within 24 hours
     */
    MEDIUM(Duration.ofHours(8), Duration.ofHours(24), 2 , false),


    /**
     * Significant business disruption affecting core operations without immediate workarounds
     * Response Target: Within 2 hours | Resolution Target: Within 8 hours.
     */
    HIGH(Duration.ofHours(2), Duration.ofHours(8), 3, true),

    /**
     * Catastrophic outage, critical ERP failure, or security breach blocking all transactions.
     * Response target: Within 30 minutes | Resolution Target: within 4 hours.
     */
    CRITICAL(Duration.ofMinutes(30), Duration.ofHours(4), 4, true);



    private final Duration maxResponseTime;
    private final Duration maxResolutionTime;
    private final int severityWeight;
    private final boolean immediateEscalationRequired;


}
