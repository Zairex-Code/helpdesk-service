package org.softtech.infrastructure.entrypoints.rest;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.softtech.domain.model.TicketStatus;
import org.softtech.domain.port.in.AssignTicketUseCase;
import org.softtech.domain.port.in.AssignTicketUseCase.AssignTicketCommand;
import org.softtech.domain.port.in.CancelTicketUseCase;
import org.softtech.domain.port.in.CancelTicketUseCase.CancelTicketCommand;
import org.softtech.domain.port.in.CloseTicketUseCase;
import org.softtech.domain.port.in.CloseTicketUseCase.CloseTicketCommand;
import org.softtech.domain.port.in.CreateTicketUseCase;
import org.softtech.domain.port.in.CreateTicketUseCase.CreateTicketCommand;
import org.softtech.domain.port.in.GetTicketUseCase;
import org.softtech.domain.port.in.ResolveTicketUseCase;
import org.softtech.domain.port.in.ResolveTicketUseCase.ResolveTicketCommand;
import org.softtech.domain.port.in.StartInvestigationUseCase;
import org.softtech.domain.port.in.StartInvestigationUseCase.StartInvestigationCommand;
import org.softtech.infrastructure.entrypoints.rest.dto.AssignTicketRequestDto;
import org.softtech.infrastructure.entrypoints.rest.dto.CancelTicketRequestDto;
import org.softtech.infrastructure.entrypoints.rest.dto.CloseTicketRequestDto;
import org.softtech.infrastructure.entrypoints.rest.dto.ResolveTicketRequestDto;
import org.softtech.infrastructure.entrypoints.rest.dto.TicketRequestDto;
import org.softtech.infrastructure.entrypoints.rest.dto.TicketResponseDto;
import org.softtech.infrastructure.entrypoints.rest.mapper.TicketRestMapper;

/**
 * Reactive REST Entrypoint Adapter exposing the HelpDesk ticket lifecycle.
 * <p>
 * Implements the driving side of the Hexagonal Architecture, translating HTTP commands and queries
 * into inbound port invocations and mapping domain aggregates into transport-optimized DTO projections
 * via {@link TicketRestMapper}. Endpoints enforce RBAC through JWT claims using {@code @RolesAllowed}.
 * </p>
 */
