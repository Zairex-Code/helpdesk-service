package org.softtech.infrastructure.cache.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.infrastructure.persistence.document.TicketDocument;
import org.softtech.infrastructure.persistence.mapper.TicketPersistenceMapper;

import java.time.Duration;
import java.util.Objects;


/**
 * Secondary (Driven) Infrastructure Adapter implementing the TicketCachePort outbound contract.
 * <p>
 * Implements the Cache-Aside architectural pattern over distributed Redis infrastructure using
 * Quarkus ReactiveRedisDataSource. It maintains dual-index string key lookups (by technical UUID
 * and business tracking sequence) with configurable Time-To-Live (TTL) expiration policies.
 * In strict compliance with Hexagonal Architecture and Domain-Driven Design (DDD), serialization
 * leverages TicketDocument and TicketPersistenceMapper as intermediary data carriers
 * to prevent Jackson reflection annotations from leaking into the pure domain aggregate.
 * </p>
 * <p>
 * In accordance with ISO/IEC 25010 Performance Efficiency (Sub-millisecond retrieval latency) and CMMI Level 2/3
 * Service Operations, all cache operations are non-blocking, asynchronous, and executed on the Netty Event Loop.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
@ApplicationScoped
public class TicketRedisAdapter implements TicketCachePort {

    private static final String KEY_PREFIX_ID = "ticket:id:";
    private static final String KEY_PREFIX_NUMBER = "ticket:number:";

    private final ReactiveValueCommands<String, String> valueCommands;
    private final ReactiveKeyCommands<String> keyCommands;
    private final TicketPersistenceMapper persistenceMapper;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the reactive Redis cache adapter with managed Quarkus CDI dependencies.
     *
     * @param redisDataSource the reactive Redis data source. Must not be {@code null}.
     * @param persistenceMapper the domain-to-document translation mapper. Must not be {@code null}.
     * @param objectMapper the enterprise JSON object mapper. Must not be {@code null}.
     */
    public TicketRedisAdapter(
            ReactiveRedisDataSource redisDataSource,
            TicketPersistenceMapper persistenceMapper,
            ObjectMapper objectMapper) {
        Objects.requireNonNull(redisDataSource, "ReactiveRedisDataSource must not be null");
        this.persistenceMapper = Objects.requireNonNull(persistenceMapper, "TicketPersistenceMapper must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.valueCommands = redisDataSource.value(String.class, String.class);
        this.keyCommands = redisDataSource.key(String.class);
    }

    /**
     * Stores an active Ticket aggregate root into the distributed cache
     * under both its technical ID and business sequence key, bounded by a Time-To-Live (TTL).
     *
     * @param ticket the valid Ticket aggregate root to store in cache. Must not be null.
     * @param ttl    the expiration duration for the cached entries. Must not be null.
     * @return a Uni that emits null upon successful cache insertion.
     * @throws NullPointerException if ticket or ttl is null.
     */
    @Override
    public Uni<Void> put(Ticket ticket, Duration ttl) {
        Objects.requireNonNull(ticket, "Ticket aggregate must not be null for cache put");
        Objects.requireNonNull(ttl, "Cache TTL duration must not be null");

        String idKey = buildIdKey(ticket.getId());
        String numberKey = buildNumberKey(ticket.getTicketNumber());

        return Uni.createFrom().item(() -> serializeTicket(ticket)).flatMap(jsonPayload -> {
            SetArgs setArgs = new SetArgs().ex(ttl);
            Uni<Void> setIdKey = valueCommands.set(idKey, jsonPayload, setArgs);
            Uni<Void> setNumberKey = valueCommands.set(numberKey, jsonPayload, setArgs);

            return Uni.combine().all().unis(setIdKey, setNumberKey).asTuple().replaceWithVoid();
        }).invoke(() -> log.debug(
                "Cache ticket {} under keys {} and {} with TTL {}s",
                ticket.getTicketNumber(), idKey, numberKey, ttl.toSeconds()
        ));
    }

    /**
     * Retrieves a cached Ticket aggregate by its technical persistence identifier (UUID/BSON ID).
     *
     * @param id the technical identifier. Must not be null or blank.
     * @return a Uni emitting the cached Ticket, or emitting null on a Cache Miss.
     * @throws NullPointerException     if id is null.
     * @throws IllegalArgumentException if id is blank.
     */
    @Override
    public Uni<Ticket> getById(String id) {
        Objects.requireNonNull(id, "Ticket ID must not be null for cache lookup");
        String sanitizedId = id.trim();
        if (sanitizedId.isBlank()){
            throw new IllegalArgumentException("Ticket ID cannot be blank");
        }
        String key = buildIdKey(sanitizedId);
        return valueCommands.get(key).onItem().ifNotNull().transform(this::deserializeTicket)
                .invoke(cachedTicket -> {
                    if (cachedTicket != null){
                        log.debug("Cache HIT for ticket ID {}", sanitizedId);
                    }else {
                        log.debug("Cache MISS for ticket ID {}", sanitizedId);
                    }
                });
    }

    /**
     * Retrieves a cached Ticket aggregate by its business tracking sequence (e.g., "TICK-2026-0001").
     *
     * @param ticketNumber the business tracking sequence. Must not be null or blank.
     * @return a Uni emitting the cached Ticket, or emitting null on a Cache Miss.
     * @throws NullPointerException     if ticketNumber is null.
     * @throws IllegalArgumentException if ticketNumber is blank.
     */
    @Override
    public Uni<Ticket> getByTicketNumber(String ticketNumber) {
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null for cache lookup");
        String sanitizedTicketNumber = ticketNumber.trim();
        if (sanitizedTicketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number cannot be null");
        }

        String key = buildNumberKey(sanitizedTicketNumber);
        return valueCommands.get(key)
                .onItem().ifNotNull().transform(this::deserializeTicket)
                .invoke(cachedTicket -> {
                    if (cachedTicket != null){
                        log.debug("Cache HIT for ticket {}", sanitizedTicketNumber);
                    }else {
                        log.debug("Cache MISS for ticketNumber {}", sanitizedTicketNumber);
                    }
                });
    }

    /**
     * Invalidates and evicts cached entries associated with a ticket across all lookup keys
     * (both technical ID and business sequence key) upon lifecycle state transitions.
     *
     * @param id           the technical persistence identifier to evict. Must not be null or blank.
     * @param ticketNumber the business tracking sequence to evict. Must not be null or blank.
     * @return a Uni that emits null upon successful eviction.
     * @throws NullPointerException     if id or ticketNumber is null.
     * @throws IllegalArgumentException if id or ticketNumber is blank.
     */
    @Override
    public Uni<Void> evict(String id, String ticketNumber) {
        Objects.requireNonNull(id, "Ticket ID must not be null for cache eviction");
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null for cache eviction");


        String idKey = buildIdKey(id.trim());
        String numberKey = buildNumberKey(ticketNumber.trim());

        return keyCommands.del(idKey, numberKey).replaceWithVoid().invoke(() -> log.debug("Evicted Redis keys {} and {}", idKey, numberKey));
    }

    private String serializeTicket(Ticket ticket){
        try {
            TicketDocument document = persistenceMapper.toDocument(ticket);
            return objectMapper.writeValueAsString(document);
        }catch (JsonProcessingException e){
            log.error("Failed to serialize ticket {} to JSON: {}", ticket.getTicketNumber(), e.getMessage(), e);
            throw new IllegalStateException("Redis serialization error", e);
        }
    }

    private Ticket deserializeTicket(String jsonPayload){
        try {
            TicketDocument document = objectMapper.readValue(jsonPayload, TicketDocument.class);
            return persistenceMapper.toDomain(document);
        }catch (JsonProcessingException e){
            log.error("Failed to deserialize JSON payload from Redis: {}", e.getMessage());
            throw new IllegalStateException("Redis deserialization error", e);
        }
    }

    private String buildIdKey(String id){
        return KEY_PREFIX_ID + id;
    }

    private String buildNumberKey(String ticketNumber){
        return KEY_PREFIX_NUMBER + ticketNumber;
    }
}
