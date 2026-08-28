package org.softtech.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link Priority} enumeration.
 */
class PriorityTest {

    @Test
    @DisplayName("Should expose SLA durations per priority level")
    void shouldExposeSlaDurations() {
        assertEquals(Duration.ofHours(24), Priority.LOW.getMaxResponseTime());
        assertEquals(Duration.ofHours(72), Priority.LOW.getMaxResolutionTime());

        assertEquals(Duration.ofHours(8), Priority.MEDIUM.getMaxResponseTime());
        assertEquals(Duration.ofHours(24), Priority.MEDIUM.getMaxResolutionTime());

        assertEquals(Duration.ofHours(2), Priority.HIGH.getMaxResponseTime());
        assertEquals(Duration.ofHours(8), Priority.HIGH.getMaxResolutionTime());

        assertEquals(Duration.ofMinutes(30), Priority.CRITICAL.getMaxResponseTime());
        assertEquals(Duration.ofHours(4), Priority.CRITICAL.getMaxResolutionTime());
    }

    @Test
    @DisplayName("Should compare urgency by severity weight")
    void shouldCompareUrgency() {
        assertTrue(Priority.CRITICAL.isHigherOrEqualUrgency(Priority.HIGH));
        assertTrue(Priority.HIGH.isHigherOrEqualUrgency(Priority.HIGH));
        assertFalse(Priority.LOW.isHigherOrEqualUrgency(Priority.HIGH));
    }

    @Test
    @DisplayName("Should reject null comparison")
    void shouldRejectNullComparison() {
        assertThrows(NullPointerException.class, () -> Priority.HIGH.isHigherOrEqualUrgency(null));
    }

    @Test
    @DisplayName("Should flag only CRITICAL as urgent")
    void shouldFlagUrgency() {
        assertTrue(Priority.CRITICAL.isUrgent());
        assertFalse(Priority.HIGH.isUrgent());
        assertFalse(Priority.MEDIUM.isUrgent());
        assertFalse(Priority.LOW.isUrgent());
    }

    @Test
    @DisplayName("Should flag escalation only for CRITICAL")
    void shouldFlagEscalation() {
        assertTrue(Priority.CRITICAL.isImmediateEscalationRequired());
        assertFalse(Priority.HIGH.isImmediateEscalationRequired());
        assertFalse(Priority.MEDIUM.isImmediateEscalationRequired());
        assertFalse(Priority.LOW.isImmediateEscalationRequired());
    }
}
