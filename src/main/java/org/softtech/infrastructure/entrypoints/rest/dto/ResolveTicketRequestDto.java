package org.softtech.infrastructure.entrypoints.rest.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Immutable Data Transfer Object (DTO) capturing HTTP command payloads for technical ticket resolution.
 * <p>
 * Enforces declarative input validation constraints at the REST perimeter via Jakarta Validation (Bean Validation 3.0),
 * ensuring that the support engineer provides an exhaustive diagnostic root-cause explanation and technical resolution
 * summary before the system triggers the status transition to {@code RESOLVED}.
 * Annotated with MicroProfile OpenAPI 3.1 / 4.0+ schema specifications using array-based examples for deterministic
 * SDK compilation and interactive API exploration.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Data Integrity and Non-repudiation) and
 * CMMI Level 2/3 Verification & Service Management standards, this record is shallowly immutable, thread-safe,
 * and deserialized efficiently without intermediate reflection proxies on the Netty Event Loop.
 * </p>
 *
 * @param resolutionNotes comprehensive diagnostic explanation, root cause analysis, and remediation steps applied.
 *                        Must not be blank (10-2000 characters).
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "ResolveTicketRequestDto",
        description = "Input command payload for marking and activate support ticket as technically resolved."
)
public record ResolveTicketRequestDto(

        @Schema(
                description = "Technical diagnostic explanation, root cause analysis, and applied fix.",
                examples = {"Resolved PostgreSQL connection poor starvation in ERP-RRHH batch worker by optimizing HikariCO connection timeout and tuning max-pool-size from 20 to 50."},
                required = true,
                minLength = 10,
                maxLength = 2000

        )
        @NotBlank(message = "Resolution notes must not be blank")
        @Size(min = 10, max = 2000, message = "Resolution notes must contain between 10 and 2000 characters")
        String resolutionNotes
) {
}
