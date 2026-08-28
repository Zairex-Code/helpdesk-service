package org.softtech.domain.port.in;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;

import java.util.Objects;

/**
 * Inbound Port (Use Case interface) defining the reactive contract for transitioning
 * an assigned HelpDesk Support Ticket into the {@code IN_PROGRESS} lifecycle state,
 * signalling that the assigned specialist has begun active troubleshooting.
 *
 * <p>
 * Following Hexagonal Architecture and Domain-Driven Design (DDD) principles, this port
 * decouples driving adapters (REST controllers, worker automations) from domain lifecycle
 * rules and executes non-blockingly via SmallRye Mutiny {@link Uni}.
 * </p>
 */
public interface StartInvestigationUseCase {

    /**
     * Executes the reactive investigation-start workflow for an assigned support ticket.
     *
     * @param command the immutable input data carrier containing the ticket identifier. Must not be {@code null}.
     * @return a {@link Uni} emitting the updated {@link Ticket} aggregate root in {@code IN_PROGRESS} status.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws IllegalArgumentException if the ticket identifier inside {@code command} is blank.
     */
    Uni<Ticket> execute(StartInvestigationCommand command);

    /**
     * Immutable Command record encapsulating the validated parameters required to start an investigation.
     *
     * @param ticketId the technical UUID/BSON ID or business tracking number (e.g., "TICK-2026-0001"). Must not be {@code null} or blank.
     */
    record StartInvestigationCommand(String ticketId) {

        /**
         * Compact constructor enforcing strict non-null and non-blank invariants on command creation.
         */
        public StartInvestigationCommand {
            Objects.requireNonNull(ticketId, "Ticket identifier must not be null");

            if (ticketId.isBlank()) {
                throw new IllegalArgumentException("Ticket ID must not be blank");
            }
        }
    }
}
