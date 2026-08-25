package org.softtech.infrastructure.persistence.adapter;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.out.TicketPersistencePort;
import org.softtech.infrastructure.persistence.document.TicketDocument;
import org.softtech.infrastructure.persistence.mapper.TicketPersistenceMapper;
import org.softtech.infrastructure.persistence.repository.ReactiveTicketPanacheRepository;

import java.util.Objects;


/**
 * Secondary (Driven) Infrastructure Adapter implementing the {@link TicketPersistencePort} outbound contract.
 * <p>
 * Bridges the pure Hexagonal Domain boundary with MongoDB storage infrastructure using Quarkus Reactive Panache.
 * Coordinates entity lifecycle persistence, single-entity queries, and backpressure-aware cursor streaming by delegating
 * database commands to {@link ReactiveTicketPanacheRepository} and performing bidirectional domain-document mapping
 * via {@link TicketPersistenceMapper}.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Reliability (Data Integrity and Fault Tolerance) and CMMI Level 2/3
 * Data Management standards, all persistence operations execute asynchronously and non-blockingly over
 * the Netty Event Loop without blocking operating system threads.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TicketMongoAdapter implements TicketPersistencePort {

    private final ReactiveTicketPanacheRepository panacheRepository;
    private final TicketPersistenceMapper persistenceMapper;

    /**
     * Persists a newly created {@link Ticket} aggregate root into MongoDB.
     *
     * @param ticket the fully initialized domain aggregate to insert. Must not be {@code null}.
     * @return a {@link Uni} emitting the persisted {@link Ticket} aggregate after insertion.
     * @throws NullPointerException if {@code ticket} is {@code null}.
     */
    @Override
    public Uni<Ticket> persist(Ticket ticket) {
        Objects.requireNonNull(ticket, "Ticket aggregate must not be null for persistence");

        TicketDocument document = persistenceMapper.toDocument(ticket);

        log.debug("Persisting new ticket document {} into MongoDB collection", document.getTicketNumber());

        return panacheRepository.persist(document)
                .map(persistenceMapper::toDomain)
                .invoke(persistedTicket -> log.info("Successfully persisted ticket {} in MongoDB with ID {}",
                        persistedTicket.getTicketNumber(),
                        persistedTicket.getId()));
    }

    /**
     * Updates the persistent state and audit trail of an existing {@link Ticket} aggregate root.
     *
     * @param ticket the domain aggregate containing modified state. Must not be {@code null}.
     * @return a {@link Uni} emitting the updated {@link Ticket} aggregate after database replacement.
     * @throws NullPointerException if {@code ticket} is {@code null}.
     */
    @Override
    public Uni<Ticket> update(Ticket ticket) {
        Objects.requireNonNull(ticket, "Ticket aggregate must not be null for update");

        TicketDocument document = persistenceMapper.toDocument(ticket);

        log.debug("Updating ticket document {} in MongoDB collection", document.getTicketNumber());

        return panacheRepository.update(document)
                .map(persistenceMapper::toDomain)
                .invoke(updateTicket -> log.debug("Successfully updated ticket in {} MongoDB", updateTicket.getTicketNumber()));
    }

    /**
     * Finds a single ticket by its technical database persistence identifier (UUID String).
     *
     * @param id the technical identifier (UUID String). Must not be {@code null} or blank.
     * @return a {@link Uni} emitting the reconstituted {@link Ticket} aggregate, or {@code null} if not found.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is blank.
     */
    @Override
    public Uni<Ticket> findById(String id) {
        Objects.requireNonNull(id, "Ticket ID must not be null for repository lookup");
        String sanitizedId = id.trim();
        if (sanitizedId.isBlank()) {
            throw new IllegalArgumentException("Ticket ID cannot be blank");
        }

        return panacheRepository.findById(sanitizedId)
                .onItem().ifNotNull().transform(persistenceMapper::toDomain);
    }

    /**
     * Finds a single ticket by its unique business tracking sequence (e.g., "TICK-2026-0001").
     *
     * @param ticketNumber the business tracking sequence. Must not be {@code null} or blank.
     * @return a {@link Uni} emitting the reconstituted {@link Ticket} aggregate, or {@code null} if not found.
     * @throws NullPointerException if {@code ticketNumber} is {@code null}.
     * @throws IllegalArgumentException if {@code ticketNumber} is blank.
     */
    @Override
    public Uni<Ticket> findByTicketNumber(String ticketNumber) {
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null for repository lookup");
        String sanitizedTicketNumber = ticketNumber.trim();

        if (sanitizedTicketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number cannot be blank");
        }

        return panacheRepository.findByTicketNumber(sanitizedTicketNumber)
                .onItem().ifNotNull().transform(persistenceMapper::toDomain);
    }

    /**
     * Streams all tickets matching a specific lifecycle status with reactive backpressure.
     *
     * @param status the target lifecycle state to filter by. Must not be {@code null}.
     * @return a {@link Multi} stream emitting reconstituted {@link Ticket} domain aggregates.
     * @throws NullPointerException if {@code status} is {@code null}.
     */
    @Override
    public Multi<Ticket> findByStatus(TicketStatus status) {
        Objects.requireNonNull(status, "TicketStatus must not be null for status filtering");

        return panacheRepository.streamByStatus(status.name()).map(persistenceMapper::toDomain);
    }

    /**
     * Streams all tickets created by a specific enterprise requester or corporate tenant.
     *
     * @param requesterId the enterprise requester identifier. Must not be {@code null} or blank.
     * @return a {@link Multi} stream emitting reconstituted {@link Ticket} domain aggregates.
     * @throws NullPointerException if {@code requesterId} is {@code null}.
     * @throws IllegalArgumentException if {@code requesterId} is blank.
     */
    @Override
    public Multi<Ticket> findByRequesterId(String requesterId) {
        Objects.requireNonNull(requesterId, "Requester ID must not be null for requester filtering");
        String sanitizedRequesterId = requesterId.trim();
        if (sanitizedRequesterId.isBlank()){
            throw new IllegalArgumentException("Requester ID cannot be blank");
        }
        return panacheRepository.streamByRequesterId(sanitizedRequesterId).map(persistenceMapper::toDomain);
    }

    /**
     * Streams all active and closed support tickets stored in the persistence engine.
     *
     * @return a {@link Multi} stream emitting all persisted {@link Ticket} domain aggregates.
     */
    @Override
    public Multi<Ticket> findAll() {
        return panacheRepository.streamAllTicket().map(persistenceMapper::toDomain);
    }

    /**
     * Verifies whether a ticket with the given business sequence already exists.
     *
     * @param ticketNumber the business sequence tracking number to check. Must not be {@code null} or blank.
     * @return a {@link Uni} emitting {@code true} if a record exists; {@code false} otherwise.
     * @throws NullPointerException if {@code ticketNumber} is {@code null}.
     * @throws IllegalArgumentException if {@code ticketNumber} is blank.
     */
    @Override
    public Uni<Boolean> existsByTicketNumber(String ticketNumber) {
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null for existence check ");
        String sanitizedTicketNumber = ticketNumber.trim();
        if (sanitizedTicketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number cannot be blank");
        }

        return panacheRepository.existsByTicketNumber(sanitizedTicketNumber);
    }
}
