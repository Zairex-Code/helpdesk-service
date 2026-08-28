# SoftTech HelpDesk Service

Microservicio **reactivo y desacoplado** de gestión de incidencias (HelpDesk) para el ERP de **SoftTech Solutions**.
Implementa el ciclo de vida completo de un ticket de soporte (creación, asignación, investigación, resolución,
cierre con CSAT y cancelación), con cálculo dinámico de SLA, caché distribuida, mensajería por eventos y
seguridad RBAC, alineado a **ISO/IEC 25010** (calidad) y **CMMI Nivel 2/3** (madurez de procesos).

---

## Tabla de contenidos

1. [Stack Tecnológico](#1-stack-tecnológico)
2. [Arquitectura](#2-arquitectura)
3. [Modelo de Dominio](#3-modelo-de-dominio)
4. [API REST](#4-api-rest)
5. [Manejo de Errores](#5-manejo-de-errores)
6. [Configuración](#6-configuración)
7. [Infraestructura Local](#7-infraestructura-local)
8. [Ejecución y Build](#8-ejecución-y-build)
9. [Pruebas](#9-pruebas)
10. [Calidad de Código (QA)](#10-calidad-de-código-qa)
11. [CI/CD](#11-cicd)
12. [Pruebas con Postman](#12-pruebas-con-postman)
13. [Estructura del Repositorio](#13-estructura-del-repositorio)

---

## 1. Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Framework | Quarkus 3.38.3 (reactivo con Mutiny) |
| Lenguaje | Java 17 |
| Arquitectura | Hexagonal (Puertos y Adaptadores) + DDD + CQRS |
| Base de datos | MongoDB (NoSQL) con Panache Reactivo |
| Caché | Redis (patrón Cache-Aside) |
| Mensajería | Apache Kafka (SmallRye Reactive Messaging) |
| Seguridad | SmallRye JWT (RBAC stateless) |
| Validación | Hibernate Validator (Jakarta Bean Validation 3.0) |
| Documentación | SmallRye OpenAPI + Swagger UI |
| Observabilidad | Micrometer/Prometheus, SmallRye Health, Lombok `@Slf4j` + MDC |
| Pruebas | JUnit 5, Mockito, RestAssured, `@QuarkusTest`, `@TestSecurity` |
| QA | JaCoCo (cobertura) + SonarQube (análisis estático) |
| Build | Maven (wrapper `./mvnw`) |
| Infraestructura | Docker Compose (MongoDB, Redis, Kafka, Kafka UI) |

---

## 2. Arquitectura

Arquitectura hexagonal estricta con límites claros entre dominio puro, casos de uso y adaptadores de infraestructura.
El dominio no depende de ningún framework: los adaptadores de entrada (REST) y salida (Mongo/Redis/Kafka)
implementan **puertos** que definen los contratos.

```
            ┌───────────────────────────────────────────────────────┐
            │              ENTRADA (Driving Adapters)                │
            │   TicketResource · DTOs · TicketRestMapper · Roles      │
            └──────────────────────┬─────────────────────────────────┘
                                   │ usa
            ┌──────────────────────▼─────────────────────────────────┐
            │            PUERTOS DE ENTRADA (port/in)                 │
            │  Create · Get · Assign · StartInvestigation ·           │
            │  Resolve · Close · Cancel                               │
            └──────────────────────┬─────────────────────────────────┘
                                   │ implementa
            ┌──────────────────────▼─────────────────────────────────┐
            │       APLICACIÓN (application/usecase)                  │
            │   Orquestación reactiva con Mutiny + fallbacks          │
            └──────────┬───────────────────────────────┬─────────────┘
                       │ depende de (interfaces)       │
        ┌──────────────▼───────────────┐   ┌──────────▼───────────────┐
        │   PUERTOS DE SALIDA (port/out)│   │     DOMINIO (model)      │
        │  TicketPersistencePort        │   │  Ticket · TicketStatus   │
        │  TicketCachePort              │   │  SlaPolicy · Feedback    │
        │  TicketEventPublisherPort     │   │  Priority · ErpModule    │
        └──────────────┬───────────────┘   └──────────────────────────┘
                       │ implementa
            ┌──────────▼─────────────────────────────────────────────┐
            │       SALIDA (Driven Adapters)                          │
            │  TicketMongoAdapter · TicketRedisAdapter                │
            │  KafkaTicketEventPublisher · ReactiveTicketPanacheRepository│
            └─────────────────────────────────────────────────────────┘
```

**Patrones aplicados:** *Strangler Fig*, *CQRS* (escrituras → MongoDB + eventos; lecturas → Redis), *SAGA*
(coreografía Kafka), *Cache-Aside*, *Circuit Breaker/Fallback* (SmallRye Fault Tolerance), *DTO Flattening*,
*Repository Pattern*.

---

## 3. Modelo de Dominio

### 3.1 Ciclo de vida del ticket (máquina de estados finitos)

```
OPEN ──assign──► ASSIGNED ──startInvestigation──► IN_PROGRESS ──resolve──► RESOLVED ──close──► CLOSED
  │                 │                                 │                      │
  └──cancel─────────┴────────────────cancel──────────┘                      └──investigate──► IN_PROGRESS
```

| Estado | Transiciones permitidas | Terminal |
|---|---|---|
| `OPEN` | `ASSIGNED`, `CANCELLED` | No |
| `ASSIGNED` | `IN_PROGRESS`, `OPEN`, `CANCELLED` | No |
| `IN_PROGRESS` | `RESOLVED`, `ASSIGNED`, `CANCELLED` | No |
| `RESOLVED` | `CLOSED`, `IN_PROGRESS` | No |
| `CLOSED` / `CANCELLED` | — | Sí |

El agregado `Ticket` es **inmutable**: cada operación de estado retorna una nueva instancia, sin mutar la anterior.
Las transiciones ilegales lanzan `InvalidStatusTransitionException` (código `HD-DOM-4001`).

### 3.2 Política de SLA (`SlaPolicy`)

| Prioridad | Respuesta | Resolución | Escalado |
|---|---|---|---|
| `LOW` | 24 h | 72 h | No |
| `MEDIUM` | 8 h | 24 h | No |
| `HIGH` | 2 h | 8 h | No |
| `CRITICAL` | 30 min | 4 h | Sí |

- **Módulos misión-crítica** (`FINANCIAL`, `BILLING`, `CORE_SYSTEM`): reducen el plazo de resolución un **25%**.
- **Clientes VIP**: reducen el plazo de primera respuesta un **50%**.

| Módulo ERP | Criticidad | Escalado supervisor |
|---|---|---|
| `FINANCIAL` | 4 (crítico) | Sí |
| `BILLING` | 4 (crítico) | Sí |
| `CORE_SYSTEM` | 4 (crítico) | Sí |
| `INVENTORY` | 3 (alto) | No |
| `SALES` | 3 (alto) | No |
| `CRM` | 2 (medio) | No |
| `HUMAN_RESOURCES` | 2 (medio) | No |
| `SUPPLY_CHAIN` | 2 (medio) | No |

### 3.3 Feedback CSAT (`Feedback`)

Calificación de 1 a 5 estrellas con comentario opcional (máx. 500 caracteres). Clasificación:
`isSatisfactory()` (≥4), `isNeutral()` (=3), `isDetractor()` (≤2).

### 3.4 Eventos de dominio

| Evento | Tipo (`eventType`) | Emitido cuando... |
|---|---|---|
| `TicketCreatedEvent` | `HELP_DESK_TICKET_CREATED_V1` | Se crea un ticket |
| `TicketStatusChangedEvent` | `HELP_DESK_TICKET_STATUS_CHANGED_V1` | Hay una transición de estado (asignación, investigación, resolución, cancelación) |
| `TicketClosedEvent` | `HELP_DESK_TICKET_CLOSED_V1` | El ticket se cierra (incluye SLA breach y CSAT) |

Todos los eventos se publican al topic `helpdesk.ticket-events.v1` usando el `ticketNumber` como clave de partición
(orden cronológico garantizado por ticket).

---

## 4. API REST

Base URL: `/api/v1/tickets`. Autenticación **JWT Bearer**. Roles: `CLIENTE`, `SOPORTE_TI`, `ADMIN`.

### 4.0 Autenticación (solo desarrollo)

En producción la autenticación se delega al Identity Provider corporativo (RF-01). Para el desarrollo local,
existe un endpoint de login que emite JWTs firmados y **solo está disponible en el perfil `dev`**
(`./mvnw quarkus:dev`); se elimina automáticamente en builds de producción.

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Autentica un usuario de desarrollo y devuelve un JWT |

Usuarios de desarrollo (contraseña **`dylan`**):

| Email | Rol |
|---|---|
| `cliente@softtech.com` | `CLIENTE` |
| `soporte@softtech.com` | `SOPORTE_TI` |
| `admin@softtech.com` | `ADMIN` |

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "soporte@softtech.com", "password": "dylan"}'
# -> { "token": "<JWT>", "tokenType": "Bearer", "expiresInSeconds": 3600 }
```

> **Importante:** la clave privada (`privateKey.pem`) y el endpoint de login son **SOLO PARA DESARROLLO**.
> No deben usarse ni desplegarse en entornos de producción.

### 4.1 Endpoints de tickets

| Método | Endpoint | Descripción | Roles |
|---|---|---|---|
| `POST` | `/api/v1/tickets` | Crear ticket | `CLIENTE`, `SOPORTE_TI`, `ADMIN` |
| `GET` | `/api/v1/tickets/{id}` | Obtener por ID técnico | todos |
| `GET` | `/api/v1/tickets/number/{ticketNumber}` | Obtener por número | todos |
| `GET` | `/api/v1/tickets` | Listar todos (stream) | todos |
| `GET` | `/api/v1/tickets/status/{status}` | Listar por estado | todos |
| `GET` | `/api/v1/tickets/requester/{requesterId}` | Listar por solicitante | todos |
| `PATCH` | `/api/v1/tickets/{id}/assign` | Asignar a especialista | `SOPORTE_TI`, `ADMIN` |
| `PATCH` | `/api/v1/tickets/{id}/start-investigation` | Iniciar investigación | `SOPORTE_TI`, `ADMIN` |
| `PATCH` | `/api/v1/tickets/{id}/resolve` | Resolver con notas técnicas | `SOPORTE_TI`, `ADMIN` |
| `PATCH` | `/api/v1/tickets/{id}/close` | Cerrar con CSAT | `CLIENTE`, `ADMIN` |
| `PATCH` | `/api/v1/tickets/{id}/cancel` | Cancelar | `SOPORTE_TI`, `ADMIN` |

### Cuerpos de petición (Request DTOs)

**`TicketRequestDto` (crear)**
```json
{
  "title": "Database timeout in payroll batch",
  "description": "PostgreSQL deadlock detected during concurrent payroll execution.",
  "priority": "HIGH",
  "erpModule": "HUMAN_RESOURCES",
  "requesterId": "USR-CORP-98421",
  "vipCustomer": true
}
```

**`AssignTicketRequestDto` (asignar)**
```json
{ "assignedAgentId": "AGT-TI-5042" }
```

**`ResolveTicketRequestDto` (resolver)**
```json
{ "resolutionNotes": "Adjusted isolation level and optimized batch chunking size." }
```

**`CloseTicketRequestDto` (cerrar)**
```json
{ "rating": 5, "comment": "Excellent support!" }
```

**`CancelTicketRequestDto` (cancelar)**
```json
{ "reason": "Duplicate ticket already tracked under TICK-2026-0041" }
```

### Respuesta (TicketResponseDto)

La proyección de lectura aplana el agregado e incluye métricas de SLA calculadas en tiempo real:
`id`, `ticketNumber`, `title`, `description`, `status`, `priority`, `erpModule`, `requesterId`, `vipCustomer`,
`assignedAgentId`, `resolutionNotes`, `responseDeadline`, `resolutionDeadline`, `isResponseSlaBreached`,
`isResolutionSlaBreached`, `csatRating`, `csatComment`, `createdAt`, `updatedAt`, `resolvedAt`, `closedAt`.

### Ejemplo de uso (curl)

```bash
# Crear ticket
curl -X POST http://localhost:8080/api/v1/tickets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{
    "title": "Database timeout in payroll batch",
    "description": "PostgreSQL deadlock detected during concurrent payroll execution.",
    "priority": "HIGH",
    "erpModule": "HUMAN_RESOURCES",
    "requesterId": "USR-CORP-98421",
    "vipCustomer": true
  }'

# Asignar (requiere rol SOPORTE_TI)
curl -X PATCH http://localhost:8080/api/v1/tickets/{id}/assign \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{"assignedAgentId": "AGT-TI-5042"}'
```

---

## 5. Manejo de Errores

Todos los errores devuelven `ErrorResponseDto` (estándar **RFC 7807 / RFC 9457**) con `type`, `title`, `status`,
`detail`, `errorCode`, `correlationId` y `timestamp`.

| Código HTTP | errorCode | Escenario |
|---|---|---|
| `400` | `ERR_HD_VALIDATION_FAILED` | Violación de Bean Validation (con mapa `violations`) |
| `400` | `ERR_HD_ILLEGAL_ARGUMENT` | Argumento inválido |
| `404` | `ERR_HD_TICKET_NOT_FOUND` | Ticket inexistente |
| `409` | `ERR_HD_INVALID_STATUS_TRANSITION` | Transición de estado ilegal |
| `422` | `ERR_HD_DOMAIN_RULE_VIOLATION` | Invariante de dominio |
| `500` | `ERR_HD_INTERNAL_SERVER_ERROR` | Fallo interno |

Ejemplo de respuesta 400 (validación):
```json
{
  "type": "https://helpdesk.softtech.com/errors/validation-violation",
  "title": "Validation Constraint Violation",
  "status": 400,
  "detail": "One or more payload attributes failed declarative validation constraints.",
  "instance": "/api/v1/tickets",
  "errorCode": "ERR_HD_VALIDATION_FAILED",
  "violations": { "title": "Ticket title must not be blank" },
  "correlationId": "c3d9a1f4-8b2e-4e71-9c63-1a2b3c4d5e6f",
  "timestamp": "2026-08-27T11:45:00Z"
}
```

---

## 6. Configuración

La configuración vive en `src/main/resources/application.properties` y es sobreescribible por variables de entorno:

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `MONGODB_URI` | `mongodb://helpdesk_admin:helpdesk_secret_password@localhost:27018/helpdesk_db?authSource=admin` | Conexión MongoDB |
| `MONGODB_DATABASE` | `helpdesk_db` | Base de datos |
| `REDIS_URI` | `redis://:redis_secret_password@localhost:6380` | Conexión Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9095` | Bootstrap Kafka |
| `KAFKA_TICKET_EVENTS_TOPIC` | `helpdesk.ticket-events.v1` | Topic de eventos |
| `JWT_PUBLIC_KEY_LOCATION` | `/publicKey.pem` | Clave pública JWT |
| `JWT_ISSUER` | `https://auth.softtech.com/oauth2/token` | Emisor JWT |

---

## 7. Infraestructura Local

```bash
docker compose up -d
```

| Servicio | Puerto host | Puerto interno |
|---|---|---|
| MongoDB | `27018` | `27017` |
| Redis | `6380` | `6379` |
| Kafka (KRaft, externo) | `9095` | `9094` |
| Kafka UI | `8088` | `8080` |

Los puertos se re-mapean para no colisionar con otros proyectos. Kafka UI permite inspeccionar el topic
`helpdesk.ticket-events.v1` en `http://localhost:8088`.

---

## 8. Ejecución y Build

```bash
# Modo desarrollo (hot reload + Dev UI + Swagger)
./mvnw quarkus:dev

# Swagger UI / OpenAPI
#   http://localhost:8080/q/swagger-ui
#   http://localhost:8080/q/openapi
# Health checks
#   http://localhost:8080/q/health/live
#   http://localhost:8080/q/health/ready

# Empaquetar
./mvnw package

# Ejecutar el jar
java -jar target/quarkus-app/quarkus-run.jar

# Ejecutar en modo nativo (requiere GraalVM)
./mvnw package -Dnative
```

---

## 9. Pruebas

**Pirámide de pruebas** implementada:

| Nivel | Ubicación | Enfoque |
|---|---|---|
| Dominio puro | `domain/model`, `domain/event`, `domain/exception` | FSM, SLA, Feedback, inmutabilidad, eventos, excepciones |
| Casos de uso | `application/usecase` | Orquestación reactiva, fallbacks, degradación elegante (Mockito + `UniAssertSubscriber`) |
| Mappers/Adaptadores | `infrastructure/*` | Mapeo dominio↔documento/DTO, Redis, Kafka, Mongo, repositorio Panache |
| Integración REST | `infrastructure/entrypoints/rest` | Contrato HTTP, validación, RBAC, ciclo de vida E2E (`@QuarkusTest` + RestAssured + `@TestSecurity`) |

```bash
# Ejecutar todas las pruebas + verificación de cobertura
./mvnw clean verify

# Ejecutar solo un caso de uso
./mvnw test -Dtest='AssignTicketUseCaseTest'

# Ejecutar la suite de integración REST
./mvnw test -Dtest='TicketResourceTest'
```

> **Nota:** las pruebas de integración REST son **herméticas** (usan puertos de salida mockeados con `@InjectMock`
> y el conector de mensajería `smallrye-in-memory`), por lo que no requieren Docker para ejecutarse.

---

## 10. Calidad de Código (QA)

### JaCoCo (cobertura)

Configurado en `pom.xml` con verificación en la fase `verify`:

- **Líneas** (`LINE`) ≥ **80%**
- **Instrucciones** (`INSTRUCTION`) ≥ **80%**
- **Ramas** (`BRANCH`) ≥ **70%**

El reporte se genera en `target/site/jacoco/index.html`. Si la cobertura cae por debajo del umbral, `mvn verify` **falla**.

```bash
./mvnw clean verify          # verifica cobertura y genera el reporte
```

### SonarQube (análisis estático)

El plugin `sonar-maven-plugin` está configurado con `sonar.projectKey`, `sonar.projectName` y la ruta del
reporte JaCoCo XML. **El host y el token se inyectan por línea de comandos**:

```bash
./mvnw clean verify sonar:sonar \
  -Dsonar.host.url=https://tu-sonarqube \
  -Dsonar.token=<TOKEN>
```

---

## 11. CI/CD

Workflow de GitHub Actions en `.github/workflows/ci.yml`: en cada `push`/`pull_request` a `main` ejecuta
`./mvnw verify -B` (compilación + pruebas unitarias e integración + verificación de cobertura JaCoCo).
Incluye un paso opcional de **SonarQube** que solo se ejecuta si el secret `SONAR_TOKEN` está configurado.

---

## 12. Pruebas con Postman

Se incluye una colección lista para importar en **Postman**:

```
postman/SoftTech_HelpDesk_Service.postman_collection.json
```

### Paso a paso

1. **Arranca la aplicación** (y, opcionalmente, la infraestructura):
   ```bash
   docker compose up -d     # opcional (MongoDB, Redis, Kafka)
   ./mvnw quarkus:dev       # la app en http://localhost:8080
   ```

2. **Abre Postman** → *Import* → arrastra o selecciona
   `postman/SoftTech_HelpDesk_Service.postman_collection.json`.

3. **Haz login** (carpeta *0. Auth (login)*):
   - Ejecuta la petición **"Login"**. Está pre-configurada con `soporte@softtech.com` / `dylan`.
   - La respuesta guarda automáticamente el JWT en la variable de colección `token`.
   - El resto de peticiones usan `Authorization: Bearer {{token}}` de forma automática.

   > Otra opción: pega manualmente un JWT en la variable `token` si prefieres usar un token de tu IdP.

4. **Ejecuta el flujo de vida** (carpeta *1. Ciclo de vida*, en orden):
   1. **Crear ticket** → devuelve `201` y guarda `ticket_id`/`ticket_number` automáticamente.
   2. **Asignar ticket** (rol `SOPORTE_TI`) → estado `ASSIGNED`.
   3. **Iniciar investigación** → estado `IN_PROGRESS`.
   4. **Resolver ticket** → estado `RESOLVED`.
   5. **Cerrar ticket (con CSAT)** → estado `CLOSED`.

5. **Otras peticiones**:
   - *2. Cancelar ticket*: cancela un ticket (estado `CANCELLED`).
   - *3. Consultas (GET)*: obtener por ID/número, listar todos, por estado y por solicitante.

### Roles y permisos (importante para probar RBAC)

| Operación | Rol requerido |
|---|---|
| Crear / Cerrar | `CLIENTE`, `ADMIN` |
| Asignar / Investigar / Resolver / Cancelar | `SOPORTE_TI`, `ADMIN` |
| Consultas | cualquier rol autenticado |

Para probar el RBAC, cambia el `email` del cuerpo de **Login** (cliente/soporte/admin, password `dylan`)
y vuelve a ejecutar la petición para re-generar el token con otro rol. Si una petición se ejecuta con un
token cuyo rol no corresponde, la API devuelve `403 Forbidden` (o `401` si el token falta).

> **Nota:** el `token` se envía en el header `Authorization: Bearer {{token}}` definido a nivel de colección.
> El endpoint de login y las credenciales demo (`dylan`) solo existen en el perfil `dev`.

---

## 13. Estructura del Repositorio

```
helpdesk-service/
├── .github/workflows/ci.yml                   # Pipeline de integración continua
├── .gitignore
├── .dockerignore
├── docker-compose.yaml                        # Infraestructura local (MongoDB, Redis, Kafka, Kafka UI)
├── pom.xml                                    # Build Maven (Quarkus BOM, JaCoCo, SonarQube, MapStruct)
├── mvnw / mvnw.cmd                            # Maven wrapper
├── PROJECT_CONTEXT.md                         # Contexto de negocio y requerimientos
├── README.md                                  # Este documento
├── postman/
│   └── SoftTech_HelpDesk_Service.postman_collection.json   # Colección Postman
└── src/
    ├── main/
    │   ├── docker/                            # Dockerfiles (JVM, legacy-jar, native, native-micro)
    │   ├── resources/
    │   │   ├── application.properties         # Configuración (HTTP, Mongo, Redis, Kafka, JWT, OpenAPI, logs)
    │   │   ├── publicKey.pem                  # Clave pública RSA para verificar JWTs
    │   │   └── privateKey.pem                 # Clave privada RSA (SOLO DESARROLLO, firma del login dev)
    │   └── java/org/softtech/
    │       ├── domain/                        # ───── DOMINIO PURO (sin dependencias de framework) ─────
    │       │   ├── model/
    │       │   │   ├── Ticket.java            #   Agregado raíz inmutable + factory Ticket.created()
    │       │   │   ├── TicketStatus.java      #   Máquina de estados finitos (enum)
    │       │   │   ├── SlaPolicy.java         #   Value Object de cálculo de SLA y deadlines
    │       │   │   ├── Feedback.java          #   Value Object CSAT (rating 1-5, comentario)
    │       │   │   ├── Priority.java          #   Enum de prioridad (LOW..CRITICAL) con SLA base
    │       │   │   └── ErpModule.java         #   Enum de módulo ERP con criticidad y escalado
    │       │   ├── event/
    │       │   │   ├── TicketCreatedEvent.java        #   Evento de creación (record)
    │       │   │   ├── TicketStatusChangedEvent.java  #   Evento de cambio de estado (record)
    │       │   │   └── TicketClosedEvent.java         #   Evento de cierre con SLA/CSAT (record)
    │       │   ├── exception/
    │       │   │   ├── DomainException.java                   #   Base de excepciones de dominio
    │       │   │   ├── TicketNotFoundException.java           #   HD-DOM-4040
    │       │   │   └── InvalidStatusTransitionException.java  #   HD-DOM-4001
    │       │   └── port/
    │       │       ├── in/                    #   Puertos de entrada (casos de uso + Command records)
    │       │       │   ├── CreateTicketUseCase.java
    │       │       │   ├── GetTicketUseCase.java
    │       │       │   ├── AssignTicketUseCase.java
    │       │       │   ├── StartInvestigationUseCase.java
    │       │       │   ├── ResolveTicketUseCase.java
    │       │       │   ├── CloseTicketUseCase.java
    │       │       │   └── CancelTicketUseCase.java
    │       │       └── out/                   #   Puertos de salida (contratos de infraestructura)
    │       │           ├── TicketPersistencePort.java
    │       │           ├── TicketCachePort.java
    │       │           └── TicketEventPublisherPort.java
    │       ├── application/
    │       │   └── usecase/                   # ───── CASOS DE USO (orquestación reactiva) ─────
    │       │       ├── CreateTicketUseCaseImpl.java
    │       │       ├── GetTicketUseCaseImpl.java
    │       │       ├── AssignTicketUseCaseImpl.java
    │       │       ├── StartInvestigationUseCaseImpl.java
    │       │       ├── ResolveTicketUseCaseImpl.java
    │       │       ├── CloseTicketUseCaseImpl.java
    │       │       └── CancelTicketUseCaseImpl.java
    │       └── infrastructure/                # ───── INFRAESTRUCTURA (adaptadores) ─────
    │           ├── config/
    │           │   └── OpenApiConfig.java     #   Configuración OpenAPI/Swagger + esquema JWT
    │           ├── entrypoints/rest/
    │           │   ├── TicketResource.java    #   Controlador REST /api/v1/tickets (+ @RolesAllowed)
    │           │   ├── AuthResource.java      #   Login dev (/api/v1/auth/login, solo perfil dev)
    │           │   ├── Roles.java             #   Constantes de roles RBAC
    │           │   ├── GlobalExceptionHandler.java  #  Mapeo de excepciones a RFC 7807
    │           │   ├── mapper/
    │           │   │   └── TicketRestMapper.java    #  MapStruct: dominio -> TicketResponseDto
    │           │   └── dto/                   #   DTOs de entrada/salida (records + validación)
    │           │       ├── TicketRequestDto.java
    │           │       ├── TicketResponseDto.java
    │           │       ├── AssignTicketRequestDto.java
    │           │       ├── ResolveTicketRequestDto.java
    │           │       ├── CloseTicketRequestDto.java
    │           │       ├── CancelTicketRequestDto.java
    │           │       ├── LoginRequestDto.java
    │           │       ├── LoginResponseDto.java
    │           │       └── ErrorResponseDto.java
    │           ├── persistence/
    │           │   ├── adapter/
    │           │   │   └── TicketMongoAdapter.java      #  Implementa TicketPersistencePort (Mongo)
    │           │   ├── document/
    │           │   │   └── TicketDocument.java          #  Documento BSON + sub-documentos SLA/Feedback
    │           │   ├── mapper/
    │           │   │   └── TicketPersistenceMapper.java #  Dominio <-> Documento
    │           │   └── repository/
    │           │       └── ReactiveTicketPanacheRepository.java  # Acceso a datos Panache reactivo
    │           ├── cache/adapter/
    │           │   └── TicketRedisAdapter.java          #  Implementa TicketCachePort (Redis)
    │           └── messaging/
    │               └── KafkaTicketEventPublisher.java   #  Implementa TicketEventPublisherPort (Kafka)
    └── test/java/org/softtech/                # ───── PRUEBAS ─────
        ├── domain/                            #   Tests de dominio (FSM, SLA, Feedback, eventos, excepciones)
        │   ├── model/
        │   │   ├── TicketTest.java
        │   │   ├── TicketStatusTest.java
        │   │   ├── SlaPolicyTest.java
        │   │   ├── FeedbackTest.java
        │   │   ├── PriorityTest.java
        │   │   └── ErpModuleTest.java
        │   ├── event/
        │   │   ├── TicketCreatedEventTest.java
        │   │   ├── TicketStatusChangedEventTest.java
        │   │   └── TicketClosedEventTest.java
        │   └── exception/
        │       ├── TicketNotFoundExceptionTest.java
        │       └── InvalidStatusTransitionExceptionTest.java
        ├── application/usecase/               #   Tests de casos de uso (mocks + UniAssertSubscriber)
        │   ├── CreateTicketUseCaseTest.java
        │   ├── GetTicketUseCaseTest.java
        │   ├── AssignTicketUseCaseTest.java
        │   ├── StartInvestigationUseCaseTest.java
        │   ├── ResolveTicketUseCaseTest.java
        │   ├── CloseTicketUseCaseTest.java
        │   └── CancelTicketUseCaseTest.java
        └── infrastructure/                    #   Tests de adaptadores, mappers e integración REST
            ├── cache/adapter/TicketRedisAdapterTest.java
            ├── entrypoints/rest/
            │   ├── TicketResourceTest.java            #  @QuarkusTest (hermético, E2E)
            │   ├── GlobalExceptionHandlerTest.java
            │   └── mapper/TicketRestMapperTest.java
            ├── messaging/KafkaTicketEventPublisherTest.java
            └── persistence/
                ├── adapter/TicketMongoAdapterTest.java
                ├── mapper/TicketPersistenceMapperTest.java
                └── repository/ReactiveTicketPanacheRepositoryTest.java
```
