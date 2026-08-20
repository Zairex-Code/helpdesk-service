# HelpDesk Service — Contexto del Proyecto

## 1. Información General y Problemática de Negocio

- **Organización:** SoftTech Solutions (proveedora de software ERP para pymes).
- **Situación actual:** alto volumen de quejas por desorganización en la gestión de
  solicitudes, respuestas inexactas, tiempos de respuesta prolongados y demoras críticas en
  la resolución de incidentes técnicos.
- **Propósito:** implementar un **microservicio reactivo y desacoplado de gestión de
  incidencias (HelpDesk)** que reemplace progresivamente el módulo de soporte del ERP
  monolítico, optimizando los flujos de atención, asegurando el cumplimiento de SLA y
  garantizando la calidad bajo estándares internacionales.

## 2. Objetivos

- **General:** plataforma reactiva de atención de tickets que optimice los tiempos de
  resolución, garantice la trazabilidad de incidentes y evalúe la satisfacción del cliente
  mediante métricas objetivas.
- **Específicos:**
  - Captura de incidencias con formulario guiado rápido (<15 s) y opciones precargadas del ERP.
  - Asignación automática de prioridades y expiración de SLA según criticidad y rol.
  - Tableros analíticos por rol con gráficos interactivos y consola de auditoría en vivo.
  - Alineación a **ISO/IEC 25010** (calidad) y **CMMI** (madurez de procesos).

## 3. Stack Tecnológico

### Backend
| Componente | Tecnología |
|---|---|
| Framework | Quarkus 3.x (Reactivo con Mutiny) |
| Lenguaje | Java 17 |
| Arquitectura | Hexagonal (Ports & Adapters) |
| Base de datos | MongoDB (NoSQL) + `quarkus-mongodb-panache` |
| Caché | Redis (`quarkus-redis-client`) — Cache-Aside |
| Mensajería | Apache Kafka (`quarkus-messaging-kafka`) — EDA y SAGA |
| Resiliencia | SmallRye Fault Tolerance (`@CircuitBreaker`, `@Retry`, `@Fallback`) |
| Salud | SmallRye Health (`/q/health/live`, `/q/health/ready`) |
| Seguridad | SmallRye JWT (RBAC, autenticación stateless) |
| Observabilidad | Lombok `@Slf4j` + MDC |
| Documentación | SmallRye OpenAPI (`/q/swagger-ui`) |
| Métricas | Micrometer + Prometheus |
| Pruebas | JUnit 5 + Mockito (unitarias), RestAssured + `@QuarkusTest` (integración) |
| QA | JaCoCo + SonarQube (cobertura >80%) |
| Entorno | Docker Compose (MongoDB, Redis, Kafka, Kafka UI) |

### Frontend (contexto de la solución global)
- Next.js (App Router) + React + TypeScript.
- Tailwind CSS + shadcn/ui + Lucide Icons + `next-themes`.
- Axios (interceptores JWT), React Hook Form + Zod, Recharts.

## 4. Patrones de Microservicios

- **Strangler Fig + DDD:** el soporte técnico es un *Bounded Context* que desacopla la
  gestión de incidencias del ERP legado.
- **CQRS:** escrituras persisten en MongoDB y emiten eventos a Kafka; lecturas (dashboards,
  listados) consumen caché Redis o índices optimizados en MongoDB.
- **SAGA (coreografía Kafka):** `TicketClosedEvent → Encuesta CSAT → Recálculo de métricas SLA`.
- **Cache-Aside:** verificación en Redis; ante *cache miss*, consulta no bloqueante a MongoDB
  y repoblación con TTL.
- **Circuit Breaker & Fallback:** aislamiento de fallos ante caídas de Redis/Kafka.
- **Rate Limiting:** control de concurrencia en endpoints públicos.
- **Health Checks:** supervisión de dependencias vía `/q/health`.

## 5. Requerimientos Funcionales (resumen)

| ID | Descripción |
|---|---|
| RF-01 | Login/logout con JWT: `cliente@softtech.com` (CLIENTE), `soporte@softtech.com` (AGENTE), `admin@softtech.com` (ADMIN/SUPERVISOR) |
| RF-02 | Alternancia de tema claro/oscuro |
| RF-03 | Creación guiada de tickets (<15 s) con selectores en cascada |
| RF-04 | Cálculo dinámico de prioridad y SLA en backend |
| RF-05 | Bandeja maestro-detalle del cliente (Open/Completed + Activity Feed) |
| RF-06 | Bandeja de agente con semáforos de SLA (verde/amarillo/rojo) |
| RF-07 | Calificación CSAT de 1–5 estrellas al resolverse |
| RF-08 | Dashboards por rol (Cliente, Agente, Supervisor/Calidad) |

