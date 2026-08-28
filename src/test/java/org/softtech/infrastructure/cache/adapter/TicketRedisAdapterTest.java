package org.softtech.infrastructure.cache.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.infrastructure.persistence.mapper.TicketPersistenceMapper;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for {@link TicketRedisAdapter} Cache-Aside serialization and key management.
 */
@ExtendWith(MockitoExtension.class)
class TicketRedisAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TICKET_ID = "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f";
    private static final String TICKET_NUMBER = "TICK-2026-0001";

    @Mock
    private ReactiveRedisDataSource redisDataSource;

    @Mock
    private ReactiveValueCommands<String, String> valueCommands;

    @Mock
    private ReactiveKeyCommands<String> keyCommands;

    private final TicketPersistenceMapper persistenceMapper = new TicketPersistenceMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private TicketRedisAdapter adapter;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        when(redisDataSource.value(String.class, String.class)).thenReturn(valueCommands);
        when(redisDataSource.key(String.class)).thenReturn(keyCommands);

        adapter = new TicketRedisAdapter(redisDataSource, persistenceMapper, objectMapper);
        ticket = Ticket.created(
                TICKET_ID, TICKET_NUMBER, "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
    }

    @Test
    @DisplayName("Should store ticket under id and number keys")
    void shouldPutTicket() {
        when(valueCommands.set(any(), any(), any())).thenReturn(Uni.createFrom().voidItem());

        adapter.put(ticket, Duration.ofMinutes(30))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        verify(valueCommands, times(2)).set(any(), any(), any());
    }

    @Test
    @DisplayName("Should retrieve and deserialize a cached ticket by id")
    void shouldGetById() throws Exception {
        String json = objectMapper.writeValueAsString(persistenceMapper.toDocument(ticket));
        when(valueCommands.get("ticket:id:" + TICKET_ID)).thenReturn(Uni.createFrom().item(json));

        Ticket result = adapter.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TICKET_ID, result.getId());
        assertEquals(TICKET_NUMBER, result.getTicketNumber());
    }

    @Test
    @DisplayName("Should return null on cache miss by id")
    void shouldReturnNullOnCacheMissById() {
        when(valueCommands.get("ticket:id:" + TICKET_ID)).thenReturn(Uni.createFrom().nullItem());

        Ticket result = adapter.getById(TICKET_ID)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertNull(result);
    }

    @Test
    @DisplayName("Should retrieve a cached ticket by number")
    void shouldGetByTicketNumber() throws Exception {
        String json = objectMapper.writeValueAsString(persistenceMapper.toDocument(ticket));
        when(valueCommands.get("ticket:number:" + TICKET_NUMBER)).thenReturn(Uni.createFrom().item(json));

        Ticket result = adapter.getByTicketNumber(TICKET_NUMBER)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).getItem();

        assertEquals(TICKET_ID, result.getId());
    }

    @Test
    @DisplayName("Should evict both keys")
    void shouldEvict() {
        when(keyCommands.del("ticket:id:" + TICKET_ID, "ticket:number:" + TICKET_NUMBER))
                .thenReturn(Uni.createFrom().item(2));

        adapter.evict(TICKET_ID, TICKET_NUMBER)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        verify(keyCommands, times(1)).del(eq("ticket:id:" + TICKET_ID), eq("ticket:number:" + TICKET_NUMBER));
    }

    @Test
    @DisplayName("Should reject null and blank inputs")
    void shouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> adapter.put(null, Duration.ofMinutes(1)));
        assertThrows(NullPointerException.class, () -> adapter.put(ticket, null));
        assertThrows(NullPointerException.class, () -> adapter.getById(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.getById(" "));
        assertThrows(NullPointerException.class, () -> adapter.getByTicketNumber(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.getByTicketNumber(" "));
        assertThrows(NullPointerException.class, () -> adapter.evict(null, TICKET_NUMBER));
        assertThrows(NullPointerException.class, () -> adapter.evict(TICKET_ID, null));
    }
}
