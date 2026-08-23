package org.softtech.domain.port.in;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;

import java.util.Objects;


/**
 * Inbound Port (Use Case interface) defining the reactive contract for marking
 * an active HelpDesk Support Ticket as resolved with verified technical resolution notes.
 *
 * Following Hexagonal Architecture and Domain-Driven Design (DDD) principles, this port
 * decouples driving entrypoint adapters (such as REST controllers, webhook dispatchers, or automation bots)
 * from core domain lifecycle rules. It executes non-blocking asynchronous operations
 * via SmallRye Mutiny {@link Uni}.
 *
 *
 */
public interface ResolveTicketUseCase {

    /**
     * Executes the technical resolution of a support ticket within a reactive stream.
     *
     * @param command the immutable input data carrier containing ticket identification and resolution notes. Must not be {@code null}.
     * @return a {@link Uni} emitting the resolved and persisted {@link Ticket} aggregate root upon success.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws IllegalArgumentException if string parameters inside {@code command} are blank.
     */
    Uni<Ticket> execute(ResolveTicketCommand command);


    /**
     * Immutable Command record encapsulating the validated parameters required to resolve a ticket.
     *
     * @param ticketId the technical UUID/BSON ID or business tracking number (e.g., "TICK-2026-0001"). Must not be {@code null} or blank.
     * @param resolutionNotes the concise technical summary of the fix or workaround applied. Must not be {@code null} or blank.
     */
    record ResolveTicketCommand(String ticketId,
                                String resolutionNotes){


        // Compact constructor enforcing strict non-null and non-blank invariants on command creation.
        public ResolveTicketCommand{
            Objects.requireNonNull(ticketId, "Ticket Id must not be null");
            Objects.requireNonNull(resolutionNotes, "Resolution notes must not be null");

            if (ticketId.isBlank()){
                throw new IllegalArgumentException("Ticket Id must not be blank");
            }
            if (resolutionNotes.isBlank()){
                throw new IllegalArgumentException("Resolution notes must not be blank");
            }
        }

    }
}
