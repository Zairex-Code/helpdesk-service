package org.softtech.infrastructure.entrypoints.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Immutable Data Transfer Object (DTO) capturing HTTP request payloads for ticket cancellation.
 * <p>
 * Enforces declarative input validation constraints at the REST boundary via Jakarta Validation (Bean Validation 3.0),
 * ensuring that cancellation requests contain a well-formed business rationale prior to triggering the use case.
 * </p>
 *
 * @param reason the business or operational rationale for cancellation. Must not be blank (3-500 characters).
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "CancelTicketRequestDto",
        description = "Input command payload for cancelling a support ticket with a mandatory business rationale."
)
public record CancelTicketRequestDto(

        @Schema(
                description = "Business or operational rationale for cancellation (duplicate, invalid, retracted).",
                examples = {"Duplicate ticket already tracked under TICK-2026-0041"},
                required = true,
                minLength = 3,
                maxLength = 500
        )
        @NotBlank(message = "Cancellation reason must not be blank")
        @Size(min = 3, max = 500, message = "Cancellation reason must contain between 3 and 500 characters")
        String reason

) {
}
