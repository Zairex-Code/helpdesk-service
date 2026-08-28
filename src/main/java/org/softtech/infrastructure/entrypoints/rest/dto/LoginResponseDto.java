package org.softtech.infrastructure.entrypoints.rest.dto;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Immutable Data Transfer Object (DTO) carrying the signed JWT token returned by the
 * development-only login endpoint.
 *
 * @param token the signed JWT access token.
 * @param tokenType the authentication scheme (always "Bearer").
 * @param expiresInSeconds the token time-to-live in seconds.
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "LoginResponseDto",
        description = "Signed JWT access token returned by the development-only login endpoint."
)
public record LoginResponseDto(

        @Schema(description = "Signed JWT access token.", examples = {"eyJraWQiOi..."})
        String token,

        @Schema(description = "Authentication scheme.", examples = {"Bearer"})
        String tokenType,

        @Schema(description = "Token time-to-live in seconds.", examples = {"3600"})
        long expiresInSeconds

) {
}
