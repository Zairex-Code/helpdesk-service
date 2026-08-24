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
import org.softtech.domain.port.in.AssignTicketUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Instant;
import java.util.Objects;


/**
 * Primary Application Service implementing the AssignTicketUseCase inbound port.
 * <p>
 * Orchestrates the technical assignment of an existing ticket to a support specialist within
 * a fully reactive, non-blocking Mutiny stream. It retrieves the aggregate, enforces finite-state
 * machine (FSM) invariants through the domain model (transitioning from TicketStatus.OPEN
 * to TicketStatus#ASSIGNED), updates the durable store in MongoDB, invalidates stale Redis cache
 * entries, and broadcasts a TicketStatusChangedEvent to Apache Kafka for real-time SLA metrics.
 * </p>
 * <p>
 * In compliance with ISO/IEC 25010 Reliability (Fault Tolerance and State Integrity) and CMMI Level 2/3
 * Service Operations, cache invalidation and messaging dispatches are isolated with resilient fallbacks
 * to guarantee that primary state transitions complete deterministically without blocking the Netty event loop.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AssignTicketUseCaseImpl implements AssignTicketUseCase {

    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;
    private final TicketEventPublisherPort ticketEventPublisherPort;

    /**
     * Executes the reactive assignment workflow for a support ticket.
     *
     * @param command the validated input data carrier containing ticket and agent identifiers. Must not be null.
     * @return a Uni emitting the updated and persisted Ticket aggregate root in {@code ASSIGNED} status.
     * @throws NullPointerException if command is null.
     * @throws TicketNotFoundException if the targeted ticket does not exist in persistence.
     * @throws InvalidStatusTransitionException if the current state rejects assignment.
     */
    @Override
    public Uni<Ticket> execute(AssignTicketCommand command) {
        Objects.requireNonNull(command, "AssignTicketCommand must not be null");

        Instant now = Instant.now();
        String id = command.ticketId().trim();
        String agentId = command.agentId().trim();

        log.info("Processing ticket assignment request for identifier {} to agent {}", id , agentId);

        return findTicketById(id)
                .flatMap(existingTicket -> processAssigment(existingTicket, agentId, now));

    }

    /**
     * Executes state mutation, persistence, cache invalidation, and event dispatching for the ticket.
     *
     * @param existingTicket the retrieved domain aggregate root prior to transition.
     * @param agentId the unique identifier of the assignee specialist.
     * @param assignedAt the UTC timestamp of the assignment.
     * @return a Uni emitting the finalized assigned Ticket.
     */
    private Uni<Ticket> processAssigment(Ticket existingTicket, String agentId, Instant assignedAt){
        TicketStatus previosStatus = existingTicket.getStatus();
        Ticket assignedTicket = existingTicket.assignToAgent(agentId, assignedAt);

        return ticketPersistencePort.update(assignedTicket)
                .call(updatedTicket -> invalidateCache(updatedTicket))
                .call(updatedTicket -> publishStatusChangedEvent(updatedTicket, previosStatus, agentId, assignedAt))
                .invoke(updatedTicket -> log.info(
                        "Ticket {} successfully assigned to agent {}. Status  transitioned from {} to {}",
                        updatedTicket.getTicketNumber(), agentId, previosStatus, updatedTicket.getStatus()
                ));
    }


    /**
     * Retrieves the ticket aggregate by searching both technical UUID and business sequence keys.
     *
     * @param id the lookup identifier (ID or ticketNumber).
     * @return a Uni emitting the found Ticket, or failing with TicketNotFoundException.
     */
    private Uni<Ticket> findTicketById(String id){
        return ticketPersistencePort.findById(id)
                .onItem().ifNull().switchTo(()-> ticketPersistencePort.findByTicketNumber(id))
                .onItem().ifNull().failWith(() -> TicketNotFoundException.forTicketNumber(id));
    }

    /**
     * Proactively evicts stale entries from the Redis cache across all lookup keys.
     *
     * @param ticket the modified ticket aggregate.
     * @return a Uni completing the cache eviction operation with non-blocking error containment.
     */
    private Uni<Void> invalidateCache(Ticket ticket){
        return ticketCachePort.evict(ticket.getId(), ticket.getTicketNumber())
                .onFailure().recoverWithItem(throwable -> {
                    log.warn("Non-fatal erro: Failed to evict Redis cache for ticket {}: {}",
                            ticket.getTicketNumber(), throwable.getMessage());
                    return null;
                });
    }

    /**
     * Publishes the TicketStatusChangedEvent to Apache Kafka to inform downstream consumers and SLA trackers.
     *
     * @param ticket the newly assigned ticket aggregate.
     * @param previousStatus the originating status before assignment.
     * @param agentId the assigned agent identifier.
     * @param occurredOn the assignment timestamp.
     * @return a Uni completing event dispatching.
     */
    private Uni<Void> publishStatusChangedEvent(Ticket ticket,
                                                TicketStatus previousStatus,
                                                String agentId,
                                                Instant occurredOn){
        String transitionReason = String.format("Assigned to specialist %s", agentId);
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(ticket, previousStatus,agentId, occurredOn);

        return ticketEventPublisherPort.publish(event)
                .onFailure()
                .invoke(throwable -> log.error("CRITICAL: Failed to publish TicketStatusChangedEvent to Kafka for ticket {}: {}",
                                ticket.getTicketNumber(), throwable.getMessage(),throwable));
    }
}
