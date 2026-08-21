package org.softtech.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Represents the lifecycle states of a Helpdesk Support Ticket within the SoftTech ERP platform.
 *
 * This enumeration models a deterministic finite-state machine enforcing valid business
 * state transitions. IN accordance with ISO/IEC 25010 Reliability standards (Fault Tolerance and State Integrity), Illegal transitions are strictly rejected at the domain level before
 * reaching persistence or messaging infrastructure.
 *
 *
 */
public enum TicketStatus {


    /**
     * Initial state. the ticket has been created and registered in the System
     * but has not yet been assigned to a technical support agent.
     */
    OPEN {
        @Override
        public Set<TicketStatus> allowedNextStates(){
            return EnumSet.of(ASSIGNED, CANCELLED);
        }
    },

    /**
     * The ticket has been assigned to a specific support agent or queue
     * and is pending active troubleshooting.
     */
    ASSIGNED {
        public Set<TicketStatus> allowedNextStates(){
            return EnumSet.of(IN_PROGRESS, OPEN, CANCELLED);
        }
    },


    /**
     * The support engineer is actively investigating or implementing a solution.
     * SLA operational response time is actively tracked in this state
     */
    IN_PROGRESS{
        public Set<TicketStatus> allowedNextStates(){
            return EnumSet.of(RESOLVED, ASSIGNED, CANCELLED);
        }
    },


    /**
     * A proposed solution has been delivered. Awaiting customer confirmation,
     * CSAT survey feedback, or automated SLA closure.
     */
    RESOLVED{
        public Set<TicketStatus> allowedNextStates(){
            return EnumSet.of(CLOSED, IN_PROGRESS);
        }
    },


    /**
     * Terminal state. the resolution has been accepted by the requester or auto-closed
     * after the SLA feedback window. CSAT metrics are frozen.
     */
    CLOSED{
        public Set<TicketStatus> allowedNextStates(){
            return EnumSet.noneOf(TicketStatus.class);
        }
    },


    /**
     * Terminal state. The ticket was marked as duplicate, invalid, or retracted
     * by an authorized administrator or requester.
     */
    CANCELLED{
        public Set<TicketStatus> allowedNextStates(){
            return EnumSet.noneOf(TicketStatus.class);
        }
    };


    /**
     * Returns the set of immutable states to which this current state can legally transition
     * @return an EnumSet containing all valid target TicketStatus instances
     */
    public abstract Set<TicketStatus> allowedNextStates();


    /**
     * Evaluates whether a transition from the current state to the target is permitted
     *
     * @param targetStatus the desired destination TicketStatus Must not be null
     * @return True if the transition is allowed by business rules; false otherwise.
     */
    public boolean canTransitionTo(TicketStatus targetStatus){
        if (targetStatus == null){
            return false;
        }
        return allowedNextStates().contains(targetStatus);
    }


    /**
     * Determines whether the current state represents a final, immutable terminal state.
     *
     * @return true if the state is CLOSED or CANCELLED ; false otherwise.
     */
    public boolean isTerminal(){
        return this == CLOSED || this == CANCELLED;
    }


    /**
     * Determines whether the ticket is currently active and requires technical attention.
     *
     * @return true if the status is OPEN, ASSIGNED or IN_PROGRESS
     */
    public boolean isActive(){
        return this == OPEN || this == ASSIGNED || this == IN_PROGRESS;
    }
}