## 6. Lógica de Negocio: Matriz de Prioridad y SLA

| Rol solicitante | Impacto reportado | Prioridad | SLA |
|---|---|---|---|
| Gerente / Jefe de Área | Caída total / todos afectados | Crítica | 2 h |
| Cualquier rol | Error bloqueante en facturación/ERP | Alta | 6 h |
| Usuario general | Degradación parcial / lentitud | Media | 24 h |
| Usuario general | Duda operativa / consulta | Baja | 48 h |

## 7. Modelo de Datos (MongoDB)

- **`tickets`:** `ticketNumber`, `title`, `description`, `erpModule`, `symptom`, `impact`,
  `affectedUsers`, `priority`, `status`, `slaDeadline`, `firstContactResolved`, `client`,
  `assignedAgent`, `attachments`, `activityLog`, `feedback` (CSAT), `createdAt`,
  `resolvedAt`, `closedAt`.
- **`audit_logs`** (asíncrona vía Kafka): `ticketId`, `ticketNumber`, `eventType`,
  `previousStatus`, `newStatus`, `executedBy`, `timestamp`.

Ciclo de vida del ticket: `Abierto → En Asignación → En Proceso → Resuelto → Cerrado`.

## 8. Requerimientos No Funcionales

- **RNF-01 (Desempeño):** respuesta < 200 ms en consultas de tickets (Mutiny + Redis).
- **RNF-02 (Fiabilidad):** continuidad ante caídas de caché (`@CircuitBreaker`/`@Fallback`).
- **RNF-03 (Mantenibilidad):** Arquitectura Hexagonal + cobertura >80% (JaCoCo/SonarQube).
- **RNF-04 (Trazabilidad/CMMI):** registro inmutable de eventos en `audit_logs` vía Kafka.
- **RNF-05 (Seguridad):** RBAC con JWT + Rate Limiting.

## 9. Observabilidad y Trazabilidad

Uso de Lombok `@Slf4j` con variables MDC (`ticketNumber`, `userId`). Trazabilidad por capas:
REST controllers (peticiones y tiempos), application services (decisiones de negocio),
adaptadores de salida (cache hits/misses, eventos Kafka) y consumidores asíncronos.

## 10. Estrategia de Pruebas y QA

- **Unitarias:** JUnit 5 + Mockito (lógica de dominio, cálculo de SLA, transiciones de estado).
- **Integración:** RestAssured + `@QuarkusTest` (contratos API y operaciones asíncronas).
- **Calidad:** JaCoCo + SonarQube (umbral de cobertura mínimo del 80%).

## 11. Estructura del Repositorio

```
helpdesk-service/
├── docker-compose.yaml        # Infraestructura local: MongoDB, Redis, Kafka, Kafka UI
├── pom.xml                    # Build Maven (Quarkus BOM 3.38.3)
├── PROJECT_CONTEXT.md         # Este documento
└── src/
    ├── main/
    │   ├── docker/            # Dockerfiles (JVM y native)
    │   ├── java/org/softtech/ # Código fuente (Arquitectura Hexagonal)
    │   └── resources/application.properties
    └── test/java/org/softtech/# Pruebas unitarias e integración
```

> **Nota:** el código fuente (`GreetingResource`, `MyMessagingApplication`) aún es boilerplate
> del *scaffold* inicial de Quarkus, pendiente de reemplazar por el dominio HelpDesk.

## 12. Puertos Locales (Docker Compose)

Re-mapeados para no colisionar con la infraestructura de otros proyectos:

| Servicio | Puerto interno | Puerto host |
|---|---|---|
| MongoDB | `27017` | `27018` |
| Redis | `6379` | `6380` |
| Kafka (INTERNAL) | `9092` | `9093` |
| Kafka (EXTERNAL) | `9094` | `9095` |
| Kafka UI | `8080` | `8088` |

La conexión de la aplicación (dev/host) se configura en `application.properties`:
`localhost:27018` (MongoDB), `localhost:6380` (Redis), `localhost:9095` (Kafka).