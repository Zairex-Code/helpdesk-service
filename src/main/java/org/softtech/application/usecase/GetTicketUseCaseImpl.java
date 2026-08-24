package org.softtech.application.usecase;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.GetTicketUseCase;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.util.Objects;

/**
 * Primary Application Service implementing the GetTicketUseCase inbound query port.
 * <p>
 * Orchestrates non-blocking read and query operations for support tickets within the SoftTech Solutions ERP.
 * Implements the Cache-Aside architectural pattern for single-entity lookups (by technical ID or business ticket number)
 * using Redis via TicketCachePort with an automatic warm-up fallback to MongoDB via TicketPersistencePort.
 * Collections and streaming queries are streamed directly from persistence using SmallRye Mutiny Multi
 * to ensure backpressure control and prevent memory exhaustion.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Performance Efficiency (Sub-millisecond retrieval latency) and CMMI Level 2/3
 * Service Operations, all cache interactions contain isolated non-blocking error fallbacks, ensuring uninterrupted
 * querying even during transient cache infrastructure degradation.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class GetTicketUseCaseImpl implements GetTicketUseCase {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(15);

    private final TicketPersistencePort ticketPersistencePort;
    private final TicketCachePort ticketCachePort;


    /**
     * Retrieves a single support ticket by its technical database persistence identifier.
     * Implements non-blocking Cache-Aside: checks Redis first; on cache miss, queries MongoDB and warms up cache.
     *
     * @param id the technical identifier (UUID/BSON ID). Must not be null or blank.
     * @return a Uni emitting the matching Ticket, or failing with TicketNotFoundException.
     * @throws NullPointerException if {@code id} is null.
     * @throws IllegalArgumentException if {@code id} is blank.
     */
    @Override
    public Uni<Ticket> getById(String id) {
        Objects.requireNonNull(id, "Ticket must not be null");
        String sanitizedId = id.trim();

        if (sanitizedId.isBlank()){
            throw new IllegalArgumentException("Ticket ID cannot be blank");
        }

        log.debug("Fetching ticket by technical ID {} via CacheAside strategy", sanitizedId);

        return fetchFromCacheById(sanitizedId)
                .onItem().ifNull().switchTo(() -> fetchAndCacheFromPersistenceById(sanitizedId))
                .onItem().ifNull().failWith(() -> TicketNotFoundException.forId(sanitizedId));

    }

    /**
     * Retrieves a single support ticket by its business tracking sequence (e.g., "TICK-2026-0001").
     * Implements non-blocking Cache-Aside: checks Redis first; on cache miss, queries MongoDB and warms up cache.
     *
     * @param ticketNumber the business tracking sequence. Must not be null or blank.
     * @return a Uni emitting the matching Ticket, or failing with TicketNotFoundException.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    @Override
    public Uni<Ticket> getByTicketNumber(String ticketNumber) {
        Objects.requireNonNull(ticketNumber, "Ticket number not be null");
        String sanitizedTicketNumber = ticketNumber.trim();
        if (sanitizedTicketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number cannot be blank");
        }

        log.debug("Fetching ticker by business sequence {} via Cache-Aside strategy", sanitizedTicketNumber);

        return fetchFromCacheByTicketNumber(sanitizedTicketNumber)
                .onItem().ifNull().switchTo(() -> fetchAndCacheFromPersistenceByTicketNumber(sanitizedTicketNumber))
                .onItem().ifNull().failWith(() -> TicketNotFoundException.forTicketNumber(sanitizedTicketNumber));

    }

    /**
     * Streams all tickets filtered by their current lifecycle state.
     *
     * @param status the target lifecycle status to filter by. Must not be null.
     * @return a Multi stream emitting matching Ticket aggregates with reactive backpressure.
     * @throws NullPointerException if {@code status} is null.
     */
    @Override
    public Multi<Ticket> listByStatus(TicketStatus status) {
        Objects.requireNonNull(status, "Ticket status must not be null");
        log.debug("Streaming tickets filtered by status {}", status);

        return ticketPersistencePort.findByStatus(status);
    }


    /**
     * Streams all tickets registered by a specific enterprise requester or corporate tenant.
     *
     * @param requesterId the enterprise requester identifier. Must not be null or blank.
     * @return a Multi stream emitting matching Ticket aggregates reactively.
     * @throws NullPointerException if requesterId is null.
     * @throws IllegalArgumentException if requesterId is blank.
     */
    @Override
    public Multi<Ticket> listByRequesterId(String requesterId) {
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        String sanitizedRequestId = requesterId.trim();
        if (sanitizedRequestId.isBlank()){
            throw new IllegalArgumentException("Requester ID cannot be blank");
        }

        log.debug("Streaming tickets registered by requesterIdd {}", sanitizedRequestId);

        return ticketPersistencePort.findByRequesterId(sanitizedRequestId);
    }

    /**
     * Streams all support tickets across the entire HelpDesk domain.
     *
     * @return a Multi stream emitting all active and historical Ticket instances.
     */
    @Override
    public Multi<Ticket> listAll() {
        log.debug("Streaming all domain ticket from persistence engine");
        return ticketPersistencePort.findAll();
    }

