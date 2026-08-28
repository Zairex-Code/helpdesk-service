package org.softtech.domain.port.out;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;

/**
 * Outbound Port (Driven / Secondary Port) defining the reactive persistence contract
 * for the Ticket Aggregate Root within the SoftTech Solutions ERP ecosystem.
 * <p>
 * In strict compliance with Hexagonal Architecture (Ports and Adapters) and Domain-Driven
 * Design (DDD), this interface decouples core domain logic from underlying NoSQL database
 * technology (MongoDB Panache Reactive). It provides non-blocking, backpressure-aware data
 * access primitives using SmallRye Mutiny Uni for single entity mutations/lookups
 * and Multi for reactive streaming of ticket collections.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
public interface TicketPersistencePort {


    /**
     * Persists a new Ticket aggregate root into the database.
     *
     * @param ticket the fully initialized, valid domain entity to store. Must not be null.
     * @return a Uni emitting the persisted Ticket upon successful database insertion.
     * @throws NullPointerException if ticket is null.
     */
    Uni<Ticket> save(Ticket ticket);


    /**
     * Updates the state and audit history of an existing Ticket aggregate root.
     *
     * @param ticket the modified domain entity containing updated lifecycle state. Must not be null.
     * @return a Uni emitting the updated Ticket aggregate upon successful database update.
     * @throws NullPointerException if ticket is null.
     */
    Uni<Ticket> update(Ticket ticket);


    /**
     * Retrieves a single support ticket by its technical database persistence identifier (UUID/BSON ID).
     *
     * @param id the technical identifier. Must not be null or blank.
     * @return a Uni emitting the matching Ticket, or an empty Uni (emitting null) if not found.
     * @throws NullPointerException if id is null.
     * @throws IllegalArgumentException if id is blank.
     */
    Uni<Ticket> findById(String id);



    /**
     * Retrieves a single support ticket by its business-readable sequence identifier (e.g., "TICK-2026-0001").
     *
     * @param ticketNumber the unique business sequence string. Must not be null or blank.
     * @return a Uni emitting the matching Ticket, or an empty Uni (emitting null) if not found.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    Uni<Ticket> findByTicketNumber(String ticketNumber);


    /**
     * Streams all support tickets matching a specific lifecycle status.
     *
     * @param status the target lifecycle state to filter by. Must not be null.
     * @return a Multi stream emitting matching Ticket aggregates as they are read from the database cursor.
     * @throws NullPointerException if status is null.
     */
    Multi<Ticket> findByStatus(TicketStatus status);


    /**
     * Streams all support tickets created by a specific enterprise requester or corporate tenant.
     *
     * @param requesterId the enterprise requester identifier. Must not be null or blank.
     * @return a Multi stream emitting matching Ticket aggregates reactively.
     * @throws NullPointerException if requesterId is null.
     * @throws IllegalArgumentException if requesterId is blank.
     */
    Multi<Ticket> findByRequesterId(String requesterId);


    /**
     * Streams all active and closed support tickets stored in the persistence engine.
     *
     * @return a Multi stream emitting all Ticket aggregates with reactive backpressure.
     */
    Multi<Ticket> findAll();



    /**
     * Verifies whether a ticket with the given business sequence already exists.
     *
     * @param ticketNumber the business sequence to check. Must not be null or blank.
     * @return a Uni emitting true if a record exists; false otherwise.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    Uni<Boolean> existsByTicketNumber(String ticketNumber);
}
