package org.softtech.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link Feedback} CSAT value object.
 */
class FeedbackTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    @DisplayName("Should create feedback with valid rating and comment")
    void shouldCreateValidFeedback() {
        Feedback feedback = Feedback.of(5, "  Great support  ", SUBMITTED_AT);

        assertEquals(5, feedback.getRating());
        assertEquals("Great support", feedback.getComment());
        assertEquals(SUBMITTED_AT, feedback.getSubmittedAt());
    }

    @Test
    @DisplayName("Should create feedback with null comment treated as empty string")
    void shouldCreateFeedbackWithNullComment() {
        Feedback feedback = Feedback.of(3, null, SUBMITTED_AT);
        assertEquals("", feedback.getComment());
    }

    @Test
    @DisplayName("Should reject rating below 1 and above 5")
    void shouldRejectOutOfRangeRating() {
        assertThrows(IllegalArgumentException.class, () -> Feedback.of(0, null, SUBMITTED_AT));
        assertThrows(IllegalArgumentException.class, () -> Feedback.of(6, null, SUBMITTED_AT));
    }

    @Test
    @DisplayName("Should reject comment exceeding 500 characters")
    void shouldRejectOversizedComment() {
        String longComment = "x".repeat(Feedback.MAX_COMMENT_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> Feedback.of(4, longComment, SUBMITTED_AT));
    }

    @Test
    @DisplayName("Should reject null submission timestamp")
    void shouldRejectNullTimestamp() {
        assertThrows(NullPointerException.class, () -> Feedback.of(4, "ok", null));
    }

    @Test
    @DisplayName("Should classify satisfaction, detractor and neutral ratings")
    void shouldClassifyRatings() {
        assertTrue(Feedback.of(4, null, SUBMITTED_AT).isSatisfactory());
        assertTrue(Feedback.of(5, null, SUBMITTED_AT).isSatisfactory());
        assertFalse(Feedback.of(3, null, SUBMITTED_AT).isSatisfactory());

        assertTrue(Feedback.of(1, null, SUBMITTED_AT).isDetractor());
        assertTrue(Feedback.of(2, null, SUBMITTED_AT).isDetractor());
        assertFalse(Feedback.of(3, null, SUBMITTED_AT).isDetractor());

        assertTrue(Feedback.of(3, null, SUBMITTED_AT).isNeutral());
        assertFalse(Feedback.of(2, null, SUBMITTED_AT).isNeutral());
        assertFalse(Feedback.of(4, null, SUBMITTED_AT).isNeutral());
    }

    @Test
    @DisplayName("Should expose rating constants")
    void shouldExposeRatingConstants() {
        assertEquals(1, Feedback.MIN_RATING);
        assertEquals(5, Feedback.MAX_RATING);
        assertEquals(500, Feedback.MAX_COMMENT_LENGTH);
    }
}
