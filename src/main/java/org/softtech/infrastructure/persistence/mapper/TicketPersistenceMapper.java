package org.softtech.infrastructure.persistence.mapper;


import jakarta.enterprise.context.ApplicationScoped;
import org.softtech.domain.model.*;
import org.softtech.infrastructure.persistence.document.TicketDocument;
import org.softtech.infrastructure.persistence.document.TicketDocument.FeedbackDocument;
import org.softtech.infrastructure.persistence.document.TicketDocument.SlaPolicyDocument;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * Bidirectional mapper component translating between the pure Domain Ticket Aggregate Root
 * and the MongoDB BSON TicketDocument persistence schema.
 * <p>
 * In strict compliance with Hexagonal Architecture (Ports and Adapters) and Domain-Driven Design (DDD),
 * this mapper isolates infrastructure BSON serialization constraints from domain business rules.
 * It provides loss-free conversion of Duration Value Objects into standard 64-bit millisecond primitives,
 * enforces defensive copies of internal audit collections, and reconstructs aggregate invariants in accordance
 * with ISO/IEC 25010 Reliability (Data Integrity) and CMMI Level 2/3 Service Operations standards.
 * </p>
 *
 * @author SoftTech Architecture Team
 * @version 1.0.0
 */
@ApplicationScoped
public class TicketPersistenceMapper {


    /**
     * Converts a pure domain Ticket aggregate root into a MongoDB TicketDocument.
     *
     * @param domain the domain aggregate to transform. Must not be null.
     * @return a fully populated TicketDocument ready for BSON persistence.
     * @throws NullPointerException if domain is null.
     */
    public TicketDocument toDocument(Ticket domain){
        Objects.requireNonNull(domain, "Domain Ticket aggregate must not be null for document conversion");

        return TicketDocument.builder()
                .id(domain.getId())
                .ticketNumber(domain.getTicketNumber())
                .title(domain.getTitle())
                .description(domain.getDescription())
                .status(domain.getStatus().name())
                .priority(domain.getPriority().name())
                .erpModule(domain.getErpModule().name())
                .requesterId(domain.getRequesterId())
                .assignedAgentId(domain.getAssignedAgentId())
                .vipCustomer(domain.isVipCustomer())
                .slaPolicy(toSlaPolicyDocument(domain.getSlaPolicy()))
                .feedback(toFeedbackDocument(domain.getFeedback()))
                .notes(domain.getNotes() != null ? new ArrayList<>(domain.getNotes()) : Collections.emptyList())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .firstResponseAt(domain.getFirstResponseAt())
                .resolveAt(domain.getUpdatedAt())
                .firstResponseAt(domain.getFirstResponseAt())
                .resolveAt(domain.getResolvedAt())
                .closedAt(domain.getClosedAt())
                .build();

    }



    /**
     * Reconstitutes a pure domain Ticket aggregate root from a persisted MongoDB TicketDocument.
     *
     * @param document the BSON document retrieved from MongoDB. Must not be null.
     * @return a fully populated, immutable Ticket domain aggregate root.
     * @throws NullPointerException if {@code document} is null.
     */
    public Ticket toDomain(TicketDocument document){
        Objects.requireNonNull(document, "TicketDocument must not be null for domain aggregate reconstruction");

        List<String> auditNotes = document.getNotes() != null ? Collections.unmodifiableList(new ArrayList<>(document.getNotes())) : Collections.emptyList();

        return Ticket.builder()
                .id(document.getId())
                .ticketNumber(document.getTicketNumber())
                .title(document.getTitle())
                .description(document.getDescription())
                .status(TicketStatus.valueOf(document.getStatus()))
                .priority(Priority.valueOf(document.getPriority()))
                .erpModule(ErpModule.valueOf(document.getErpModule()))
                .requesterId(document.getRequesterId())
                .assignedAgentId(document.getAssignedAgentId())
                .vipCustomer(document.isVipCustomer())
                .slaPolicy(toSlaPolicyDomain(document.getSlaPolicy()))
                .feedback(toFeedbackDomain(document.getFeedback()))
                .notes(auditNotes)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .firstResponseAt(document.getFirstResponseAt())
                .resolvedAt(document.getResolveAt())
                .closedAt(document.getClosedAt())
                .build();
    }



    /**
     * Translates a domain {@link SlaPolicy} Value Object into an embedded {@link SlaPolicyDocument} record.
     * Converts Duration instances into 64-bit millisecond primitives for seamless BSON serialization.
     *
     * @param domainPolicy the domain SLA policy Value Object. Can be null.
     * @return a populated {@link SlaPolicyDocument} record, or null if input is null.
     */
    private SlaPolicyDocument toSlaPolicyDocument(SlaPolicy domainPolicy){
        if (domainPolicy == null){
            return null;
        }

        Long responseDurationMilis = domainPolicy.getMaxResponseDuration() != null ? domainPolicy.getMaxResponseDuration().toMillis() : null;

        Long resolutionDurationMilis = domainPolicy.getMaxResolutionDuration() != null ? domainPolicy.getMaxResolutionDuration().toMillis() : null;

        return new SlaPolicyDocument(
                domainPolicy.getResponseDeadline(),
                domainPolicy.getResolutionDeadline(),
                responseDurationMilis,
                resolutionDurationMilis,
                domainPolicy.isEscalationRequired()
        );
    }



    /**
     * Reconstructs a domain {@link SlaPolicy} Value Object from an embedded {@link SlaPolicyDocument} record.
     * Restores Duration instances from persisted millisecond values.
     *
     * @param document the embedded BSON SLA policy record. Can be null.
     * @return a populated {@link SlaPolicy} domain Value Object, or null if input is null.
     */
    private SlaPolicy toSlaPolicyDomain(SlaPolicyDocument document){
        if (document == null){
            return null;
        }

        Duration responseDuration = document.maxResponseDurationMillis() != null
                ? Duration.ofMillis(document.maxResponseDurationMillis())
                : Duration.ZERO;

        Duration resolutionDuration = document.maxResolutionDurationMillis() != null
                ? Duration.ofMillis(document.maxResolutionDurationMillis())
                : Duration.ZERO;

        return SlaPolicy.builder()
                .responseDeadline(document.responseDeadline())
                .resolutionDeadline(document.resolutionDeadline())
                .maxResponseDuration(responseDuration)
                .maxResolutionDuration(resolutionDuration)
                .escalationRequired(document.escalationRequired())
                .build();

    }



    /**
     * Translates a domain {@link Feedback} Value Object into an embedded {@link FeedbackDocument} record.
     *
     * @param domainFeedback the domain customer feedback Value Object. Can be null.
     * @return a populated {@link FeedbackDocument} record, or null if input is null.
     */
    private FeedbackDocument toFeedbackDocument(Feedback domainFeedback){
        if (domainFeedback == null){
            return null;
        }

        return new FeedbackDocument(
                domainFeedback.getRating(),
                domainFeedback.getComment(),
                domainFeedback.getSubmittedAt()
                );
    }


    /**
     * Reconstructs a domain {@link Feedback} Value Object from an embedded {@link FeedbackDocument} record.
     *
     * @param doc the embedded BSON feedback record. Can be null.
     * @return a populated {@link Feedback} domain Value Object, or null if input is null.
     */
    private Feedback toFeedbackDomain(FeedbackDocument doc){
        if (doc == null){
            return null;
        }
        return Feedback.builder()
                .rating(doc.rating())
                .comment(doc.comment())
                .submittedAt(doc.submittedAt())
                .build();
    }
}
