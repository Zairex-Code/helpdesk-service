package org.softtech.domain.port.in;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;

import java.util.Objects;

/**
 * Inbound Port (Use Case interface) defining the reactive contract for cancelling
 * an existing HelpDesk Support Ticket, transitioning it into the terminal {@code CANCELLED} state.
 *
 * <p>
 * Following Hexagonal Architecture and Domain-Driven Design (DDD) principles, this port
 * decouples driving adapters from domain lifecycle rules and executes non-blockingly
 * via SmallRye Mutiny {@link Uni}.
 * </p>
 */
public interface CancelTicketUseCase {

    /**
     * Executes the reactive cancellation workflow for an existing support ticket.
     *
     * @param command the immutable input data carrier containing the ticket identifier and reason. Must not be {@code null}.
     * @return a {@link Uni} emitting the finalized {@link Ticket} aggregate root in {@code CANCELLED} status.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws IllegalArgumentException if the ticket identifier or reason is blank.
     */
    Uni<Ticket> execute(CancelTicketCommand command);

    /**
     * Immutable Command record encapsulating the validated parameters required to cancel a ticket.
     *
     * @param ticketId the technical UUID/BSON ID or business tracking number (e.g., "TICK-2026-0001"). Must not be {@code null} or blank.
     * @param reason the business or operational rationale for cancellation. Must not be {@code null} or blank.
     */
    record CancelTicketCommand(String ticketId, String reason) {

        /**
         * Compact constructor enforcing strict non-null and non-blank invariants on command creation.
         */
        public CancelTicketCommand {
            Objects.requireNonNull(ticketId, "Ticket identifier must not be null");
            Objects.requireNonNull(reason, "Cancellation reason must not be null");

            if (ticketId.isBlank()) {
                throw new IllegalArgumentException("Ticket ID must not be blank");
            }
            if (reason.isBlank()) {
                throw new IllegalArgumentException("Cancellation reason must not be blank");
            }
        }
    }
}
