package org.softtech.domain.port.out;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.Ticket;

import java.time.Duration;


/**
 * Outbound Port (Driven / Secondary Port) defining the reactive caching contract
 * for the Ticket aggregate root within the SoftTech Solutions ERP ecosystem.
 * 
 * Implements the Cache-Aside architectural pattern using non-blocking primitives
 * powered by SmallRye Mutiny Uni. This port decouples core application and domain
 * orchestration from low-level distributed cache implementations (such as Quarkus Redis Client),
 * enabling sub-millisecond retrieval latencies, TTL-based eviction, and proactive cache
 * invalidation to satisfy ISO/IEC 25010 Performance Efficiency and Time-Behavior requirements.
 * 
 *
 */
public interface TicketCachePort {

    /**
     * Stores an active Ticket aggregate root into the distributed cache
     * under both its technical ID and business sequence key, bounded by a Time-To-Live (TTL).
     *
     * @param ticket the valid Ticket aggregate root to store in cache. Must not be null.
     * @param ttl the expiration duration for the cached entries. Must not be null.
     * @return a Uni that emits null upon successful cache insertion.
     * @throws NullPointerException if ticket or ttl is null.
     */
    Uni<Void> put(Ticket ticket, Duration ttl);


    /**
     * Retrieves a cached Ticket aggregate by its technical persistence identifier (UUID/BSON ID).
     *
     * @param id the technical identifier. Must not be null or blank.
     * @return a Uni emitting the cached Ticket, or emitting null on a Cache Miss.
     * @throws NullPointerException if id is null.
     * @throws IllegalArgumentException if id is blank.
     */
    Uni<Ticket> getById(String id);


    /**
     * Retrieves a cached Ticket aggregate by its business tracking sequence (e.g., "TICK-2026-0001").
     *
     * @param ticketNumber the business tracking sequence. Must not be null or blank.
     * @return a Uni emitting the cached Ticket, or emitting null on a Cache Miss.
     * @throws NullPointerException if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    Uni<Ticket> getByTicketNumber(String ticketNumber);



    /**
     * Invalidates and evicts cached entries associated with a ticket across all lookup keys
     * (both technical ID and business sequence key) upon lifecycle state transitions.
     *
     * @param id the technical persistence identifier to evict. Must not be null or blank.
     * @param ticketNumber the business tracking sequence to evict. Must not be null or blank.
     * @return a Uni that emits null upon successful eviction.
     * @throws NullPointerException if id or ticketNumber is null.
     * @throws IllegalArgumentException if id or ticketNumber is blank.
     */
    Uni<Void> evict(String id, String ticketNumber);
}
