package org.softtech.infrastructure.entrypoints.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Immutable Data Transfer Object (DTO) capturing the login credentials for the development-only
 * authentication endpoint.
 *
 * @param email the user email address (must be a valid email format).
 * @param password the user password (must not be blank).
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "LoginRequestDto",
        description = "Login credentials payload for the development-only authentication endpoint."
)
public record LoginRequestDto(

        @Schema(
                description = "User email address.",
                examples = {"soporte@softtech.com"},
                required = true
        )
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(
                description = "User password.",
                examples = {"dylan"},
                required = true
        )
        @NotBlank(message = "Password must not be blank")
        String password

) {
}
