package org.softtech.infrastructure.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


@OpenAPIDefinition(
        info = @Info(
                title = "SoftTech Solutions - HelpDesk & Incident Management Reactive API",
                version = "1.0.0",
                description = "High-performance non-blocking reactive microservice for enterprise ticket lifecycle management, "
                        + "automated SLA breach calculations, distributed Redis caching, and Apache Kafka event streaming.",
                contact = @Contact(
                        name = "SoftTech Enterprise Architecture Team",
                        email = "architecture@softtech.com",
                        url = "https://helpdesk.softtech.com/support"
                ),
                license = @License(
                        name = "SoftTech Enterprise Commercial License",
                        url = "https://softtech.com/legal/license"
                )
        ),
        servers = {
                @Server(url = "/", description = "Default API Gateway / Microservice Base Path")
        },
        tags = {
                @Tag(
                        name = "Tickets",
                        description = "Reactive endpoints for support ticket creation, assignment, resolution, "
                                + "feedback closure, and multi-criteria queries."
                )
        },
        security = {
                @SecurityRequirement(name = "jwtAuth")
        }
)
@SecurityScheme(
        securitySchemeName = "jwtAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Cryptographically signed JWT Bearer token containing user identity and RBAC roles "
                + "(e.g., CLIENTE, SOPORTE_TI, ADMIN) issued by the SoftTech Identity Provider (IdP)."
)
public class OpenApiConfig extends Application {
}
