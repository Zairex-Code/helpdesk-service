package org.softtech.domain.model;

import java.time.Duration;
import java.util.Objects;
import lombok.Getter;

/**
 * Enumeration representing the operational priority and severity levels of a HelpDesk Support Ticket.
 * <p>
 * Defines base SLA time thresholds for initial technical acknowledgment and complete resolution.
 * In accordance with ISO/IEC 25010 Performance Efficiency (Time Behavior) and CMMI Level 2/3
 * Incident Management standards, this enum provides deterministic operational parameters
 * utilized by the domain's SLA computation engine without runtime database dependencies.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Getter
public enum Priority {

    /**
     * Non-urgent incidents or cosmetic inquiries with minimal business impact.
     * Response window: 24 hours | Resolution window: 72 hours.
     */
    LOW(
            1,
            Duration.ofHours(24),
            Duration.ofHours(72),
            false
    ),

    /**
     * Standard operational disruptions with available workarounds and moderate impact.
     * Response window: 8 hours | Resolution window: 24 hours.
     */
    MEDIUM(
            2,
            Duration.ofHours(8),
            Duration.ofHours(24),
            false
    ),

    /**
     * Severe service degradation significantly impacting core business operations without complete outage.
     * Response window: 2 hours | Resolution window: 8 hours.
     */
    HIGH(
            3,
            Duration.ofHours(2),
            Duration.ofHours(8),
            false
    ),

    /**
     * Catastrophic outage or critical ERP module downtime blocking entire financial or operational workflows.
     * Response window: 30 minutes | Resolution window: 4 hours. Requires immediate supervisory escalation.
     */
    CRITICAL(
            4,
            Duration.ofMinutes(30),
            Duration.ofHours(4),
            true
    );

    private final int weight;
    private final Duration maxResponseTime;
    private final Duration maxResolutionTime;
    private final boolean immediateEscalationRequired;

    /**
     * Constructor initializing immutable operational thresholds for each priority level.
     *
     * @param weight relative numeric priority for sorting and triage queue ranking.
     * @param maxResponseTime maximum allowable duration before first agent acknowledgment.
     * @param maxResolutionTime maximum allowable duration before technical ticket resolution.
     * @param immediateEscalationRequired flag indicating if immediate supervisor alert is triggered.
     */
    Priority(
            int weight,
            Duration maxResponseTime,
            Duration maxResolutionTime,
            boolean immediateEscalationRequired) {
        this.weight = weight;
        this.maxResponseTime = Objects.requireNonNull(maxResponseTime, "Max response time must not be null");
        this.maxResolutionTime = Objects.requireNonNull(maxResolutionTime, "Max resolution time must not be null");
        this.immediateEscalationRequired = immediateEscalationRequired;
    }

    /**
     * Evaluates whether this priority level has higher or equal operational urgency than another.
     *
     * @param other the priority to compare against. Must not be {@code null}.
     * @return {@code true} if this priority weight is greater than or equal to {@code other.weight}; {@code false} otherwise.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    public boolean isHigherOrEqualUrgency(Priority other) {
        Objects.requireNonNull(other, "Comparison priority must not be null");
        return this.weight >= other.weight;
    }

    /**
     * Determines whether the priority indicates an emergency condition requiring priority queue bypass.
     *
     * @return {@code true} if priority is {@link #CRITICAL}; {@code false} otherwise.
     */
    public boolean isUrgent() {
        return this == CRITICAL;
    }
}