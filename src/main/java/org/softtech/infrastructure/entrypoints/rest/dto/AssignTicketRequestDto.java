package org.softtech.infrastructure.entrypoints.rest.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Immutable Data Transfer Object (DTO) capturing HTTP request payloads for ticket assignment operations.
 * <p>
 * Enforces declarative input validation constraints at the REST boundary via Jakarta Validation (Bean Validation 3.0),
 * ensuring that assignment requests contain a well-formed specialist identifier prior to triggering the use case workflow.
 * Documented with MicroProfile OpenAPI 3.1 / 4.0+ annotations using array-based examples for deterministic SDK compilation.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Data Integrity and Input Robustness) and
 * CMMI Level 2/3 Verification standards, this record provides shallow immutability and leverages
 * Jacksonized builder instantiation to prevent state tampering on the Netty Event Loop.
 * </p>
 *
 * @param assignedAgentId the unique identifier of the support specialist or IT engineer handling the ticket.
 *                        Must not be blank (3-50 characters).
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "AssignTicketRequestDto",
        description = "Input command payload for assigning an open or pending support ticket to a dedicated IT specialist."
)
public record AssignTicketRequestDto(
        @Schema(
                description = "Unique identifier of the IT support specialist assigned to diagnose and resolve the incident.",
                examples = {"AGT-TI-5042"},
                required = true,
                minLength = 3,
                maxLength = 50
        )
        @NotBlank(message = "Assigned agent ID must not be blank")
        @Size(min = 3, max = 50, message = "Assigned agent ID must contain between 3 and 50 characters")
        String assignedAgentId

) {
}
