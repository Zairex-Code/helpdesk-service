package org.softtech.domain.port.in;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Ticket;

import java.util.Objects;


/**
 * Inbound Port (Use Case interface) defining the reactive contract for permanently closing
 * an existing HelpDesk Support Ticket and recording optional Customer Satisfaction (CSAT) feedback.
 *
 * Following Hexagonal Architecture and Domain-Driven Design (DDD) principles, this port
 * decouples driving entrypoint adapters (such as REST controllers, automated SLA cron workers,
 * or customer survey webhooks) from underlying domain lifecycle rules. It executes non-blocking
 * asynchronous operations via SmallRye Mutiny {@link Uni}.
 *
 *
 */
public interface CloseTicketUseCase {

    /**
     * Executes the permanent closure of a support ticket within a reactive stream.
     *
     * @param command the immutable input data carrier containing ticket identification and optional CSAT feedback. Must not be {@code null}.
     * @return a {@link Uni} emitting the finalized and persisted {@link Ticket} aggregate root in {@code CLOSED} status upon success.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws IllegalArgumentException if string parameters inside {@code command} are blank or rating bounds are violated.
     */
    Uni<Ticket> execute(CloseTicketCommand command);



    /**
     * Immutable Command record encapsulating the validated parameters required to close a ticket.
     *
     * @param ticketId the technical UUID/BSON ID or business tracking number (e.g., "TICK-2026-0001"). Must not be {@code null} or blank.
     * @param rating optional customer satisfaction score on a scale of 1 to 5. Can be {@code null} if auto-closed or survey was skipped.
     * @param feedbackComment optional qualitative remarks or suggestions from the requester. Can be {@code null}.
     */
    record CloseTicketCommand(String ticketId,
                              Integer rating,
                              String feedbackComment){

        // Compact constructor enforcing non-null identifiers and validating rating boundaries if provided.
        public CloseTicketCommand{
            Objects.requireNonNull(ticketId, "Ticket ID must not be null");

            if (ticketId.isBlank()){
                throw new IllegalArgumentException("Ticket Id cannot be blank");
            }
            if (rating != null && (rating < Feedback.MIN_RATING || rating > Feedback.MAX_RATING)){
                throw new IllegalArgumentException(
                        String.format("Feedback rating must be between %d and %d. Provided: %d",
                                Feedback.MIN_RATING, Feedback.MIN_RATING, rating)
                );
            }
        }

    }
}
