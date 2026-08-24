package org.softtech.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.event.TicketStatusChangedEvent;
import org.softtech.domain.exception.InvalidStatusTransitionException;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.ResolveTicketUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Instant;
import java.util.Objects;


/**
 * Primary Application Service implementing the {@link ResolveTicketUseCase} inbound port.
 * <p>
 * Orchestrates the technical resolution of an active support ticket within a fully reactive,
 * non-blocking Mutiny stream. It retrieves the aggregate root, enforces finite-state machine (FSM)
 * invariants via Ticket.resolve(String, Instant) (transitioning the state to TicketStatus.RESOLVED),
 * updates the durable document store in MongoDB, invalidates stale Redis cache projections, and broadcasts
 * a TicketStatusChangedEvent to Apache Kafka to halt MTTR calculation timers across distributed consumers.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Fault Tolerance and State Integrity) and CMMI Level 2/3
 * Service Operations, secondary operations (caching and event streaming) are isolated with non-blocking error fallbacks
 * to ensure that core technical resolutions are persisted reliably without blocking the Netty event loop.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ResolveTicketUseCaseImpl implements ResolveTicketUseCase {


    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;
    private final TicketEventPublisherPort ticketEventPublisherPort;


    /**
     * Executes the reactive resolution workflow for an active support ticket.
     *
     * @param command the validated input data carrier containing ticket identifier and resolution notes. Must not be null.
     * @return a Uni emitting the resolved and persisted Ticket aggregate root in RESOLVED status.
     * @throws NullPointerException if command is null.
     * @throws TicketNotFoundException if the targeted ticket does not exist in persistence.
     * @throws InvalidStatusTransitionException if the current state rejects resolution.
     */
    @Override
    public Uni<Ticket> execute(ResolveTicketCommand command) {
        Objects.requireNonNull(command, "ResolveTicketCommand must not be null");

        Instant now = Instant.now();
        String id = command.ticketId().trim();
        String resolutionNotes = command.resolutionNotes().trim();

        log.info("Proccessing resolution request for ticket identifier {}", id);

        return findTicketById(id)
                .flatMap(existingTicket -> processResolution(existingTicket, resolutionNotes, now));
    }


    /**
     * Executes aggregate state mutation, database update, cache invalidation, and domain event dispatching.
     *
     * @param existingTicket the retrieved domain aggregate root prior to transition.
     * @param resolutionNotes the verified technical explanation of the fix applied.
     * @param resolvedAt the exact UTC timestamp of technical resolution.
     * @return a Uni emitting the finalized resolved Ticket.
     */
    private Uni<Ticket> processResolution(Ticket existingTicket, String resolutionNotes, Instant resolvedAt){
        TicketStatus preciousStatus = existingTicket.getStatus();
        Ticket resolvedTicket = existingTicket.resolve(resolutionNotes, resolvedAt);

        return ticketPersistencePort.update(resolvedTicket)
                .call(updatedTicket -> invalidateCache(updatedTicket))
                .call(updatedTicket -> publishStatusChangedEvent(updatedTicket, preciousStatus, resolutionNotes, resolvedAt))
                .invoke(updatedTicket -> log.info(
                        "Ticket {} successfully market as RESOLVED. resolution notes recorded. SLA Breached: {}",
                        updatedTicket.getTicketNumber(),
                        updatedTicket.isResolutionBreached(resolvedAt)
                ));
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
     * @param ticket the resolved ticket aggregate root.
     * @return a Uni completing the cache eviction with non-blocking error isolation.
     */
    private Uni<Void> invalidateCache(Ticket ticket){
        return ticketCachePort.evict(ticket.getId(), ticket.getTicketNumber())
                .onFailure().recoverWithItem(throwable -> {
                    log.warn("Non-fatal error: Failed to evict Redis cache for ticket {}. {}",
                            ticket.getTicketNumber(), throwable.getMessage());

                    return null;
                });
    }


    /**
     * Publishes the TicketStatusChangedEvent to Apache Kafka to inform downstream consumers and analytics engines.
     *
     * @param ticket the newly resolved ticket aggregate root.
     * @param previousStatus the originating status before resolution.
     * @param resolutionNotes the diagnostic resolution notes.
     * @param occurredOn the resolution timestamp.
     * @return a Uni completing event dispatching.
     */
    private Uni<Void> publishStatusChangedEvent(Ticket ticket,
                                                TicketStatus previousStatus,
                                                String resolutionNotes,
                                                Instant occurredOn){
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(
                ticket,
                previousStatus,
                resolutionNotes,
                occurredOn
        );

        return ticketEventPublisherPort.publish(event)
                .onFailure().invoke(throwable -> log.error(
                        "CRITICAL: Failed to publish TicketStatusChangedEvent to Kafka  for ticket {}: {}",
                        ticket.getTicketNumber(), throwable.getMessage(),throwable
                ));
    }
}
