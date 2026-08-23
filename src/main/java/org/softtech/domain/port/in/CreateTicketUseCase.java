package org.softtech.domain.port.in;

import io.smallrye.mutiny.Uni;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;

import java.util.Objects;

/**
 * Inbound Port (Primary Port / Use Case interface) defining the reactive contract for creating
 * and registering a new HelpDesk Support Ticket within the SoftTech Solutions ERP ecosystem.
 *
 * In strict compliance with Hexagonal Architecture (Ports and Adapters) and Domain-Driven Design (DDD),
 * this interface exposes ticket creation to driving adapters (e.g., REST endpoints, Kafka message consumers,
 * or scheduled tasks). It decouples transport protocols from domain orchestration, executing in a fully
 * non-blocking reactive stream powered by SmallRye Mutiny {@link Uni}.
 *
 */
public interface CreateTicketUseCase {

    /**
     * Executes the creation and persistence of a new {@link Ticket} aggregate root reactively.
     *
     * @param command the immutable input data carrier containing verified creation parameters. Must not be {@code null}.
     * @return a {@link Uni} emitting the persisted {@link Ticket} aggregate upon successful processing.
     * @throws NullPointerException if {@code command} is {@code null}.
     * @throws IllegalArgumentException if string parameters inside {@code command} are blank.
     */
    Uni<Ticket> execute(CreateTicketCommand command);



    /**
     * Immutable Command record encapsulating the necessary parameters to request ticket creation.
     *
     * @param title the concise summary of the reported incident. Must not be {@code null} or blank.
     * @param description the comprehensive technical context and reproduction steps. Must not be {@code null} or blank.
     * @param priority the operational urgency and severity level. Must not be {@code null}.
     * @param erpModule the affected ERP functional module. Must not be {@code null}.
     * @param requesterId the enterprise user or tenant identifier reporting the incident. Must not be {@code null} or blank.
     * @param vipCustomer indicates whether the requester holds high-priority contractual SLA coverage.
     */
    record CreateTicketCommand(
                                String title,
                                String description,
                                Priority priority,
                                ErpModule erpModule,
                                String requesterId,
                                boolean vipCustomer){

        public CreateTicketCommand{
            Objects.requireNonNull(title, "Title must not be null");
            Objects.requireNonNull(description, "Description must not be null");
            Objects.requireNonNull(priority, "Priority must not be null");
            Objects.requireNonNull(erpModule, "ErpModule must not be null");
            Objects.requireNonNull(requesterId, "Requester ID must not be null");


            if (title.isBlank()){
                throw new IllegalArgumentException("Title cannot be blank");
            }
            if (description.isBlank()){
                throw new IllegalArgumentException("Description cannot be blank");
            }
            if (requesterId.isBlank()){
                throw new IllegalArgumentException("Requester ID must not be blank");
            }

        }
    }
}
