package org.softtech.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.event.TicketStatusChangedEvent;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.StartInvestigationUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Instant;
import java.util.Objects;

/**
 * Primary Application Service implementing the {@link StartInvestigationUseCase} inbound port.
 * <p>
 * Orchestrates the transition of an assigned ticket into {@code IN_PROGRESS} within a fully reactive,
 * non-blocking Mutiny stream, enforcing the finite-state machine (FSM) invariant via
 * {@link Ticket#startInvestigation(Instant)}, persisting the state change, evicting stale Redis
 * cache entries, and broadcasting a {@link TicketStatusChangedEvent} to Kafka.
 * </p>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class StartInvestigationUseCaseImpl implements StartInvestigationUseCase {

    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;
    private final TicketEventPublisherPort ticketEventPublisherPort;

    /**
     * Executes the reactive investigation-start workflow.
     *
     * @param command the validated input data carrier. Must not be {@code null}.
     * @return a {@link Uni} emitting the updated Ticket in {@code IN_PROGRESS} status.
     */
    @Override
    public Uni<Ticket> execute(StartInvestigationCommand command) {
        Objects.requireNonNull(command, "StartInvestigationCommand must not be null");

        Instant now = Instant.now();
        String id = command.ticketId().trim();

        log.info("Processing investigation-start request for ticket identifier {}", id);

        return findTicketById(id)
                .flatMap(existingTicket -> processInvestigation(existingTicket, now));
    }

    private Uni<Ticket> processInvestigation(Ticket existingTicket, Instant startedAt) {
        TicketStatus previousStatus = existingTicket.getStatus();
        Ticket inProgressTicket = existingTicket.startInvestigation(startedAt);

        return ticketPersistencePort.update(inProgressTicket)
                .call(updatedTicket -> invalidateCache(updatedTicket))
                .call(updatedTicket -> publishStatusChangedEvent(updatedTicket, previousStatus, startedAt))
                .invoke(updatedTicket -> log.info(
                        "Ticket {} transitioned from {} to {}",
                        updatedTicket.getTicketNumber(), previousStatus, updatedTicket.getStatus()
                ));
    }

    private Uni<Ticket> findTicketById(String id) {
        return ticketPersistencePort.findById(id)
                .onItem().ifNull().switchTo(() -> ticketPersistencePort.findByTicketNumber(id))
                .onItem().ifNull().failWith(() -> TicketNotFoundException.forTicketNumber(id));
    }

    private Uni<Void> invalidateCache(Ticket ticket) {
        return ticketCachePort.evict(ticket.getId(), ticket.getTicketNumber())
                .onFailure().recoverWithItem(throwable -> {
                    log.warn("Non-fatal error: Failed to evict Redis cache for ticket {}: {}",
                            ticket.getTicketNumber(), throwable.getMessage());
                    return null;
                });
    }

    private Uni<Void> publishStatusChangedEvent(Ticket ticket, TicketStatus previousStatus, Instant occurredOn) {
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(
                ticket,
                previousStatus,
                "Technical investigation initiated",
                occurredOn
        );

        return ticketEventPublisherPort.publish(event)
                .onFailure().invoke(throwable -> log.error(
                        "CRITICAL: Failed to publish TicketStatusChangedEvent to Kafka for ticket {}: {}",
                        ticket.getTicketNumber(), throwable.getMessage(), throwable
                ));
    }
}
