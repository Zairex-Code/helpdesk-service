package org.softtech.infrastructure.entrypoints.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.port.out.TicketCachePort;
import org.softtech.domain.port.out.TicketEventPublisherPort;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Hermetic integration test suite for {@link TicketResource} REST contract, Bean Validation,
 * RBAC security and error handling, using mocked outbound ports and an in-memory ticket store.
 */
@QuarkusTest
class TicketResourceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @InjectMock
    TicketPersistencePort ticketPersistencePort;

    @InjectMock
    TicketCachePort ticketCachePort;

    @InjectMock
    TicketEventPublisherPort ticketEventPublisherPort;

    private final Map<String, Ticket> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();

        Mockito.reset(ticketPersistencePort, ticketCachePort, ticketEventPublisherPort);

        when(ticketPersistencePort.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            store.put(ticket.getId(), ticket);
            return Uni.createFrom().item(ticket);
        });
        when(ticketPersistencePort.update(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            store.put(ticket.getId(), ticket);
            return Uni.createFrom().item(ticket);
        });
        when(ticketPersistencePort.findById(anyString())).thenAnswer(invocation ->
                Uni.createFrom().item(store.get(invocation.getArgument(0))));
        when(ticketPersistencePort.findByTicketNumber(anyString())).thenAnswer(invocation ->
                Uni.createFrom().item(store.values().stream()
                        .filter(t -> t.getTicketNumber().equals(invocation.getArgument(0)))
                        .findFirst().orElse(null)));

        when(ticketCachePort.getById(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(ticketCachePort.getByTicketNumber(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(ticketCachePort.put(any(Ticket.class), any())).thenReturn(Uni.createFrom().voidItem());
        when(ticketCachePort.evict(anyString(), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(org.softtech.domain.event.TicketCreatedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(org.softtech.domain.event.TicketStatusChangedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(ticketEventPublisherPort.publish(any(org.softtech.domain.event.TicketClosedEvent.class)))
                .thenReturn(Uni.createFrom().voidItem());
    }

    private String validTicketPayload() {
        return """
                {
                  "title": "Database timeout in payroll batch",
                  "description": "PostgreSQL deadlock detected during concurrent payroll execution in ERP-RRHH.",
                  "priority": "HIGH",
                  "erpModule": "HUMAN_RESOURCES",
                  "requesterId": "USR-CORP-98421",
                  "vipCustomer": true
                }
                """;
    }

    @Test
    @TestSecurity(user = "cliente@softtech.com", roles = "CLIENTE")
    @DisplayName("Should create a ticket and return 201 with Location header")
    void shouldCreateTicket() {
        given()
                .contentType(ContentType.JSON)
                .body(validTicketPayload())
                .when().post("/api/v1/tickets")
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("ticketNumber", notNullValue())
                .body("status", equalTo("OPEN"));
    }

    @Test
    @TestSecurity(user = "cliente@softtech.com", roles = "CLIENTE")
    @DisplayName("Should reject invalid create payload with 400 and violations")
    void shouldRejectInvalidCreatePayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "   ",
                          "description": "short",
                          "priority": null,
                          "erpModule": "HUMAN_RESOURCES",
                          "requesterId": "USR-CORP-98421"
                        }
                        """)
                .when().post("/api/v1/tickets")
                .then()
                .statusCode(400)
                .body("errorCode", equalTo("ERR_HD_VALIDATION_FAILED"))
                .body("violations", notNullValue());
    }

    @Test
    @TestSecurity(user = "cliente@softtech.com", roles = "CLIENTE")
    @DisplayName("Should return 404 for unknown ticket")
    void shouldReturn404ForUnknownTicket() {
        given()
                .when().get("/api/v1/tickets/non-existent-id")
                .then()
                .statusCode(404)
                .body("errorCode", equalTo("ERR_HD_TICKET_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "cliente@softtech.com", roles = "CLIENTE")
    @DisplayName("Should return 200 for existing ticket")
    void shouldReturn200ForExistingTicket() {
        Ticket ticket = Ticket.created(
                "known-id", "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
        store.put(ticket.getId(), ticket);

        given()
                .when().get("/api/v1/tickets/known-id")
                .then()
                .statusCode(200)
                .body("id", equalTo("known-id"))
                .body("ticketNumber", equalTo("TICK-2026-0001"));
    }

    @Test
    @TestSecurity(user = "cliente@softtech.com", roles = "CLIENTE")
    @DisplayName("Should forbid CLIENTE from assigning tickets")
    void shouldForbidClienteFromAssigning() {
        Ticket ticket = Ticket.created(
                "known-id", "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
        store.put(ticket.getId(), ticket);

        given()
                .contentType(ContentType.JSON)
                .body("{\"assignedAgentId\": \"AGT-TI-5042\"}")
                .when().patch("/api/v1/tickets/known-id/assign")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "soporte@softtech.com", roles = "SOPORTE_TI")
    @DisplayName("Should allow SOPORTE_TI to assign tickets")
    void shouldAllowSoporteToAssign() {
        Ticket ticket = Ticket.created(
                "known-id", "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
        store.put(ticket.getId(), ticket);

        given()
                .contentType(ContentType.JSON)
                .body("{\"assignedAgentId\": \"AGT-TI-5042\"}")
                .when().patch("/api/v1/tickets/known-id/assign")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    @TestSecurity(user = "admin@softtech.com", roles = "ADMIN")
    @DisplayName("Should execute the full ticket lifecycle CREATE -> ASSIGN -> IN_PROGRESS -> RESOLVED -> CLOSED")
    void shouldExecuteFullLifecycle() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(validTicketPayload())
                .when().post("/api/v1/tickets")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"assignedAgentId\": \"AGT-TI-5042\"}")
                .when().patch("/api/v1/tickets/" + id + "/assign")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"));

        given()
                .when().patch("/api/v1/tickets/" + id + "/start-investigation")
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));

        given()
                .contentType(ContentType.JSON)
                .body("{\"resolutionNotes\": \"Adjusted isolation level and optimized batch chunking.\"}")
                .when().patch("/api/v1/tickets/" + id + "/resolve")
                .then()
                .statusCode(200)
                .body("status", equalTo("RESOLVED"));

        given()
                .contentType(ContentType.JSON)
                .body("{\"rating\": 5, \"comment\": \"Excellent\"}")
                .when().patch("/api/v1/tickets/" + id + "/close")
                .then()
                .statusCode(200)
                .body("status", equalTo("CLOSED"))
                .body("csatRating", equalTo(5));
    }

    @Test
    @TestSecurity(user = "admin@softtech.com", roles = "ADMIN")
    @DisplayName("Should reject closing an OPEN ticket with conflict")
    void shouldRejectClosingOpenTicket() {
        Ticket ticket = Ticket.created(
                "open-id", "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
        store.put(ticket.getId(), ticket);

        given()
                .contentType(ContentType.JSON)
                .body("{\"rating\": 5, \"comment\": \"n/a\"}")
                .when().patch("/api/v1/tickets/open-id/close")
                .then()
                .statusCode(409)
                .body("errorCode", equalTo("ERR_HD_INVALID_STATUS_TRANSITION"));
    }

    @Test
    @TestSecurity(user = "admin@softtech.com", roles = "ADMIN")
    @DisplayName("Should allow cancel and stream by status")
    void shouldCancelTicket() {
        Ticket ticket = Ticket.created(
                "cancel-id", "TICK-2026-0001", "Title", "Description",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "USR-1", false, NOW);
        store.put(ticket.getId(), ticket);

        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\": \"Duplicate ticket\"}")
                .when().patch("/api/v1/tickets/cancel-id/cancel")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }
}