// ====================================================================================

    /**
     * Queries Redis for a ticket by technical ID with non-fatal error recovery.
     *
     * @param id the technical identifier.
     * @return a Uni emitting the cached Ticket, or null if missing or cache fails.
     */
    private Uni<Ticket> fetchFromCacheById(String id){
        return ticketCachePort.getById(id).onFailure().recoverWithItem(throwable -> {
            log.warn("Cache read failed for ticket ID {}. Falling back to persistence: {}",
                    id, throwable.getMessage());
            return null;
        });
    }

    /**
     * Queries Redis for a ticket by business ticketNumber with non-fatal error recovery.
     *
     * @param ticketNumber the business sequence tracking number.
     * @return a Uni emitting the cached Ticket, or null if missing or cache fails.
     */
    private Uni<Ticket> fetchFromCacheByTicketNumber(String ticketNumber){
        return ticketCachePort.getByTicketNumber(ticketNumber).onFailure().recoverWithItem(throwable -> {
            log.warn("Cache read failed from ticket number {}. Falling back to persistence: {}",
                    ticketNumber, throwable.getMessage());
            return null;
        });
    }

    /**
     * Queries MongoDB by technical ID and proactively warms up Redis cache on cache miss.
     *
     * @param id the technical identifier.
     * @return a Uni emitting the persisted Ticket, or null if not found in database.
     */
    private Uni<Ticket> fetchAndCacheFromPersistenceById(String id){
        return ticketPersistencePort.findById(id).onItem().ifNotNull().call(ticket -> warmUpCache(ticket));
    }


    /**
     * Queries MongoDB by business ticketNumber and proactively warms up Redis cache on cache miss.
     *
     * @param ticketNumber the business sequence tracking number.
     * @return a Uni emitting the persisted Ticket, or null if not found in database.
     */
    private Uni<Ticket> fetchAndCacheFromPersistenceByTicketNumber(String ticketNumber){
        return ticketPersistencePort.findByTicketNumber(ticketNumber)
                .onItem()
                .ifNotNull()
                .call(ticket -> warmUpCache(ticket));
    }


    /**
     * Asynchronously writes a ticket aggregate to Redis with non-blocking error isolation.
     *
     * @param ticket the ticket aggregate to store in cache.
     * @return a Uni completing the cache write operation.
     */
    private Uni<Void> warmUpCache(Ticket ticket){
        return ticketCachePort.put(ticket, DEFAULT_CACHE_TTL)
                .onFailure().recoverWithItem(throwable -> {
                   log.warn("Non-fatal error: Failed to populate Redis cache ticket {}: {}",
                           ticket.getTicketNumber(), throwable.getMessage());
                   return null;
                });
    }
}
