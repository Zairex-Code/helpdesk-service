package org.softtech.infrastructure.entrypoints.rest.dto;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.TicketStatus;

import java.time.Instant;


@Builder(toBuilder = true)
@Jacksonized
@Schema(
        name = "TicketResponseDto",
        description = "Consolidated read projection of support ticker aggregate, including SLA metrics and customer feedback."
)
public record TicketResponseDto(

        @Schema(
                description = "Technical database persistence identifier (UUID v4).",
                examples = {"c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f"}
        )
        String id,

        @Schema(
                description = "Business-readable tracking sequence identifier.",
                examples = {"TICK-2026-0001"}
        )
        String ticketNumber,

        @Schema(
                description = "Concise summary of the reported incident.",
                examples = {"Database timeout error during batch payroll execution"}
        )
        String title,

        @Schema(
                description = "Full diagnostic description and error details.",
                examples = {"PostgreSQL deadlock detected when processing over 5,000 employee payroll records concurrently in ERP-RRHH."}
        )
        String description,

        @Schema(
                description = "Current lifecycle state of the ticket.",
                examples = {"IN_PROGRESS"},
                enumeration = {"OPEN", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"}
        )
        TicketStatus status,

        @Schema(
                description = "Operational urgency and severity level.",
                examples = {"CRITICAL"},
                enumeration = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
        )
        Priority priority,

        @Schema(
                description = "Impacted ERP functional module.",
                examples = {"HUMAN_RESOURCES"},
                enumeration = {"FINANCE", "HUMAN_RESOURCES", "INVENTORY", "SALES", "CRM", "SUPPLY_CHAIN"}
        )
        ErpModule erpModule,

        @Schema(
                description = "Corporate user or tenant identifier who reported the ticket.",
                examples = {"USR-CORP-98421"}
        )
        String requesterId,

        @Schema(
                description = "Flag indicating premium enterprise SLA entitlement.",
                examples = {"true"}
        )
        boolean vipCustomer,

        @Schema(
                description = "Support specialist assigned to diagnose and resolve the issue.",
                examples = {"AGT-TI-5042"},
                nullable = true
        )
        String assignedAgentId,

        @Schema(
                description = "Diagnostic and technical solution summary provided upon resolution.",
                examples = {"Adjusted isolation level and optimized batch chunking size in payroll transaction manager."},
                nullable = true
        )
        String resolutionNotes,

        @Schema(
                description = "Contractual UTC deadline for initial agent response.",
                examples = {"2026-08-25T14:30:00Z"}
        )
        Instant responseDeadline,

        @Schema(
                description = "Contractual UTC deadline for full incident resolution.",
                examples = {"2026-08-25T18:30:00Z"}
        )
        Instant resolutionDeadline,

        @Schema(
                description = "Indicates whether the initial response breached SLA limits.",
                examples = {"false"}
        )
        boolean isResponseSlaBreached,

        @Schema(
                description = "Indicates whether the final resolution breached SLA limits.",
                examples = {"false"}
        )
        boolean isResolutionSlaBreached,

        @Schema(
                description = "Customer satisfaction rating score from 1 (poor) to 5 (excellent).",
                examples = {"5"},
                nullable = true,
                minimum = "1",
                maximum = "5"
        )
        Integer csatRating,

        @Schema(
                description = "Qualitative feedback comments submitted by the user upon ticket closure.",
                examples = {"Issue resolved swiftly before the end-of-month payroll cutoff. Great support!"},
                nullable = true
        )
        String csatComment,

        @Schema(
                description = "Exact UTC instant of ticket creation.",
                examples = {"2026-08-25T12:30:00Z"}
        )
        Instant createdAt,

        @Schema(
                description = "Exact UTC instant of most recent ticket state modification.",
                examples = {"2026-08-25T13:00:00Z"}
        )
        Instant updatedAt,

        @Schema(
                description = "Exact UTC instant when the ticket transitioned to RESOLVED status.",
                examples = {"2026-08-25T16:15:00Z"},
                nullable = true
        )
        Instant resolvedAt,

        @Schema(
                description = "Exact UTC instant when the ticket transitioned to terminal CLOSED status.",
                examples = {"2026-08-25T16:45:00Z"},
                nullable = true
        )
        Instant closedAt
) {
}
