package org.softtech.domain.port.in;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.model.TicketStatus;
/**
 * Inbound Port (Query Use Case interface) defining the reactive read contracts
 * for retrieving HelpDesk Support Tickets within the SoftTech Solutions ERP ecosystem.
 *
 * In strict compliance with CQRS (Command Query Responsibility Segregation) and Hexagonal
 * Architecture, this interface isolates read operations from state-mutating commands.
 * It provides non-blocking data access using SmallRye Mutiny Uni for single entity
 * retrieval and Multi for backpressure-aware, continuous reactive streaming.
 *
 */
public interface GetTicketUseCase {

    /**
     * Retrieves a single support ticket by its technical persistence identifier (UUID/BSON String).
     *
     * @param id the unique technical persistence identifier. Must not be null or blank.
     * @return a Uni emitting the matching Ticket aggregate root, or failing with
     * @throws org.softtech.domain.exception.TicketNotFoundException if absent.
     * @throws NullPointerException if id is null.
     * @throws IllegalArgumentException if id is blank.
     */
    Uni<Ticket> getById(String id);

    /**
     * Retrieves a single support ticket by its business-readable sequence identifier (e.g., "TICK-2026-0001").
     *
     * @param ticketNumber the business tracking sequence. Must not be null or blank.
     * @return a Uni emitting the matching Ticket aggregate root, or failing with
     *         TicketNotFoundException if absent.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    Uni<Ticket> getByTicketNumber(String ticketNumber);

    /**
     * Streams all tickets filtered by their current lifecycle state.
     *
     * @param status the target lifecycle status to filter by. Must not be null.
     * @return a Multi stream emitting matching Ticket instances reactively.
     * @throws NullPointerException if {@code status} is null.
     */
    Multi<Ticket> listByStatus(TicketStatus status);

    /**
     * Streams all tickets registered by a specific enterprise user or corporate tenant.
     *
     * @param requesterId the enterprise requester identifier. Must not be null or blank.
     * @return a Multi stream emitting matching Ticket instances reactively.
     * @throws NullPointerException if requesterId is null.
     * @throws IllegalArgumentException if requesterId is blank.
     */
    Multi<Ticket> listByRequesterId(String requesterId);

    /**
     * Streams all support tickets across the entire HelpDesk domain.
     *
     * @return a Multi stream emitting all active and historical Ticket instances.
     */
    Multi<Ticket> listAll();
}
