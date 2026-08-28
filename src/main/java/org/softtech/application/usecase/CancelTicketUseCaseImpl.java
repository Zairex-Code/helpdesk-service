package org.softtech.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.event.TicketStatusChangedEvent;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.CancelTicketUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Instant;
import java.util.Objects;

/**
 * Primary Application Service implementing the {@link CancelTicketUseCase} inbound port.
 * <p>
 * Orchestrates the terminal cancellation of a support ticket within a fully reactive, non-blocking
 * Mutiny stream, enforcing the finite-state machine (FSM) invariant via {@link Ticket#cancel(String, Instant)},
 * persisting the state change, evicting stale Redis cache entries, and broadcasting a
 * {@link TicketStatusChangedEvent} to Kafka.
 * </p>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CancelTicketUseCaseImpl implements CancelTicketUseCase {

    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;
    private final TicketEventPublisherPort ticketEventPublisherPort;

    /**
     * Executes the reactive cancellation workflow.
     *
     * @param command the validated input data carrier. Must not be {@code null}.
     * @return a {@link Uni} emitting the finalized Ticket in {@code CANCELLED} status.
     */
    @Override
    public Uni<Ticket> execute(CancelTicketCommand command) {
        Objects.requireNonNull(command, "CancelTicketCommand must not be null");

        Instant now = Instant.now();
        String id = command.ticketId().trim();
        String reason = command.reason().trim();

        log.info("Processing cancellation request for ticket identifier {}", id);

        return findTicketById(id)
                .flatMap(existingTicket -> processCancellation(existingTicket, reason, now));
    }

    private Uni<Ticket> processCancellation(Ticket existingTicket, String reason, Instant cancelledAt) {
        TicketStatus previousStatus = existingTicket.getStatus();
        Ticket cancelledTicket = existingTicket.cancel(reason, cancelledAt);

        return ticketPersistencePort.update(cancelledTicket)
                .call(updatedTicket -> invalidateCache(updatedTicket))
                .call(updatedTicket -> publishStatusChangedEvent(updatedTicket, previousStatus, reason, cancelledAt))
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

    private Uni<Void> publishStatusChangedEvent(Ticket ticket,
                                                TicketStatus previousStatus,
                                                String reason,
                                                Instant occurredOn) {
        TicketStatusChangedEvent event = TicketStatusChangedEvent.from(ticket, previousStatus, reason, occurredOn);

        return ticketEventPublisherPort.publish(event)
                .onFailure().invoke(throwable -> log.error(
                        "CRITICAL: Failed to publish TicketStatusChangedEvent to Kafka for ticket {}: {}",
                        ticket.getTicketNumber(), throwable.getMessage(), throwable
                ));
    }
}
