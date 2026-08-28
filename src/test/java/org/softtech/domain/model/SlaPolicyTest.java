package org.softtech.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link SlaPolicy} value object SLA calculation engine.
 */
class SlaPolicyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    @DisplayName("Should calculate SLA with priority base durations for standard customers")
    void shouldCalculateStandardPolicy() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);

        assertEquals(Duration.ofHours(2), policy.getMaxResponseDuration());
        assertEquals(Duration.ofHours(8), policy.getMaxResolutionDuration());
        assertEquals(CREATED_AT.plus(Duration.ofHours(2)), policy.getResponseDeadline());
        assertEquals(CREATED_AT.plus(Duration.ofHours(8)), policy.getResolutionDeadline());
        assertFalse(policy.isEscalationRequired());
    }

    @Test
    @DisplayName("Should compress response time by 50% for VIP customers")
    void shouldCompressResponseTimeForVipCustomers() {
        SlaPolicy standard = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);
        SlaPolicy vip = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, true);

        assertEquals(Duration.ofHours(1), vip.getMaxResponseDuration());
        assertTrue(vip.getMaxResponseDuration().compareTo(standard.getMaxResponseDuration()) < 0);
        assertEquals(CREATED_AT.plus(Duration.ofHours(1)), vip.getResponseDeadline());
    }

    @Test
    @DisplayName("Should compress resolution time by 25% for mission-critical modules")
    void shouldCompressResolutionTimeForMissionCriticalModules() {
        SlaPolicy standard = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);
        SlaPolicy critical = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.FINANCIAL, false);

        assertEquals(Duration.ofHours(6), critical.getMaxResolutionDuration());
        assertTrue(critical.getMaxResolutionDuration().compareTo(standard.getMaxResolutionDuration()) < 0);
        assertTrue(critical.isEscalationRequired(), "FINANCIAL module requires supervisor escalation");
    }

    @Test
    @DisplayName("Should flag escalation for CRITICAL priority")
    void shouldFlagEscalationForCriticalPriority() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.CRITICAL, ErpModule.CRM, false);
        assertTrue(policy.isEscalationRequired());
        assertEquals(Duration.ofMinutes(30), policy.getMaxResponseDuration());
        assertEquals(Duration.ofHours(4), policy.getMaxResolutionDuration());
    }

    @Test
    @DisplayName("Should reject null inputs")
    void shouldRejectNullInputs() {
        assertThrows(NullPointerException.class, () -> SlaPolicy.calculatePolicy(null, Priority.HIGH, ErpModule.CRM, false));
        assertThrows(NullPointerException.class, () -> SlaPolicy.calculatePolicy(CREATED_AT, null, ErpModule.CRM, false));
        assertThrows(NullPointerException.class, () -> SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, null, false));
    }

    @Test
    @DisplayName("Should evaluate response breach against effective time")
    void shouldEvaluateResponseBreach() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);

        assertFalse(policy.isResponseBreached(policy.getResponseDeadline().minusSeconds(1), CREATED_AT.plusSeconds(10)));
        assertTrue(policy.isResponseBreached(policy.getResponseDeadline().plusSeconds(1), CREATED_AT.plusSeconds(10)));
        // null firstResponseAt falls back to referenceInstant
        assertFalse(policy.isResponseBreached(null, policy.getResponseDeadline().minusSeconds(1)));
        assertTrue(policy.isResponseBreached(null, policy.getResponseDeadline().plusSeconds(1)));
    }

    @Test
    @DisplayName("Should evaluate resolution breach against effective time")
    void shouldEvaluateResolutionBreach() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);

        assertFalse(policy.isResolutionBreached(policy.getResolutionDeadline().minusSeconds(1), CREATED_AT.plusSeconds(10)));
        assertTrue(policy.isResolutionBreached(policy.getResolutionDeadline().plusSeconds(1), CREATED_AT.plusSeconds(10)));
    }

    @Test
    @DisplayName("Should calculate remaining resolution duration")
    void shouldCalculateRemainingResolutionDuration() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);

        Duration remaining = policy.getRemainingResolutionDuration(policy.getResolutionDeadline().minusSeconds(30));
        assertEquals(Duration.ofSeconds(30), remaining);

        assertEquals(Duration.ZERO, policy.getRemainingResolutionDuration(policy.getResolutionDeadline().plusSeconds(1)));
    }

    @Test
    @DisplayName("Should reject null reference instants")
    void shouldRejectNullReferenceInstant() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.HIGH, ErpModule.CRM, false);

        assertThrows(NullPointerException.class, () -> policy.isResponseBreached(CREATED_AT, null));
        assertThrows(NullPointerException.class, () -> policy.isResolutionBreached(CREATED_AT, null));
        assertThrows(NullPointerException.class, () -> policy.getRemainingResolutionDuration(null));
    }

    @Test
    @DisplayName("Should expose non-null deadlines")
    void shouldExposeNonNullDeadlines() {
        SlaPolicy policy = SlaPolicy.calculatePolicy(CREATED_AT, Priority.LOW, ErpModule.CRM, false);
        assertNotNull(policy.getResponseDeadline());
        assertNotNull(policy.getResolutionDeadline());
    }
}