@Path("/api/v1/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class TicketResource {

    private final CreateTicketUseCase createTicketUseCase;
    private final GetTicketUseCase getTicketUseCase;
    private final AssignTicketUseCase assignTicketUseCase;
    private final StartInvestigationUseCase startInvestigationUseCase;
    private final ResolveTicketUseCase resolveTicketUseCase;
    private final CloseTicketUseCase closeTicketUseCase;
    private final CancelTicketUseCase cancelTicketUseCase;
    private final TicketRestMapper ticketRestMapper;

    /**
     * CDI constructor injecting all inbound ports and the REST mapper.
     */
    @Inject
    public TicketResource(CreateTicketUseCase createTicketUseCase,
                          GetTicketUseCase getTicketUseCase,
                          AssignTicketUseCase assignTicketUseCase,
                          StartInvestigationUseCase startInvestigationUseCase,
                          ResolveTicketUseCase resolveTicketUseCase,
                          CloseTicketUseCase closeTicketUseCase,
                          CancelTicketUseCase cancelTicketUseCase,
                          TicketRestMapper ticketRestMapper) {
        this.createTicketUseCase = createTicketUseCase;
        this.getTicketUseCase = getTicketUseCase;
        this.assignTicketUseCase = assignTicketUseCase;
        this.startInvestigationUseCase = startInvestigationUseCase;
        this.resolveTicketUseCase = resolveTicketUseCase;
        this.closeTicketUseCase = closeTicketUseCase;
        this.cancelTicketUseCase = cancelTicketUseCase;
        this.ticketRestMapper = ticketRestMapper;
    }

    /**
     * Creates a new support ticket in {@code OPEN} status.
     *
     * @param request the validated creation payload.
     * @param uriInfo the contextual HTTP request URI information.
     * @return a {@link Uni} emitting HTTP 201 with the created ticket and a Location header.
     */
    @POST
    @RolesAllowed({Roles.CLIENTE, Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<Response> create(TicketRequestDto request, @Context UriInfo uriInfo) {
        CreateTicketCommand command = new CreateTicketCommand(
                request.title(),
                request.description(),
                request.priority(),
                request.erpModule(),
                request.requesterId(),
                request.vipCustomer()
        );

        return createTicketUseCase.execute(command)
                .map(ticketRestMapper::toResponseDto)
                .map(dto -> Response
                        .status(Response.Status.CREATED)
                        .location(uriInfo.getAbsolutePathBuilder().path(dto.id()).build())
                        .entity(dto)
                        .build());
    }

    /**
     * Retrieves a single ticket by its technical persistence identifier.
     */
    @GET
    @Path("/{id}")
    @RolesAllowed({Roles.CLIENTE, Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<TicketResponseDto> getById(@PathParam("id") String id) {
        return getTicketUseCase.getById(id).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Retrieves a single ticket by its business tracking sequence.
     */
    @GET
    @Path("/number/{ticketNumber}")
    @RolesAllowed({Roles.CLIENTE, Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<TicketResponseDto> getByTicketNumber(@PathParam("ticketNumber") String ticketNumber) {
        return getTicketUseCase.getByTicketNumber(ticketNumber).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Streams all support tickets.
     */
    @GET
    @RolesAllowed({Roles.CLIENTE, Roles.SOPORTE_TI, Roles.ADMIN})
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<TicketResponseDto> listAll() {
        return getTicketUseCase.listAll().map(ticketRestMapper::toResponseDto);
    }

    /**
     * Streams all tickets filtered by lifecycle status.
     */
    @GET
    @Path("/status/{status}")
    @RolesAllowed({Roles.CLIENTE, Roles.SOPORTE_TI, Roles.ADMIN})
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<TicketResponseDto> listByStatus(@PathParam("status") TicketStatus status) {
        return getTicketUseCase.listByStatus(status).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Streams all tickets reported by a specific requester.
     */
    @GET
    @Path("/requester/{requesterId}")
    @RolesAllowed({Roles.CLIENTE, Roles.SOPORTE_TI, Roles.ADMIN})
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<TicketResponseDto> listByRequester(@PathParam("requesterId") String requesterId) {
        return getTicketUseCase.listByRequesterId(requesterId).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Assigns a support ticket to a technical specialist.
     */
    @PATCH
    @Path("/{id}/assign")
    @RolesAllowed({Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<TicketResponseDto> assign(@PathParam("id") String id, AssignTicketRequestDto request) {
        AssignTicketCommand command = new AssignTicketCommand(id, request.assignedAgentId());
        return assignTicketUseCase.execute(command).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Marks an assigned ticket as {@code IN_PROGRESS}, starting active troubleshooting.
     */
    @PATCH
    @Path("/{id}/start-investigation")
    @RolesAllowed({Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<TicketResponseDto> startInvestigation(@PathParam("id") String id) {
        StartInvestigationCommand command = new StartInvestigationCommand(id);
        return startInvestigationUseCase.execute(command).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Marks an active ticket as {@code RESOLVED} with technical resolution notes.
     */
    @PATCH
    @Path("/{id}/resolve")
    @RolesAllowed({Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<TicketResponseDto> resolve(@PathParam("id") String id, ResolveTicketRequestDto request) {
        ResolveTicketCommand command = new ResolveTicketCommand(id, request.resolutionNotes());
        return resolveTicketUseCase.execute(command).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Closes a resolved ticket, optionally recording CSAT feedback.
     */
    @PATCH
    @Path("/{id}/close")
    @RolesAllowed({Roles.CLIENTE, Roles.ADMIN})
    public Uni<TicketResponseDto> close(@PathParam("id") String id, CloseTicketRequestDto request) {
        CloseTicketCommand command = new CloseTicketCommand(id, request.rating(), request.comment());
        return closeTicketUseCase.execute(command).map(ticketRestMapper::toResponseDto);
    }

    /**
     * Cancels a support ticket in a terminal {@code CANCELLED} state.
     */
    @PATCH
    @Path("/{id}/cancel")
    @RolesAllowed({Roles.SOPORTE_TI, Roles.ADMIN})
    public Uni<TicketResponseDto> cancel(@PathParam("id") String id, CancelTicketRequestDto request) {
        CancelTicketCommand command = new CancelTicketCommand(id, request.reason());
        return cancelTicketUseCase.execute(command).map(ticketRestMapper::toResponseDto);
    }
}
