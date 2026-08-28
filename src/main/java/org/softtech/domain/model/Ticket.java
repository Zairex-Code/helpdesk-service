package org.softtech.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.softtech.domain.exception.InvalidStatusTransitionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * Aggregate Root representing a HelpDesk Support Ticket within the SoftTech Solutions ERP.
 *
 * This domain entity encapsulates all state, invariants, lifecycle transitions, SLA evaluations,
 * and historical audit notes for an enterprise issue. Adhering to Domain-Driven Design (DDD)
 * and ISO/IEC 25010 Quality in Use standards (Reliability and Integrity), the aggregate enforces
 * absolute immutability: all state-modifying operations yield a new verified instance of Ticket
 * without leaking mutable internal state or relying on infrastructure framework annotations
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Ticket {
    private final String id;
    private final String ticketNumber;
    private final String title;
    private final String description;
    private final TicketStatus status;
    private final Priority priority;
    private final ErpModule erpModule;
    private final String requesterId;
    private final String assignedAgentId;
    private final boolean vipCustomer;
    private final SlaPolicy slaPolicy;
    private final Feedback feedback;
    private final List<String> notes;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant firstResponseAt;
    private final Instant resolvedAt;
    private final Instant closedAt;


    /**
     * Factory method to create a new, immutable Ticket aggregate initial TicketStatus.OPEN state
     *
     * @param id the unique technical persistence identifier (UUID/BSON String). Must not be null
     * @param ticketNumber the business-readable tracking sequence "TICK-2026-0001". Must not be null or blank
     * @param title the concience summary of the incident. Must not be null or blank.
     * @param description the full technical context of the defect. Must not be null or blank
     * @param priority the operational urgency and severity level. Must not be null.
     * @param erpModule the affected ERP functional module. Must not be null.
     * @param requesterId the identifier pf the reporting user or corporate tenant. Must not be null or blank
     * @param isVipCustomer Indicates whether the requester holds high-priority enterprise SLA coverage
     * @param createdAt initial creation timestamp in UTC. Must not be null.
     * @return a fully initialized, valid Ticket instance in TicketStatus.OPEN status
     * @throws NullPointerException if any mandatory parameter is null
     * @throws IllegalArgumentException if string parameters are blank or empty.
     */
    public static Ticket created(String id,
                                 String ticketNumber,
                                 String title,
                                 String description,
                                 Priority priority,
                                 ErpModule erpModule,
                                 String requesterId,
                                 boolean isVipCustomer,
                                 Instant createdAt){
        Objects.requireNonNull(id, "Ticket ID must not be null");
        Objects.requireNonNull(ticketNumber, "Ticket number must not be null");
        Objects.requireNonNull(title, "Title must not be null");
        Objects.requireNonNull(description, "Description must not be null");
        Objects.requireNonNull(priority, "Priority must not be null");
        Objects.requireNonNull(erpModule, "ErpModule must not be null");
        Objects.requireNonNull(requesterId, "requester Id must not be null");
        Objects.requireNonNull(createdAt, "Creation timestamp must not be null");


        if (ticketNumber.isBlank()){
            throw new IllegalArgumentException("Ticket number must not be blank");
        }
        if (title.isBlank()){
            throw new IllegalArgumentException("Ticket title must not be blank");
        }
        if (description.isBlank()){
            throw new IllegalArgumentException("Ticket description must not be blank");
        }
        if (requesterId.isBlank()){
            throw new IllegalArgumentException("Requester ID must not be blank");
        }

        SlaPolicy calculatedPolicy = SlaPolicy.calculatePolicy(createdAt,priority,erpModule,isVipCustomer);

        return Ticket.builder()
                .id(id)
                .ticketNumber(ticketNumber.trim())
                .title(title.trim())
                .description(description.trim())
                .status(TicketStatus.OPEN)
                .priority(priority)
                .erpModule(erpModule)
                .requesterId(requesterId.trim())
                .assignedAgentId(null)
                .vipCustomer(isVipCustomer)
                .slaPolicy(calculatedPolicy)
                .feedback(null)
                .notes(Collections.emptyList())
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .firstResponseAt(null)
                .resolvedAt(null)
                .closedAt(null)
                .build();
    }


    /**
     * Assigns the ticket to technical support specialist, transitioning the status to TicketStatus.ASSIGNED
     *
     * @param agentId the unique identifier of the support engineer. Must not be null or blank
     * @param assignedAt the timestamp when assignment occurred. Must not be null.
     * @return a new Ticket instance with updated status, assigned agent, and audit timestamp.
     * @throws InvalidStatusTransitionException if the state machine transition from current status to TicketStatus.ASSIGNED is illegal
     * @throws NullPointerException if agentId or assignedAt is null.
     * @throws IllegalArgumentException if agentId is blank
     */
    public Ticket assignToAgent(String agentId, Instant assignedAt){
        Objects.requireNonNull(agentId, "Agent ID must not be null");
        Objects.requireNonNull(assignedAt, "Assignment timestamp must not be null");

        if (agentId.isBlank()){
            throw new IllegalArgumentException("Agent ID must not be blank");
        }

        validateTransition(TicketStatus.ASSIGNED);

        Instant firstResponse = (this.firstResponseAt == null) ? assignedAt : this.firstResponseAt;

        List<String> updatedNotes = new ArrayList<>(this.notes != null ? this.notes : Collections.emptyList());
        updatedNotes.add(String.format("[%s] Assigned to agent: %s", assignedAt, agentId.trim()));

        return this.toBuilder()
                .assignedAgentId(agentId.trim())
                .status(TicketStatus.ASSIGNED)
                .firstResponseAt(firstResponse)
                .updatedAt(assignedAt)
                .notes(Collections.unmodifiableList(updatedNotes))
                .build();

    }

    /**
     * Transitions the ticket to TicketStatus#IN_PROGRESS when the assigned engineer begins active troubleshooting.
     *
     * @param startedAt the timestamp when active investigation started. Must not be null.
     * @return a new Ticket instance in TicketStatus#IN_PROGRESS status.
     * @throws InvalidStatusTransitionException if transitioning to TicketStatus#IN_PROGRESS violates the state machine.
     * @throws NullPointerException if startedAt is null.
     */
    public Ticket startInvestigation(Instant startedAt){
        Objects.requireNonNull(startedAt, "Investigation start timestamp must not be null");
        validateTransition(TicketStatus.IN_PROGRESS);

        List<String> updatedNotes = new ArrayList<>(this.notes != null ? this.notes : Collections.emptyList());
        updatedNotes.add(String.format("[%s] Technical investigation initiated", startedAt));

        return this.toBuilder()
                .status(TicketStatus.IN_PROGRESS)
                .updatedAt(startedAt)
                .notes(Collections.unmodifiableList(updatedNotes))
                .build();

    }

    /**
     * Transitions the ticket to TicketStatus#RESOLVED after delivering a verified technical solution.
     *
     * @param resolutionNote concise technical summary of the fix applied. Must not be null or blank.
     * @param resolvedAt the exact timestamp of technical resolution. Must not be null.
     * @return a new Ticket instance with updated resolution status, appended note, and timestamp.
     * @throws InvalidStatusTransitionException if transitioning to TicketStatus#RESOLVED violates the state machine.
     * @throws NullPointerException if resolutionNote or resolvedAt is null.
     * @throws IllegalArgumentException if resolutionNote is blank.
     */
    public Ticket resolve(String resolutionNote, Instant resolvedAt){
        Objects.requireNonNull(resolutionNote,"Resolution note must not be null");
        Objects.requireNonNull(resolvedAt, "Resolution timestamp must not be null");

        if (resolutionNote.isBlank()){
            throw new IllegalArgumentException("Resolution note must not be blank");
        }

        validateTransition(TicketStatus.RESOLVED);

        List<String> updatedNotes = new ArrayList<>(this.notes);
        updatedNotes.add(
                String.format("%s Resolution: %s", resolvedAt, resolutionNote.trim()));

        return this.toBuilder()
                .status(TicketStatus.RESOLVED)
                .resolvedAt(resolvedAt)
                .updatedAt(resolvedAt)
                .notes(Collections.unmodifiableList(updatedNotes))
                .build();
    }


    /**
     * Permanently closes the ticket in terminal status TicketStatus#CLOSED with optional CSAT feedback.
     *
     * @param customerFeedback the CSAT rating and comments submitted by the user. Can be null if auto-closed.
     * @param closedAt the exact timestamp of final closure. Must not be null.
     * @return a new Ticket instance in terminal TicketStatus#CLOSED status.
     * @throws InvalidStatusTransitionException if transitioning to TicketStatus#CLOSED violates the state machine.
     * @throws NullPointerException if closedAt is null.
     */
    public Ticket closeWithFeedback(Feedback customerFeedback, Instant closedAt){
        Objects.requireNonNull(closedAt, "Closure timestamp must not be null");
        validateTransition(TicketStatus.CLOSED);

        List<String> updatedNotes = new ArrayList<>(this.notes != null ? this.notes : Collections.emptyList());
        updatedNotes.add(String.format("[%s] Ticket closed. CSAT Recorded: %s",
                closedAt, (customerFeedback != null ? customerFeedback.getRating() + "/5" : "N/A")));

        return this.toBuilder()
                .status(TicketStatus.CLOSED)
                .feedback(customerFeedback)
                .closedAt(closedAt)
                .updatedAt(closedAt)
                .notes(Collections.unmodifiableList(updatedNotes))
                .build();
    }


    /**
     * Cancels the ticket in terminal status TicketStatus#CANCELLED, indicating invalidity or duplication.
     *
     * @param reason the business or operational rationale for cancellation. Must not be null or blank.
     * @param cancelledAt the timestamp when cancellation occurred. Must not be null.
     * @return a new Ticket instance in terminal TicketStatus#CANCELLED status.
     * @throws InvalidStatusTransitionException if transitioning to TicketStatus#CANCELLED violates the state machine.
     * @throws NullPointerException if reason or cancelledAt is null.
     * @throws IllegalArgumentException if reason is blank.
     */
    public Ticket cancel(String reason, Instant cancelledAt){
        Objects.requireNonNull(reason, "Cancellation reason must not be null");
        Objects.requireNonNull(cancelledAt, "Cancellation timestamp must not be null");

        if (reason.isBlank()){
            throw new IllegalArgumentException("Cancellation reason must not be blank ");
        }

        validateTransition(TicketStatus.CANCELLED);

        List<String> updatedNotes = new ArrayList<>(this.notes != null ? this.notes : Collections.emptyList());
        updatedNotes.add(String.format("[%s] Cancelled. Reason: %s", cancelledAt, reason.trim()));

        return this.toBuilder()
                .status(TicketStatus.CANCELLED)
                .updatedAt(cancelledAt)
                .notes(Collections.unmodifiableList(updatedNotes))
                .build();
    }


    /**
     * Appends an operational or diagnostic note to the ticket audit trail without altering status.
     *
     * @param noteContent the textual content of the note. Must not be null or blank.
     * @param notedAt the timestamp when the note was appended. Must not be null.
     * @return a new Ticket instance with the appended immutable note.
     * @throws NullPointerException if noteContent or notedAtis null.
     * @throws IllegalArgumentException if noteContent is blank.
     */
    public Ticket addInternalNote(String noteContent, Instant notedAt){
        Objects.requireNonNull(noteContent, "Note content must not be null");
        Objects.requireNonNull(notedAt, "Note timestamp must not be null");

        if (noteContent.isBlank()){
            throw new IllegalArgumentException("Note content must not be blank");
        }

        List<String> updatedNotes = new ArrayList<>(this.notes != null ? this.notes : Collections.emptyList());
        updatedNotes.add(String.format("[%s] Note: %s", notedAt, noteContent.trim()));

        return this.toBuilder()
                .updatedAt(notedAt)
                .notes(Collections.unmodifiableList(updatedNotes))
                .build();
    }


    /**
     * Returns an unmodifiable view of the internal diagnostic and audit notes list.
     *
     * @return an unmodifiable List containing audit notes.
     */
    public List<String> getNotes(){
        return this.notes != null ? Collections.unmodifiableList(this.notes) : Collections.emptyList();
    }



    /**
     * Evaluates if the ticket has breached its initial first-response SLA deadline.
     *
     * @param currentInstant the reference instant in time for evaluation. Must not be null.
     * @return true if response SLA is breached; false otherwise.
     */
    public boolean isFirstResponseBreached(Instant currentInstant){
        return this.slaPolicy.isResponseBreached(this.firstResponseAt, currentInstant);
    }


    /**
     * Evaluates if the ticket has breached its final resolution SLA deadline.
     *
     * @param currentInstant the reference instant in time for evaluation. Must not be null.
     * @return true if resolution SLA is breached; false otherwise.
     */
    public boolean isResolutionBreached(Instant currentInstant){
        return this.slaPolicy.isResolutionBreached(this.resolvedAt, currentInstant);
    }



    /**
     * Internal invariant validator enforcing deterministic finite-state machine (FSM) rules.
     *
     * @param targetStatus the desired destination TicketStatus.
     * @throws InvalidStatusTransitionException if the transition is explicitly prohibited by domain rules.
     */
    private void validateTransition(TicketStatus targetStatus){
        if (!this.status.canTransitionTo(targetStatus)){
            throw new InvalidStatusTransitionException(this.ticketNumber, this.status, targetStatus);
        }
    }
}
