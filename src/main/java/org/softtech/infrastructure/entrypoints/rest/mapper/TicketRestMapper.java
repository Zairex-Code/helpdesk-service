package org.softtech.infrastructure.entrypoints.rest.mapper;


import org.mapstruct.*;
import org.softtech.domain.model.Ticket;
import org.softtech.infrastructure.entrypoints.rest.dto.TicketResponseDto;

import java.time.Instant;
import java.util.List;


/**
 * Enterprise MapStruct translation mapper bridging pure domain aggregates with REST response projections.
 * <p>
 * Performs compile-time, zero-reflection flattening of the {@link Ticket} domain aggregate (including encapsulated
 * Value Objects such as {@code SlaPolicy} and {@code Feedback}) into the transport-optimized {@link TicketResponseDto}.
 * Dynamically computes SLA breach verdicts based on the current UTC timeline and ticket lifecycle timestamps.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Performance Efficiency (Time Behavior and Sub-millisecond Execution)
 * and CMMI Level 2/3 Verification standards, MapStruct generates direct Java bytecode invocations during the
 * Maven build phase, ensuring total compatibility with GraalVM native binary compilation.
 * </p>
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED
)
public interface TicketRestMapper {



    /**
     * Translates a rich {@link Ticket} domain aggregate root into a flattened {@link TicketResponseDto}.
     *
     * @param ticket the source domain aggregate root. Can be {@code null}.
     * @return the corresponding {@link TicketResponseDto} projection, or {@code null} if the input is {@code null}.
     */
    @Mapping(target = "responseDeadline", source = "slaPolicy.resolutionDeadline")
    @Mapping(target = "resolutionDeadline", source = "slaPolicy.resolutionDeadline")
    @Mapping(target = "csatRating", source = "feedback.rating")
    @Mapping(target = "csatComment", source = "feedback.comment")
    @Mapping(target = "isResponseSlaBreached", source = "tick", qualifiedByName = "mapIsResponseSlaBreached")
    @Mapping(target = "isResolutionSlaBreached", source = "ticket", qualifiedByName = "mapIsResolutionSlaBreached")
    TicketResponseDto toResponseDto(Ticket ticket);


    /**
     * Translates a collection of {@link Ticket} domain aggregates into an immutable list of {@link TicketResponseDto}.
     *
     * @param tickets the list of source domain aggregates.
     * @return the transformed list of {@link TicketResponseDto} instances.
     */
    List<TicketResponseDto> toResponseDtoList(List<Ticket> tickets);



    /**
     * Evaluates whether the initial response SLA has been breached against the current UTC timeline
     * or the effective resolution instant.
     *
     * @param ticket the ticket aggregate to evaluate.
     * @return {@code true} if response SLA limits were exceeded; {@code false} otherwise.
     */
    @Named("mapIsResponseSlaBreached")
    default boolean mapIsResponseSlaBreached(Ticket ticket){
        if (ticket == null || ticket.getSlaPolicy() == null){
            return false;
        }

        Instant evaluationInstant = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : Instant.now();

        return ticket.isResolutionBreached(evaluationInstant);

    }



    /**
     * Evaluates whether the incident resolution SLA has been breached against the closure timestamp
     * or the current UTC timeline.
     *
     * @param ticket the ticket aggregate to evaluate.
     * @return {@code true} if resolution SLA limits were exceeded; {@code false} otherwise.
     */
    @Named("mapIsResolutionSlaBreached")
    default boolean mapIsResolutionSlaBreached(Ticket ticket){
        if (ticket == null || ticket.getSlaPolicy() == null){
            return false;
        }

        Instant evalutionInstant = ticket.getClosedAt() != null ? ticket.getClosedAt() : Instant.now();

        return ticket.isResolutionBreached(evalutionInstant);
    }
}
