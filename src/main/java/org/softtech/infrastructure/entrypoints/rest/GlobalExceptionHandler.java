package org.softtech.infrastructure.entrypoints.rest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.softtech.domain.exception.DomainException;
import org.softtech.domain.exception.InvalidStatusTransitionException;
import org.softtech.domain.exception.TicketNotFoundException;
import org.softtech.infrastructure.entrypoints.rest.dto.ErrorResponseDto;

/**
 * Centralized Reactive Exception Interceptor and REST Error Boundary Handler.
 * <p>
 * Intercepts uncaught domain, validation, and infrastructure exceptions thrown across the Netty Event Loop,
 * translating them into uniform, machine-readable HTTP responses compliant with the IETF RFC 7807 / RFC 9457
 * (Problem Details for HTTP APIs) specification.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Security (Confidentiality & Non-leakage of internal system mechanics)
 * and CMMI Level 2/3 Defect Management standards, this interceptor sanitizes raw stack traces, isolates
 * internal infrastructure details, and injects distributed correlation IDs to ensure complete end-to-end traceability.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Slf4j
public class GlobalExceptionHandler {

    private static final String ERROR_BASE_URI = "https://helpdesk.softtech.com/errors/";
    private static final String TYPE_NOT_FOUND = ERROR_BASE_URI + "ticket-not-found";
    private static final String TYPE_INVALID_TRANSITION = ERROR_BASE_URI + "invalid-status-transition";
    private static final String TYPE_VALIDATION = ERROR_BASE_URI + "validation-violation";
    private static final String TYPE_DOMAIN_RULE = ERROR_BASE_URI + "domain-rule-violation";
    private static final String TYPE_ILLEGAL_ARGUMENT = ERROR_BASE_URI + "illegal-argument";
    private static final String TYPE_INTERNAL_SERVER_ERROR = ERROR_BASE_URI + "internal-server-error";

    private static final String CODE_TICKET_NOT_FOUND = "ERR_HD_TICKET_NOT_FOUND";
    private static final String CODE_INVALID_STATUS_TRANSITION = "ERR_HD_INVALID_STATUS_TRANSITION";
    private static final String CODE_VALIDATION_FAILED = "ERR_HD_VALIDATION_FAILED";
    private static final String CODE_DOMAIN_RULE_VIOLATION = "ERR_HD_DOMAIN_RULE_VIOLATION";
    private static final String CODE_ILLEGAL_ARGUMENT = "ERR_HD_ILLEGAL_ARGUMENT";
    private static final String CODE_INTERNAL_ERROR = "ERR_HD_INTERNAL_SERVER_ERROR";

    /**
     * Intercepts {@link TicketNotFoundException} when a query by ID or sequence number returns empty.
     *
     * @param exception the domain lookup failure exception.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link RestResponse} containing HTTP 404 Not Found and RFC 7807 Problem Details payload.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> handleTicketNotFoundException(TicketNotFoundException exception, UriInfo uriInfo) {
        log.warn("Resource lookup failed: {}", exception.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .type(TYPE_NOT_FOUND)
                .title("Ticket Not Found")
                .status(Status.NOT_FOUND.getStatusCode())
                .detail(exception.getMessage())
                .instance(getPath(uriInfo))
                .errorCode(CODE_TICKET_NOT_FOUND)
                .correlationId(generateCorrelationId())
                .timestamp(Instant.now())
                .build();

        return RestResponse.status(Status.NOT_FOUND, errorDto);
    }

    /**
     * Intercepts {@link InvalidStatusTransitionException} when an illegal state machine transition is attempted.
     *
     * @param exception the domain state transition conflict exception.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link RestResponse} containing HTTP 409 Conflict and RFC 7807 Problem Details payload.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> handleInvalidStatusTransitionException(
            InvalidStatusTransitionException exception, UriInfo uriInfo) {
        log.warn("Illegal status transition rejected: {}", exception.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .type(TYPE_INVALID_TRANSITION)
                .title("Invalid Status Transition")
                .status(Response.Status.CONFLICT.getStatusCode())
                .detail(exception.getMessage())
                .instance(getPath(uriInfo))
                .errorCode(CODE_INVALID_STATUS_TRANSITION)
                .correlationId(generateCorrelationId())
                .timestamp(Instant.now())
                .build();

        return RestResponse.status(Status.CONFLICT, errorDto);
    }

    /**
     * Intercepts Jakarta Bean Validation {@link ConstraintViolationException} triggered on invalid DTO fields.
     *
     * @param exception the bean validation constraint violations container.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link RestResponse} containing HTTP 400 Bad Request and detailed field violation mappings.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> handleConstraintViolationException(
            ConstraintViolationException exception, UriInfo uriInfo) {
        Map<String, String> fieldViolations = exception.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> extractPropertyPath(violation),
                        ConstraintViolation::getMessage,
                        (existingMessage, newMessage) -> existingMessage + "; " + newMessage
                ));

        log.warn("Input validation failed on endpoint [{}] with [{}] field violations",
                getPath(uriInfo), fieldViolations.size());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .type(TYPE_VALIDATION)
                .title("Validation Constraint Violation")
                .status(Status.BAD_REQUEST.getStatusCode())
                .detail("One or more payload attributes failed declarative validation constraints.")
                .instance(getPath(uriInfo))
                .errorCode(CODE_VALIDATION_FAILED)
                .violations(fieldViolations)
                .correlationId(generateCorrelationId())
                .timestamp(Instant.now())
                .build();

        return RestResponse.status(Status.BAD_REQUEST, errorDto);
    }

    /**
     * Intercepts generic {@link DomainException} domain invariant rule breaches.
     *
     * @param exception the domain invariant exception.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link RestResponse} containing HTTP 422 Unprocessable Entity and problem details.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> handleDomainException(DomainException exception, UriInfo uriInfo) {
        log.warn("Domain rule invariant violation: {}", exception.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .type(TYPE_DOMAIN_RULE)
                .title("Unprocessable Domain Entity")
                .status(422)
                .detail(exception.getMessage())
                .instance(getPath(uriInfo))
                .errorCode(CODE_DOMAIN_RULE_VIOLATION)
                .correlationId(generateCorrelationId())
                .timestamp(Instant.now())
                .build();

        return RestResponse.status(RestResponse.Status.fromStatusCode(422), errorDto);
    }

    /**
     * Intercepts {@link IllegalArgumentException} and {@link IllegalStateException} runtime preconditions.
     *
     * @param exception the illegal parameter exception.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link RestResponse} containing HTTP 400 Bad Request and problem details.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> handleIllegalArgumentException(
            IllegalArgumentException exception, UriInfo uriInfo) {
        log.warn("Illegal argument detected on endpoint [{}]: {}", getPath(uriInfo), exception.getMessage());

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .type(TYPE_ILLEGAL_ARGUMENT)
                .title("Illegal Request Argument")
                .status(Status.BAD_REQUEST.getStatusCode())
                .detail(exception.getMessage())
                .instance(getPath(uriInfo))
                .errorCode(CODE_ILLEGAL_ARGUMENT)
                .correlationId(generateCorrelationId())
                .timestamp(Instant.now())
                .build();

        return RestResponse.status(Status.BAD_REQUEST, errorDto);
    }

    /**
     * Catches all unhandled root exceptions and fatal system failures.
     * Sanitizes response payloads to prevent leakage of internal database or infrastructure traces.
     *
     * @param throwable the unexpected root exception.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link RestResponse} containing HTTP 500 Internal Server Error with correlation tracking.
     */
    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> handleGenericThrowable(Throwable throwable, UriInfo uriInfo) {
        String correlationId = generateCorrelationId();
        log.error("Unhandled critical system failure [Trace ID: {}] on endpoint [{}]: {}",
                correlationId, getPath(uriInfo), throwable.getMessage(), throwable);

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .type(TYPE_INTERNAL_SERVER_ERROR)
                .title("Internal Server Error")
                .status(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .detail("An unexpected internal failure occurred while processing your request. Please reference the correlation ID with IT support.")
                .instance(getPath(uriInfo))
                .errorCode(CODE_INTERNAL_ERROR)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

        return RestResponse.status(Status.INTERNAL_SERVER_ERROR, errorDto);
    }

    private String getPath(UriInfo uriInfo) {
        return uriInfo != null && uriInfo.getPath() != null ? "/" + uriInfo.getPath() : "/api/v1/tickets";
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private String extractPropertyPath(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int lastDotIndex = propertyPath.lastIndexOf('.');
        return lastDotIndex >= 0 ? propertyPath.substring(lastDotIndex + 1) : propertyPath;
    }
}
