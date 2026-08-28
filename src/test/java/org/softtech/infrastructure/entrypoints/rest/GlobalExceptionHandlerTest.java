package org.softtech.infrastructure.entrypoints.rest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.softtech.domain.exception.DomainException;
import org.softtech.domain.exception.InvalidStatusTransitionException;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.domain.model.TicketStatus;
import org.softtech.infrastructure.entrypoints.rest.dto.ErrorResponseDto;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test suite for {@link GlobalExceptionHandler} RFC 7807 error mapping.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should map TicketNotFoundException to 404")
    void shouldMapTicketNotFoundTo404() {
        RestResponse<ErrorResponseDto> response = handler.handleTicketNotFoundException(
                new TicketNotFoundException("TICK-2026-0001"), null);

        assertEquals(404, response.getStatus());
        assertEquals("ERR_HD_TICKET_NOT_FOUND", response.getEntity().errorCode());
        assertNotNull(response.getEntity().correlationId());
    }

    @Test
    @DisplayName("Should map InvalidStatusTransitionException to 409")
    void shouldMapInvalidTransitionTo409() {
        RestResponse<ErrorResponseDto> response = handler.handleInvalidStatusTransitionException(
                new InvalidStatusTransitionException("TICK-2026-0001", TicketStatus.OPEN, TicketStatus.CLOSED), null);

        assertEquals(409, response.getStatus());
        assertEquals("ERR_HD_INVALID_STATUS_TRANSITION", response.getEntity().errorCode());
    }

    @Test
    @DisplayName("Should map ConstraintViolationException to 400 with violations")
    void shouldMapConstraintViolationTo400() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        doReturn("create.title").when(path).toString();
        when(violation.getMessage()).thenReturn("must not be blank");
        when(violation.getPropertyPath()).thenReturn(path);

        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        RestResponse<ErrorResponseDto> response = handler.handleConstraintViolationException(exception, null);

        assertEquals(400, response.getStatus());
        assertEquals("ERR_HD_VALIDATION_FAILED", response.getEntity().errorCode());
        assertEquals("must not be blank", response.getEntity().violations().get("title"));
    }

    @Test
    @DisplayName("Should map generic DomainException to 422")
    void shouldMapDomainExceptionTo422() {
        DomainException exception = new DomainException("HD-DOM-9000", "Business rule violated") {
        };

        RestResponse<ErrorResponseDto> response = handler.handleDomainException(exception, null);

        assertEquals(422, response.getStatus());
        assertEquals("ERR_HD_DOMAIN_RULE_VIOLATION", response.getEntity().errorCode());
    }

    @Test
    @DisplayName("Should map IllegalArgumentException to 400")
    void shouldMapIllegalArgumentTo400() {
        RestResponse<ErrorResponseDto> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Invalid argument"), null);

        assertEquals(400, response.getStatus());
        assertEquals("ERR_HD_ILLEGAL_ARGUMENT", response.getEntity().errorCode());
    }

    @Test
    @DisplayName("Should map generic Throwable to 500")
    void shouldMapGenericThrowableTo500() {
        RestResponse<ErrorResponseDto> response = handler.handleGenericThrowable(
                new RuntimeException("boom"), null);

        assertEquals(500, response.getStatus());
        assertEquals("ERR_HD_INTERNAL_SERVER_ERROR", response.getEntity().errorCode());
        assertNotNull(response.getEntity().correlationId());
    }
}
