package org.softtech.domain.model;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;


/**
 * value Object representing the customer satisfaction (CSAT) feedback recorded upon ticket completion.
 *
 * Encapsulates immutable qualitative and quantitative evaluation metrics, including rating scale
 * constraints (1 to 5 stars) and user commentary. In compliance with ISO/IEC 25010 Usability
 * and Satisfaction standards and CMMI level 2/3 Service Quality Measurement, this object enforces
 * strict invariant validation at creation to ensure uncorrupted metric aggregation across the platform.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Feedback {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 500;
    private static final int SATISFACTION_THRESHOLD = 4;
    private static final int DETRACTOR_THRESHOLD = 2;

    private final int rating;
    private final String comment;
    private final Instant submittedAt;


    /**
     * Factory method to construct and validate an immutable Feedback instance
     *
     * @param rating the numeric customer satisfaction score between MIN_RATING and MAX_RATING
     * @param comment optional textual Feedback or remarks from the requester. max #MAX_COMMETN_LENGTH characters
     * @param submittedAt the exact timestamp when the survey was submitted. Must not be null.
     * @return a validated, immutable Feedback value object
     * @throws IllegalArgumentException if the rating is out of range or comment exceeds maximum length
     * @throws NullPointerException if submittedAt is null
     */
    public static Feedback of(int rating, String comment, Instant submittedAt){
        Objects.requireNonNull(submittedAt, "Submission timestamp must not be null");

        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    String.format("Feedback rating must be between %d and %d. Provided: %d", MIN_RATING, MAX_RATING, rating)
            );
        }

        String sanitizedComment = (comment != null) ? comment.trim() : "";
        if (sanitizedComment.length() > MAX_COMMENT_LENGTH){
            throw new IllegalArgumentException(
                    String.format("Feedback comment exceeds maximum allowable limit of %d characters", MAX_COMMENT_LENGTH)
            );
        }

        return Feedback.builder()
                .rating(rating)
                .comment(sanitizedComment)
                .submittedAt(submittedAt)
                .build();
    }


    /**
     * Evaluates whether the recorded rating qualifies as positive customer satisfaction (CSAT Promoter)
     *
     * @return true is rating is equal to or grater than #SATISFACTION_THRESHOLD; false otherwise
     */
    public boolean isSatisfactory(){
        return this.rating >= SATISFACTION_THRESHOLD;
    }

    /**
     * Evaluates whether the recorded rating represents a dissatisfied customer (CSAT Detractor)
     *
     * @return true if rating is equal to or less than #DETRACTOR_THRESHOLD ; false otherwise.
     */
    public boolean isDetractor(){
        return this.rating <= DETRACTOR_THRESHOLD;
    }


    /**
     * Evaluates whether the recorded rating represents a neutral/passive customer evaluation.
     *
     * @return {@code true} if rating is between detractor and satisfaction thresholds (rating = 3); {@code false} otherwise.
     */
    public boolean isNeutral() {
        return this.rating > DETRACTOR_THRESHOLD && this.rating < SATISFACTION_THRESHOLD;
    }

}
