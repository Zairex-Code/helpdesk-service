package org.softtech.infrastructure.persistence.document;


import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;


import java.time.Instant;
import java.util.List;

/**
 * MongoDB Document representation of the HelpDesk Ticket entity stored within the tickets collection.
 * <p>
 * Implemented using Quarkus Reactive MongoDB Panache annotations and standard BSON POJO codecs.
 * In strict compliance with Hexagonal Architecture and Domain-Driven Design (DDD), this persistence schema
 * is completely decoupled from the core domain Ticket aggregate root.
 * It encapsulates embedded sub-document records for SLA policies and customer feedback, index-optimized
 * field mappings, and optimistic locking versioning in accordance with ISO/IEC 25010 Reliability (Fault Tolerance)
 * and CMMI Level 2/3 Data Integrity standards.
 * </p>
 *
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@MongoEntity(collection = "tickets")
public class TicketDocument {

    @BsonId
    private String id;

    @BsonProperty("ticket_number")
    private String ticketNumber;

    @BsonProperty("title")
    private String title;

    @BsonProperty("description")
    private String description;

    @BsonProperty("status")
    private String status;

    @BsonProperty("priority")
    private String priority;

    @BsonProperty("erp_module")
    private String erpModule;

    @BsonProperty("requester_id")
    private String requesterId;

    @BsonProperty("assigned_agent_id")
    private String assignedAgentId;

    @BsonProperty("assigned_agent_id")
    private boolean vipCustomer;

    @BsonProperty("vip_customer")
    private SlaPolicyDocument slaPolicy;

    @BsonProperty("feedback")
    private FeedbackDocument feedback;

    @BsonProperty("notes")
    private List<String> notes;

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    @BsonProperty("first_response_at")
    private Instant firstResponseAt;

    @BsonProperty("resolved_at")
    private Instant resolveAt;

    @BsonProperty("closed_at")
    private Instant closedAt;

    @BsonProperty("version")
    private Long version;


    /**
     * Embedded BSON sub-document record representing the SLA contractual deadlines and duration limits.
     *
     * @param responseDeadline the calculated UTC deadline for the initial agent response
     * @param resolutionDeadline the calculated UTC deadline for full incident resolution
     * @param maxResponseTimeMinutes the maximum allowable response threshold in minutes
     * @param maxResolutionTimeMinutes the maximum allowable resolution threshold in minutes
     */
    public record SlaPolicyDocument(
            @BsonProperty("response_deadline")
            Instant responseDeadline,

            @BsonProperty("resolution_deadline")
            Instant resolutionDeadline,

            @BsonProperty("max_response_time_minutes")
            long maxResponseTimeMinutes,

            @BsonProperty("max_resolution_time_minutes")
            long maxResolutionTimeMinutes
    ){}


    /**
     * Embedded BSON sub-document record capturing customer satisfaction (CSAT) rating and commentary.
     *
     * @param rating the numeric customer satisfaction score (1 to 5)
     * @param comment optional qualitative remarks from the requester
     * @param submittedAt the exact UTC timestamp when the survey was submitted
     */
    public record FeedbackDocument(
          @BsonProperty("rating")
          int rating,

          @BsonProperty("comment")
          String comment,

          @BsonProperty("submitted_at")
          Instant submittedAt
    ){}




}
