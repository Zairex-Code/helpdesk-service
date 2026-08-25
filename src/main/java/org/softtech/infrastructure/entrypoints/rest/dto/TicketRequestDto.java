package org.softtech.infrastructure.entrypoints.rest.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;


/**
 * Immutable Data Transfer Object (DTO) capturing HTTP request payloads for support ticket creation.
 *
 * Enforces declarative input validation constraints at the REST boundary via Jakarta Validation (Bean Validation 3.0),
 * preventing malformed or incomplete client requests from entering the application use cases.
 * Fully documented using modern MicroProfile OpenAPI 3.1 / 4.0+ annotations with array-based examples
 * to eliminate deprecation warnings and ensure long-term framework compatibility.
 *
 *
 * In strict compliance with ISO/IEC 25010 Reliability (Data Integrity and Fault Tolerance) and
 * CMMI Level 2/3 Verification standards, this record provides shallow immutability and utilizes
 * Jacksonized builder instantiation to eliminate mutable state leaks.
 *
 *
 * @param title the concise summary of the reported technical incident. Must not be blank (5-150 chars).
 * @param description the full diagnostic context and reproduction steps. Must not be blank (10-2000 chars).
 * @param priority the operational urgency and severity level. Must not be null.
 * @param erpModule the impacted ERP functional module. Must not be null.
 * @param requesterId the unique corporate user or tenant identifier. Must not be blank.
 * @param vipCustomer indicates whether the requester is entitled to premium SLA coverage.
 *
 */
@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "TicketRequestDto",
        description = "Input payload for creating a new enterprise support ticket in the HelpDesk system."
)
public record TicketRequestDto(

        @Schema(
                description = "Concise summary of the reported incident.",
                examples = {"Database timeout error during batch payroll execution"},
                required = true,
                minLength = 5,
                maxLength = 150
        )
        @NotBlank(message = "Ticket little must not be blank")
        @Size(min = 5, max = 150, message = "Ticket little must contain between 5 and 150 characters")
        String title,



        @Schema(
                description = "Full diagnostic description, error logs, and reproduction steps.",
                examples = {"PostgreSQL deadlock detected when procesing over 5000 employee payroll records concurrently in ERP-RRHH"},
                required = true,
                minLength = 10,
                maxLength = 2000
        )
        @NotBlank(message = "Ticket description must not be blank")
        @Size(min = 10, max = 2000, message = "Ticket description must contain between 2000 characters")
        String description,



        @Schema(
                description = "Operational urgency and severity level.",
                examples = {"CRITICAL"},
                required = true,
                enumeration = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
        )
        @NotNull(message = "Ticket priority must not be null")
        Priority priority,



        @Schema(
                description = "Impacted ERP function module.",
                examples = {"HUMAN_RESOURCES"},
                required = true,
                enumeration = {"FINANCE", "HUMAN_RESOURCES", "INVENTORY", "SALES","CRM", "SUPPLY_CHAIN" }
        )
        @NotNull(message = "ERP module must not be null")
        ErpModule erpModule,



        @Schema(
                description = "Unique corporate user or tenant identifier reporting the incident.",
                examples = {"USR-CORP-98421"},
                required = true
        )
        @NotBlank(message = "Requester ID must not be blank")
        String requesterId,




        @Schema(
                description = "Flag indicating premium enterprise SLA coverage",
                examples = {"true"},
                defaultValue = "false"
        )
        boolean vipCustomer



) {
}
