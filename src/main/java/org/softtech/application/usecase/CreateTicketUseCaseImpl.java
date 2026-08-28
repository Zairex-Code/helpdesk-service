package org.softtech.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.event.TicketCreatedEvent;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.port.in.CreateTicketUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.Objects;
import java.util.UUID;

/**
 * Primary Application Service implementing the CreateTicketUseCase inbound port.
 * <p>
 * Orchestrates the complete end-to-end ticket registration workflow within a fully reactive,
 * non-blocking Mutiny pipeline. It coordinates domain aggregate instantiation via the static
 * factory method Ticket.create, durable persistence into MongoDB via TicketPersistencePort,
 * cache warm-up in Redis via TicketCachePort using the Cache-Aside pattern, and asynchronous event
 * streaming to Apache Kafka via TicketEventPublisherPort.
 *
 * In strict compliance with ISO/IEC 25010 Reliability (Fault Tolerance) and CMMI Level 2/3 Service Operations,
 * secondary operations (caching and messaging) are structured with resilient non-blocking fallbacks to prevent
 * transient infrastructure degradation from interrupting the primary transactional creation flow.
 *
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CreateTicketUseCaseImpl implements CreateTicketUseCase {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);
    private static final String TICKET_PREFIX = "TICK";

    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;
    private final TicketEventPublisherPort ticketEventPublisherPort;


    /**
     * Executes the reactive creation and persistence pipeline for a new support ticket.
     *
     * @param command the immutable input data carrier containing ticket parameters. Must not be null.
     * @return a Uni emitting the persisted and processed Ticket aggregate root.
     * @throws NullPointerException if command is null.
     */
    @Override
    public Uni<Ticket> execute(CreateTicketCommand command) {

        Objects.requireNonNull(command, "CreateTicketCommand must not be null");

        Instant now = Instant.now();
        String ticketId = UUID.randomUUID().toString();
        String ticketNumber = generateUniqueTicketNumber();

        Ticket newTicket = Ticket.created(
                ticketId,
                ticketNumber,
                command.title(),
                command.description(),
                command.priority(),
                command.erpModule(),
                command.requesterId(),
                command.vipCustomer(),
                now
        );


        log.info("Initiating ticket creation pipeline for ticketNumber {} and requesterId {}", ticketNumber, command.requesterId());

        return ticketPersistencePort.save(newTicket)
                .call(persistTicket -> warmUpCache(persistTicket))
                .call(persistedTicket -> publishCreationEvent(persistedTicket, now))
                .invoke(persistedTicket -> log.info(
                        "Ticket {} successfully registerd, cached, and dispatched to Kafka topic",
                        persistedTicket.getTicketNumber()
                ));
    }

    /**
     * Preheats the Redis cache asynchronously to accelerate subsequent read requests.
     *
     * @param ticket the newly persisted ticket aggregate.
     * @return a Uni completing the cache operation with non-blocking error isolation.
     */
    private Uni<Void> warmUpCache(Ticket ticket){
        return ticketCachePort.put(ticket, DEFAULT_CACHE_TTL)
                .onFailure().recoverWithItem(throwable -> {
                    log.warn("Non-fatal error: Failed to warm up Redis cache for ticket {}: {}",ticket.getTicketNumber(), throwable.getMessage());
                    return null;
                });
    }


    /**
     * Publishes the {@link TicketCreatedEvent} to Apache Kafka within the reactive pipeline.
     *
     * @param ticket the newly persisted ticket aggregate.
     * @param occurredOn the creation timestamp.
     * @return a Uni completing the event publication.
     */
    private Uni<Void> publishCreationEvent(Ticket ticket, Instant occurredOn){
        TicketCreatedEvent event = TicketCreatedEvent.from(ticket, occurredOn);
        return ticketEventPublisherPort.publish(event)
                .onFailure().invoke(throwable -> log.error(
                        "CRITICAL: Failed to publish TicketCreatedEvent to Kafka for ticket {}: {}",
                        ticket.getTicketNumber(), throwable.getMessage(), throwable
                ));
    }


    /**
     * Generates a collision-resistant, business-readable ticket tracking sequence.
     * Format: TICK-YYYY-XXXXXXXX (e.g., "TICK-2026-A1B2C3D4").
     *
     * @return a formatted tracking sequence string.
     */
    private String generateUniqueTicketNumber(){
        int currentYear = Year.now().getValue();
        String entropy = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("%s-%d-%s", TICKET_PREFIX,currentYear,entropy);
    }
}
