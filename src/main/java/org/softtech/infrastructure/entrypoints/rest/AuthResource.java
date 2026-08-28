package org.softtech.infrastructure.entrypoints.rest;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.softtech.infrastructure.entrypoints.rest.dto.ErrorResponseDto;
import org.softtech.infrastructure.entrypoints.rest.dto.LoginRequestDto;
import org.softtech.infrastructure.entrypoints.rest.dto.LoginResponseDto;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Development-only authentication entrypoint issuing signed JWT tokens for local testing.
 * <p>
 * This resource only exists when the application runs in the {@code dev} build profile
 * (i.e. {@code ./mvnw quarkus:dev}). It is intentionally removed from production builds:
 * in real deployments authentication is delegated to the corporate Identity Provider (IdP)
 * per RF-01. The embedded private key is a development secret and MUST NOT be used in production.
 * </p>
 */
@Slf4j
@Path("/api/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile("dev")
public class AuthResource {

    private static final long TOKEN_TTL_SECONDS = 3600;
    private static final String PRIVATE_KEY_RESOURCE = "/privateKey.pem";

    private static final Map<String, String> DEMO_USERS = Map.of(
            "cliente@softtech.com", Roles.CLIENTE,
            "soporte@softtech.com", Roles.SOPORTE_TI,
            "admin@softtech.com", Roles.ADMIN
    );

    private static final String DEMO_PASSWORD = "dylan";

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "https://auth.softtech.com/oauth2/token")
    String issuer;

    /**
     * Authenticates a development user and returns a signed JWT access token.
     *
     * @param request the login credentials.
     * @return HTTP 200 with the signed token, or HTTP 401 with an RFC 7807 error payload.
     */
    @POST
    @Path("/login")
    @PermitAll
    public Response login(@Valid LoginRequestDto request) {
        String email = request.email().trim();
        String role = DEMO_USERS.get(email);

        if (role == null || !DEMO_PASSWORD.equals(request.password())) {
            log.warn("Development login rejected for user {}", email);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(buildInvalidCredentialsError())
                    .build();
        }

        String token = generateToken(email, role);
        log.info("Development login successful for user {} with role {}", email, role);

        return Response.ok(new LoginResponseDto(token, "Bearer", TOKEN_TTL_SECONDS)).build();
    }

    private String generateToken(String email, String role) {
        Instant now = Instant.now();
        return Jwt.issuer(issuer)
                .subject(email)
                .upn(email)
                .groups(Set.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_TTL_SECONDS))
                .sign(loadPrivateKey());
    }

    private PrivateKey loadPrivateKey() {
        try (InputStream in = getClass().getResourceAsStream(PRIVATE_KEY_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Development private key not found on classpath: " + PRIVATE_KEY_RESOURCE);
            }
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return KeyUtils.decodePrivateKey(pem);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load development JWT signing key", e);
        }
    }

    private ErrorResponseDto buildInvalidCredentialsError() {
        return ErrorResponseDto.builder()
                .type("https://helpdesk.softtech.com/errors/invalid-credentials")
                .title("Invalid Credentials")
                .status(Response.Status.UNAUTHORIZED.getStatusCode())
                .detail("The provided email or password is incorrect.")
                .errorCode("ERR_HD_INVALID_CREDENTIALS")
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .build();
    }
}
