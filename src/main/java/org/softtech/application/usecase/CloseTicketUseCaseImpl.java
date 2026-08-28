package org.softtech.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.event.TicketClosedEvent;
import org.softtech.domain.exception.InvalidStatusTransitionException;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.port.in.CloseTicketUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Instant;
import java.util.Objects;


/**
 * Primary Application Service implementing the CloseTicketUseCase inbound port.
 * <p>
 * Orchestrates the permanent closure of a support ticket and optional capture of Customer Satisfaction (CSAT)
 * feedback within a fully reactive, non-blocking Mutiny pipeline. It retrieves the aggregate root, validates
 * finite-state machine (FSM) invariants via Ticket.closeWithFeedback(Feedback, Instant) (transitioning the state
 * to TicketStatus.CLOSED ), updates the durable store in MongoDB, evicts stale Redis cache entries,
 * and broadcasts a TicketClosedEvent to Apache Kafka to trigger customer engagement analytics and SLA compliance reporting.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Fault Tolerance) and CMMI Level 2/3 Service Operations,
 * secondary operations (cache eviction and event distribution) execute with non-blocking error containment,
 * guaranteeing that core administrative closures complete deterministically without blocking Netty event loops.
 * </p>
 *
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CloseTicketUseCaseImpl implements CloseTicketUseCase {

    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;
    private final TicketEventPublisherPort ticketEventPublisherPort;

    /**
     * Executes the reactive closure workflow for an existing support ticket.
     *
     * @param command the validated input data carrier containing ticket identifier and optional CSAT feedback. Must not be null.
     * @return a Uni emitting the finalized and persisted Ticket aggregate root in CLOSED status.
     * @throws NullPointerException if command is null.
     * @throws TicketNotFoundException if the targeted ticket does not exist in persistence.
     * @throws InvalidStatusTransitionException if the current state rejects closure.
     */
    @Override
    public Uni<Ticket> execute(CloseTicketCommand command) {

        Objects.requireNonNull(command, "CloseTicketCommand must not be null");

        Instant now = Instant.now();
        String id = command.ticketId().trim();
        Feedback feedback = buildFeedBackIfPresent(command.rating(), command.feedbackComment(), now);

        log.info("Proccessing ticket closure request for identifier {} with CSAT rating {}",
                id, command.rating() != null ? command.rating() : "N/A");

        return findTicketById(id).flatMap(existingTicket -> processClosure(existingTicket, feedback, now));
    }


    /**
     * Executes aggregate state mutation, database update, cache invalidation, and domain event dispatching.
     *
     * @param existingTicket the retrieved domain aggregate root prior to transition.
     * @param feedback the optional customer satisfaction evaluation.
     * @param closedAt the exact UTC timestamp of permanent closure.
     * @return a Uni emitting the finalized closed Ticket.
     */
    private Uni<Ticket> processClosure(Ticket existingTicket, Feedback feedback, Instant closedAt){
        Ticket closedTicket = existingTicket.closeWithFeedback(feedback,closedAt);

        return ticketPersistencePort.update(closedTicket)
                .call(updatedTicket -> invalidateCache(updatedTicket))
                .call(updatedTicket -> publicClosedEvent(updatedTicket, closedAt))
                .invoke(updatedTicket -> log.info(
                        "Ticket {} successfully CLOSED. total lifecycle finalized. SLA Breached: {}",
                        updatedTicket.getTicketNumber(),
                        updatedTicket.isResolutionBreached(closedAt)
                ));
    }


    /**
     * Constructs a domain Feedback Value Object if a rating score is supplied in the command.
     *
     * @param rating the numeric satisfaction score (1 to 5), or null.
     * @param comment the qualitative commentary, or null.
     * @param submittedAt the submission timestamp.
     * @return a validated Feedback instance, or null if no rating was provided.
     */
    private Feedback buildFeedBackIfPresent(Integer rating, String comment, Instant submittedAt){
        if (rating == null){
            return null;
        }

        return Feedback.of(rating, comment, submittedAt);
    }


    /**
     * Retrieves the ticket aggregate by searching both technical UUID and business sequence keys.
     *
     * @param id the lookup identifier (technical ID or business ticketNumber).
     * @return a Uni emitting the found Ticket, or failing with TicketNotFoundException.
     */
    private Uni<Ticket> findTicketById(String id){
        return ticketPersistencePort.findById(id)
                .onItem().ifNull().switchTo(() -> ticketPersistencePort.findByTicketNumber(id))
                .onItem().ifNull().failWith(() -> TicketNotFoundException.forTicketNumber(id));
    }


    /**
     * Proactively evicts stale entries from the Redis distributed cache across all lookup keys.
     *
     * @param ticket the closed ticket aggregate root.
     * @return a Uni completing the cache eviction with non-blocking error isolation.
     */
    private Uni<Void> invalidateCache(Ticket ticket){
        return ticketCachePort.evict(ticket.getId(), ticket.getTicketNumber())
                .onFailure().recoverWithItem(throwable -> {
                    log.warn("Non-fatal error: Failed to evict Redis cache for ticket {}: {}",
                            ticket.getTicketNumber(), throwable.getMessage());
                    return null;
                });
    }


    /**
     * Publishes the TicketClosedEvent to Apache Kafka to inform downstream analytical consumers.
     *
     * @param ticket the finalized ticket aggregate root.
     * @param occurredOn the closure timestamp.
     * @return a Uni completing event dispatching.
     */
    private Uni<Void>publicClosedEvent(Ticket ticket, Instant occurredOn){
        TicketClosedEvent event = TicketClosedEvent.from(ticket, occurredOn);

        return ticketEventPublisherPort.publish(event)
                .onFailure().invoke(throwable -> log.error(
                        "CRITICAL: Failed to publish TicketClosedEvent to Kafka for ticket {}: {}",
                        ticket.getTicketNumber(), throwable.getMessage(), throwable
                ));
    }
}
