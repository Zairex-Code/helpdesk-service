package org.softtech.infrastructure.persistence.repository;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.softtech.infrastructure.persistence.document.TicketDocument;

import java.util.Objects;

/**
 * Reactive Panache Data Access Repository for the TicketDocument MongoDB collection.
 * <p>
 * Implements the Repository Pattern via Quarkus Reactive MongoDB Panache (ReactivePanacheMongoRepository),
 * executing non-blocking database queries over the Netty Event Loop. It encapsulates low-level MongoDB filter
 * expressions, backpressure-aware cursor streaming, and count aggregations, keeping persistence mechanics
 * completely decoupled from the Hexagonal domain boundary.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@ApplicationScoped
public class ReactiveTicketPanacheRepository implements ReactivePanacheMongoRepository<TicketDocument> {


    private static final String FIELD_TICKET_NUMBER = "ticket_number";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_REQUESTER_ID = "requester_id";


    /**
     * Finds a single ticket document by its unique business tracking sequence.
     *
     * @param ticketNumber the business sequence identifier. Must not be null or blank.
     * @return a Uni emitting the matching TicketDocument, or emitting null on miss.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    public Uni<TicketDocument> findByTicketNumber(String ticketNumber){
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null");
        if (ticketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number cannot be blank");
        }

        return find(FIELD_TICKET_NUMBER, ticketNumber.trim()).firstResult();

    }


    /**
     * Streams ticket documents matching a specific lifecycle status directly from the MongoDB cursor.
     *
     * @param status the string representation of the target status. Must not be null or blank.
     * @return a Multi stream emitting matching TicketDocument instances with reactive backpressure.
     * @throws NullPointerException if status is null.
     * @throws IllegalArgumentException if status is blank.
     */
    public Multi<TicketDocument> streamByStatus(String status){
        Objects.requireNonNull(status, "status must not be null");
        if (status.isBlank()){
            throw new IllegalArgumentException("Status cannot be blank");
        }

        return find(FIELD_STATUS, status.trim()).stream();
    }


    /**
     * Streams ticket documents registered by a specific enterprise requester or corporate tenant.
     *
     * @param requesterId the enterprise requester identifier. Must not be null or blank.
     * @return a Multi stream emitting matching TicketDocument records reactively.
     * @throws NullPointerException if requesterId is null.
     * @throws IllegalArgumentException if requesterId is blank.
     */
    public Multi<TicketDocument> streamByRequesterId(String  requesterId){
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        if (requesterId.isBlank()){
            throw new IllegalArgumentException("Requester ID cannot be blank");
        }

        return find(FIELD_REQUESTER_ID, requesterId.trim()).stream();
    }



    /**
     * Streams all support ticket documents stored within the MongoDB collection.
     *
     * @return a Multi stream emitting all persisted TicketDocument instances.
     */
    public Multi<TicketDocument> streamAllTicket(){
        return findAll().stream();
    }


    /**
     * Verifies whether a ticket with the specified business tracking number already exists.
     *
     * @param ticketNumber the business tracking number to check. Must not be null or blank.
     * @return a Uni emitting true if a record exists; false otherwise.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    public Uni<Boolean> existsByTicketNumber(String ticketNumber){
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null");

        if (ticketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number cannot be blank");
        }

        return count(FIELD_TICKET_NUMBER, ticketNumber.trim())
                .map(totalCount -> totalCount > 0L);
    }

}
