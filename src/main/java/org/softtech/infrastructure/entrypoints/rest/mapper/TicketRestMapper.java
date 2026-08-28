package org.softtech.infrastructure.entrypoints.rest.mapper;

import java.time.Instant;
import java.util.List;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.softtech.domain.model.Ticket;
import org.softtech.infrastructure.entrypoints.rest.dto.TicketResponseDto;

/**
 * Enterprise MapStruct translation mapper bridging pure domain aggregates with REST response projections.
 * <p>
 * Performs compile-time, zero-reflection flattening of the {@link Ticket} domain aggregate root (including encapsulated
 * Value Objects such as {@code SlaPolicy} and {@code Feedback}) into the transport-optimized {@link TicketResponseDto}.
 * Dynamically evaluates initial response and final resolution SLA compliance against real-time UTC timelines
 * and extracts structured resolution summaries from the aggregate audit trail.
 * </p>
 * <p>
 * In strict compliance with ISO/IEC 25010 Performance Efficiency (Sub-millisecond execution) and
 * CMMI Level 2/3 Verification standards, MapStruct generates direct Java bytecode invocations during the
 * Maven build phase, ensuring total compatibility with GraalVM native binary compilation.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED
)
public interface TicketRestMapper {

    /**
     * Canonical prefix utilized to delineate technical resolution entries within the internal aggregate notes trail.
     */
    String RESOLUTION_NOTE_PREFIX = " Resolution: ";

    /**
     * Translates a rich {@link Ticket} domain aggregate root into a flattened {@link TicketResponseDto}.
     *
     * @param ticket the source domain aggregate root. Can be {@code null}.
     * @return the corresponding {@link TicketResponseDto} projection, or {@code null} if the input is {@code null}.
     */
    @Mapping(target = "responseDeadline", source = "slaPolicy.responseDeadline")
    @Mapping(target = "resolutionDeadline", source = "slaPolicy.resolutionDeadline")
    @Mapping(target = "csatRating", source = "feedback.rating")
    @Mapping(target = "csatComment", source = "feedback.comment")
    @Mapping(target = "resolutionNotes", source = "ticket", qualifiedByName = "extractResolutionNotes")
    @Mapping(target = "isResponseSlaBreached", source = "ticket", qualifiedByName = "mapIsResponseSlaBreached")
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
     * Extracts the technical resolution note text from the immutable audit notes trail.
     *
     * @param ticket the source ticket aggregate.
     * @return the extracted resolution note content, or {@code null} if not yet resolved.
     */
    @Named("extractResolutionNotes")
    default String extractResolutionNotes(Ticket ticket) {
        if (ticket == null || ticket.getNotes() == null || ticket.getNotes().isEmpty()) {
            return null;
        }

        return ticket.getNotes().stream()
                .filter(note -> note.contains(RESOLUTION_NOTE_PREFIX))
                .map(note -> note.substring(note.indexOf(RESOLUTION_NOTE_PREFIX) + RESOLUTION_NOTE_PREFIX.length()).trim())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /**
     * Evaluates whether the initial response SLA has been breached against the recorded response timestamp
     * or the current UTC timeline.
     *
     * @param ticket the ticket aggregate to evaluate.
     * @return {@code true} if initial response SLA limits were exceeded; {@code false} otherwise.
     */
    @Named("mapIsResponseSlaBreached")
    default boolean mapIsResponseSlaBreached(Ticket ticket) {
        if (ticket == null || ticket.getSlaPolicy() == null) {
            return false;
        }
        Instant evaluationInstant = ticket.getFirstResponseAt() != null ? ticket.getFirstResponseAt() : Instant.now();
        return ticket.isFirstResponseBreached(evaluationInstant);
    }

    /**
     * Evaluates whether the incident resolution SLA has been breached against the closure timestamp
     * or the current UTC timeline.
     *
     * @param ticket the ticket aggregate to evaluate.
     * @return {@code true} if final resolution SLA limits were exceeded; {@code false} otherwise.
     */
    @Named("mapIsResolutionSlaBreached")
    default boolean mapIsResolutionSlaBreached(Ticket ticket) {
        if (ticket == null || ticket.getSlaPolicy() == null) {
            return false;
        }
        Instant evaluationInstant = ticket.getResolvedAt() != null
                ? ticket.getResolvedAt()
                : (ticket.getClosedAt() != null ? ticket.getClosedAt() : Instant.now());
        return ticket.isResolutionBreached(evaluationInstant);
    }
}