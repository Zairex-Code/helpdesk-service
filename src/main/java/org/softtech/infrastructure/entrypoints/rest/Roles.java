package org.softtech.infrastructure.entrypoints.rest;

/**
 * Canonical RBAC role constants for the HelpDesk microservice.
 * <p>
 * Centralizes the JWT claim role identifiers enforced via {@code jakarta.annotation.security.RolesAllowed}
 * on the REST entrypoint adapters, guaranteeing a single source of truth across the platform.
 * </p>
 */
public final class Roles {

    /**
     * Corporate requester role: creates and closes their own support tickets.
     */
    public static final String CLIENTE = "CLIENTE";

    /**
     * Technical support role: assigns, investigates and resolves support tickets.
     */
    public static final String SOPORTE_TI = "SOPORTE_TI";

    /**
     * Administrative / supervisor role: full lifecycle access including cancellation.
     */
    public static final String ADMIN = "ADMIN";

    private Roles() {
        // Prevent instantiation of a utility constants holder.
    }
}
