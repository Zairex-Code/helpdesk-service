package org.softtech.infrastructure.entrypoints.rest.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;



/**
 * Immutable Data Transfer Object (DTO) capturing HTTP command payloads for terminal ticket closure and customer feedback.
 * <p>
 * Enforces declarative input validation constraints at the REST boundary via Jakarta Validation (Bean Validation 3.0),
 * validating that customer satisfaction (CSAT) rating adheres strictly to the 1-5 numerical score range and that optional
 * qualitative comments do not exceed storage limits before delegating to {@code CloseTicketUseCase}.
 * Documented with MicroProfile OpenAPI 3.1 / 4.0+ annotations using modern array-based examples.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Data Integrity & Fault Tolerance) and
 * CMMI Level 2/3 Service Quality & Customer Satisfaction Measurement standards, this record is shallowly immutable,
 * thread-safe, and deserialized without reflection overhead on the Netty Event Loop.
 * </p>
 *
 * @param rating the numeric customer satisfaction (CSAT) rating score. Mandatory integer between 1 and 5.
 * @param comment the optional qualitative assessment or feedback commentary. Maximum 500 characters.
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "CloseTicketRequestDto",
        description = "Input command payload for closing a resolved support ticket with customer satisfaction (CSAT) rating and commentary."
)
public record CloseTicketRequestDto(
        @Schema(
                description = "Customer satisfaction (CSAT) rating score from 1 (very unsatisfied) to 5 (completely satisfied).",
                examples = {"5"},
                required = true,
                minimum = "1",
                maximum = "5"
        )
        @NotNull(message = "CSAT rating score must not be null")
        @Min(value = 1, message = "CSAT rating score must be at least 1")
        @Max(value = 5, message = "CSAT rating score must not exceed 5")
        Integer rating,


        @Schema(
                description = "Optional qualitative feedback comments evaluating the resolution quality and agent attention.",
                examples = {"The issue was diagnosed quickly and payroll executed successfully without further locks. Excellent service!"},
                required = true,
                maxLength = 500,
                nullable = true
        )
        @Size(max = 500, message = "CSAT feedback comment must not exceed 500 characters")
        String comment

) {
}
