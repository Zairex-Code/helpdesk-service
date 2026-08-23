package org.softtech.domain.model;
import java.time.Duration;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enumeration representing the operational priority and severity levels of a HelpDesk Support Ticket.
 * <p>
 * Defines deterministic Service Level Agreement (SLA) time thresholds for initial technical
 * acknowledgment and complete resolution. In accordance with ISO/IEC 25010 Performance Efficiency
 * (Time Behavior) and CMMI Level 2/3 Incident Management standards, this enum encapsulates
 * operational parameters utilized by the domain's SLA computation engine without runtime database dependencies.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Getter
@AllArgsConstructor
public enum Priority {

    /**
     * Minor cosmetic issues, non-blocking queries, or low-impact feature inquiries.
     * Response window: Within 24 hours | Resolution window: Within 72 hours.
     */
    LOW(1, Duration.ofHours(24), Duration.ofHours(72), false),

    /**
     * Standard operational disruptions with functional workarounds available.
     * Response window: Within 8 hours | Resolution window: Within 24 hours.
     */
    MEDIUM(2, Duration.ofHours(8), Duration.ofHours(24), false),

    /**
     * Severe business degradation significantly impacting operations without complete outage.
     * Response window: Within 2 hours | Resolution window: Within 8 hours.
     */
    HIGH(3, Duration.ofHours(2), Duration.ofHours(8), false),

    /**
     * Catastrophic outage, critical ERP failure, or security breach blocking all transactions.
     * Response window: Within 30 minutes | Resolution window: Within 4 hours. Requires immediate supervisory escalation.
     */
    CRITICAL(4, Duration.ofMinutes(30), Duration.ofHours(4), true);

    private final int severityWeight;
    private final Duration maxResponseTime;
    private final Duration maxResolutionTime;
    private final boolean immediateEscalationRequired;

    /**
     * Evaluates whether this priority level has greater or equal operational urgency than another.
     *
     * @param other the target priority to compare against. Must not be {@code null}.
     * @return {@code true} if this severity weight is greater than or equal to {@code other.severityWeight}; {@code false} otherwise.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    public boolean isHigherOrEqualUrgency(Priority other) {
        Objects.requireNonNull(other, "Comparison priority must not be null");
        return this.severityWeight >= other.severityWeight;
    }

    /**
     * Determines whether the incident represents a catastrophic emergency requiring priority bypass.
     *
     * @return {@code true} if priority is {@link #CRITICAL}; {@code false} otherwise.
     */
    public boolean isUrgent() {
        return this == CRITICAL;
    }
}
