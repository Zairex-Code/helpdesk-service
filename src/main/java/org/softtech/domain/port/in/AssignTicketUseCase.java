package org.softtech.domain.port.in;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;

import java.util.Objects;

/**
 * Inbound Port (Use Case interface) defining the reactive contract for assigning
 * an existing HelpDesk Support Ticket to a designated support specialist.
 * <p>
 * Following Hexagonal Architecture and Domain-Driven Design (DDD) principles, this port
 * decouples driving adapters (such as REST controllers, automated triage workers, or event consumers)
 * from the underlying domain orchestration. It executes within a non-blocking reactive
 * stream powered by SmallRye Mutiny {@link Uni}.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
public interface AssignTicketUseCase {

    /**
     * Executes the assignment of a support ticket to an agent within a reactive stream.
     *
     * @param command the immutable input data carrier containing ticket and agent identifiers. Must not be {@code null}.
     * @return a {@link Uni} emitting the updated and persisted {@link Ticket} aggregate root upon success.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws IllegalArgumentException if string parameters inside {@code command} are blank.
     */
    Uni<Ticket> execute(AssignTicketCommand command);


    /**
     * Immutable Command record encapsulating the validated parameters required for ticket assignment.
     *
     * @param ticketId the technical UUID/BSON ID or business tracking number (e.g., "TICK-2026-0001"). Must not be {@code null} or blank.
     * @param agentId the unique identifier of the support specialist receiving the ticket. Must not be {@code null} or blank.
     */
    record AssignTicketCommand(String ticketId,
                               String agentId){

        /**
         * Compact constructor enforcing strict non-null and non-blank invariants on command creation.
         */
        public AssignTicketCommand {
            Objects.requireNonNull(ticketId, "Ticket identifier must not be null");
            Objects.requireNonNull(agentId, "Agent ID must not be null");

            if (ticketId.isBlank()){
                throw new IllegalArgumentException("Ticket ID must not be blank");
            }
            if (agentId.isBlank()){
                throw new IllegalArgumentException("Agent ID must not be blank");
            }
        }
    }
}
