package org.softtech.domain.model;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;


/**
 * Value Object representing the immutable Service Level Agreement (SLA) policy and operational
 * deadlines assigned to a HelpDesk Support Ticket.
 *
 * Encapsulates deterministic temporal boundaries for first-response and full-resolution targets.
 * In strict compliance with ISO/IEC 25010 Performance Efficiency (Time Behavior) and CMMI Level 2/3
 * Service Level Management standards, this domain component guarantees audit-proof evaluation
 * of SLA violations without producing side effects or coupling to external clock states.

 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SlaPolicy {

    private final Instant responseDeadline;
    private final Instant resolutionDeadline;
    private final Duration maxResponseDuration;
    private final Duration maxResolutionDuration;
    private final boolean escalationRequired;

    /**
     * Factory method that deterministically computes SLA deadlines and thresholds based on
     * ticket creation timestamp, operational priority, impacted ERP module, and customer tier.
     *
     * @param createdAt the initial ticket creation timestamp in UTC. Must not be null.
     * @param priority the operational priority determining base durations. Must not be null.
     * @param module the affected enterprise functional module. Must not be null.
     * @param isVipCustomer indicates whether the requester holds a VIP priority service tier.
     * @return a fully populated, immutable SlaPolicy instance.
     * @throws NullPointerException if createdAt, priority, or module is null.
     */
    public static SlaPolicy calculatePolicy(
            Instant createdAt,
            Priority priority,
            ErpModule module,
            boolean isVipCustomer) {
        Objects.requireNonNull(createdAt, "Creation timestamp must not be null");
        Objects.requireNonNull(priority, "Priority must not be null");
        Objects.requireNonNull(module, "ErpModule must not be null");

        Duration responseDuration = priority.getMaxResponseTime();
        Duration resolutionDuration = priority.getMaxResolutionTime();

        // High-criticality modules compress maximum resolution time by 25%
        if (module.isMissionCritical()) {
            resolutionDuration = resolutionDuration.minus(resolutionDuration.dividedBy(4));
        }

        // VIP enterprise tier contracts compress response time expectation by 50%
        if (isVipCustomer) {
            responseDuration = responseDuration.dividedBy(2);
        }

        Instant responseDeadline = createdAt.plus(responseDuration);
        Instant resolutionDeadline = createdAt.plus(resolutionDuration);
        boolean escalation = priority.isImmediateEscalationRequired() || module.isRequiresSupervisorEscalation();

        return SlaPolicy.builder()
                .responseDeadline(responseDeadline)
                .resolutionDeadline(resolutionDeadline)
                .maxResponseDuration(responseDuration)
                .maxResolutionDuration(resolutionDuration)
                .escalationRequired(escalation)
                .build();
    }

    /**
     * Evaluates whether the initial technical agent response has breached the contractual SLA deadline.
     *
     * @param firstResponseAt the timestamp when the first agent intervened. If null, evaluates against referenceInstant.
     * @param referenceInstant the reference instant to evaluate against (e.g., current evaluation time). Must not be null.
     * @return true if response occurred after or is currently exceeding the response deadline; false otherwise.
     * @throws NullPointerException if referenceInstant is null.
     */
    public boolean isResponseBreached(Instant firstResponseAt, Instant referenceInstant) {
        Objects.requireNonNull(referenceInstant, "Reference instant must not be null");
        Instant effectiveTime = (firstResponseAt != null) ? firstResponseAt : referenceInstant;
        return effectiveTime.isAfter(this.responseDeadline);
    }

    /**
     * Evaluates whether the overall ticket resolution has breached the contractual SLA deadline.
     *
     * @param resolvedAt the timestamp when the ticket reached resolved state. If null, evaluates against referenceInstant.
     * @param referenceInstant the reference instant to evaluate against (e.g., current evaluation time). Must not be null.
     * @return true if resolution occurred after or is currently exceeding the resolution deadline; {@code false} otherwise.
     * @throws NullPointerException if referenceInstant is null.
     */
    public boolean isResolutionBreached(Instant resolvedAt, Instant referenceInstant) {
        Objects.requireNonNull(referenceInstant, "Reference instant must not be null");
        Instant effectiveTime = (resolvedAt != null) ? resolvedAt : referenceInstant;
        return effectiveTime.isAfter(this.resolutionDeadline);
    }

    /**
     * Calculates the remaining duration available before the resolution SLA deadline is breached.
     *
     * @param referenceInstant the reference instant to compare against. Must not be null.
     * @return the remaining Duration, or Duration.ZERO if already breached.
     * @throws NullPointerException if referenceInstantis null.
     */
    public Duration getRemainingResolutionDuration(Instant referenceInstant) {
        Objects.requireNonNull(referenceInstant, "Reference instant must not be null");
        if (referenceInstant.isAfter(this.resolutionDeadline)) {
            return Duration.ZERO;
        }
        return Duration.between(referenceInstant, this.resolutionDeadline);
    }
}
