# SoftTech HelpDesk Service

Microservicio **reactivo y desacoplado** de gestión de incidencias (HelpDesk) para el ERP de **SoftTech Solutions**.
Implementa el ciclo de vida completo de un ticket de soporte (creación, asignación, investigación, resolución,
cierre con CSAT y cancelación), con cálculo dinámico de SLA, caché distribuida, mensajería por eventos y
seguridad RBAC, alineado a **ISO/IEC 25010** (calidad) y **CMMI Nivel 2/3** (madurez de procesos).

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
| Documentación | SmallRye OpenAPI + Swagger UI |
| Observabilidad | Micrometer/Prometheus, SmallRye Health, Lombok `@Slf4j` + MDC |
| Pruebas | JUnit 5, Mockito, RestAssured, `@QuarkusTest` |
| QA | JaCoCo (cobertura) + SonarQube (análisis estático) |
| Build | Maven (wrapper `./mvnw`) |

---

## 2. Arquitectura

Arquitectura hexagonal estricta con límites claros entre dominio puro, casos de uso y adaptadores de infraestructura:

```
src/main/java/org/softtech/
├── domain/                         # Dominio puro (sin dependencias de framework)
│   ├── model/                      # Ticket (agregado), TicketStatus (FSM), SlaPolicy, Feedback, Priority, ErpModule
│   ├── event/                      # TicketCreatedEvent, TicketStatusChangedEvent, TicketClosedEvent
│   ├── exception/                  # DomainException, TicketNotFoundException, InvalidStatusTransitionException
│   └── port/
│       ├── in/                     # Puertos de entrada (casos de uso): Create, Get, Assign, StartInvestigation, Resolve, Close, Cancel
│       └── out/                    # Puertos de salida: TicketPersistencePort, TicketCachePort, TicketEventPublisherPort
├── application/usecase/            # Orquestación reactiva (CreateTicketUseCaseImpl, etc.)
└── infrastructure/
    ├── entrypoints/rest/           # TicketResource, DTOs, TicketRestMapper (MapStruct), GlobalExceptionHandler
    ├── persistence/                # TicketMongoAdapter, TicketDocument, TicketPersistenceMapper, ReactiveTicketPanacheRepository
    ├── cache/adapter/              # TicketRedisAdapter
    └── messaging/                  # KafkaTicketEventPublisher
```

**Patrones aplicados:** *Strangler Fig*, *CQRS* (escrituras → MongoDB + eventos; lecturas → Redis), *SAGA*
(coreografía Kafka), *Cache-Aside*, *Circuit Breaker/Fallback* (SmallRye Fault Tolerance).

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

### 3.3 Feedback CSAT (`Feedback`)

Calificación de 1 a 5 estrellas con comentario opcional (máx. 500 caracteres). Clasificación:
`isSatisfactory()` (≥4), `isNeutral()` (=3), `isDetractor()` (≤2).

---

## 4. API REST

Base URL: `/api/v1/tickets`. Autenticación **JWT Bearer**. Roles: `CLIENTE`, `SOPORTE_TI`, `ADMIN`.

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

### Manejo de errores (RFC 7807 / RFC 9457)

Todos los errores devuelven `ErrorResponseDto` con `type`, `title`, `status`, `detail`, `errorCode`,
`correlationId` y `timestamp`.

| Código HTTP | errorCode | Escenario |
|---|---|---|
| `400` | `ERR_HD_VALIDATION_FAILED` | Violación de Bean Validation (con mapa `violations`) |
| `400` | `ERR_HD_ILLEGAL_ARGUMENT` | Argumento inválido |
| `404` | `ERR_HD_TICKET_NOT_FOUND` | Ticket inexistente |
| `409` | `ERR_HD_INVALID_STATUS_TRANSITION` | Transición de estado ilegal |
| `422` | `ERR_HD_DOMAIN_RULE_VIOLATION` | Invariante de dominio |
| `500` | `ERR_HD_INTERNAL_SERVER_ERROR` | Fallo interno |

---

## 5. Configuración

La configuración vive en `src/main/resources/application.properties` y es sobreescribible por variables de entorno:

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `MONGODB_URI` | `mongodb://helpdesk_admin:helpdesk_secret_password@localhost:27018/helpdesk_db?authSource=admin` | Conexión MongoDB |
| `REDIS_URI` | `redis://:redis_secret_password@localhost:6380` | Conexión Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9095` | Bootstrap Kafka |
| `KAFKA_TICKET_EVENTS_TOPIC` | `helpdesk.ticket-events.v1` | Topic de eventos |
| `JWT_PUBLIC_KEY_LOCATION` | `/publicKey.pem` | Clave pública JWT |
| `JWT_ISSUER` | `https://auth.softtech.com/oauth2/token` | Emisor JWT |

---

## 6. Infraestructura local (Docker Compose)

```bash
docker compose up -d
```

| Servicio | Puerto host |
|---|---|
| MongoDB | `27018` |
| Redis | `6380` |
| Kafka (KRaft, externo) | `9095` |
| Kafka UI | `8088` |

Los puertos se re-mapean para no colisionar con otros proyectos. Kafka UI permite inspeccionar el topic
`helpdesk.ticket-events.v1` en `http://localhost:8088`.

---

## 7. Ejecución y Build

```bash
# Modo desarrollo (hot reload + Dev UI + Swagger)
./mvnw quarkus:dev

# Swagger UI / OpenAPI
#   http://localhost:8080/q/swagger-ui
#   http://localhost:8080/q/openapi

# Empaquetar
./mvnw package

# Ejecutar el jar
java -jar target/quarkus-app/quarkus-run.jar

# Ejecutar en modo nativo (requiere GraalVM)
./mvnw package -Dnative
```

---

## 8. Pruebas

**Pirámide de pruebas** implementada:

| Nivel | Ubicación | Enfoque |
|---|---|---|
| Dominio puro | `domain/model`, `domain/event`, `domain/exception` | FSM, SLA, Feedback, inmutabilidad, eventos, excepciones |
| Casos de uso | `application/usecase` | Orquestación reactiva, fallbacks, degradación elegante (Mockito + `UniAssertSubscriber`) |
| Mappers/Adaptadores | `infrastructure/*` | Mapeo dominio↔documento/DTO, Redis, Kafka, Mongo |
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

## 9. Calidad de Código (QA)

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

## 10. CI/CD

Workflow de GitHub Actions en `.github/workflows/ci.yml`: en cada `push`/`pull_request` a `main` ejecuta
`./mvnw verify -B` (que incluye compilación, pruebas unitarias + integración y verificación de cobertura JaCoCo).

---

## 11. Estructura del repositorio

```
helpdesk-service/
├── docker-compose.yaml                    # Infraestructura local (MongoDB, Redis, Kafka, Kafka UI)
├── pom.xml                                # Build Maven (Quarkus BOM 3.38.3, JaCoCo, SonarQube)
├── .github/workflows/ci.yml               # CI/CD
├── PROJECT_CONTEXT.md                     # Contexto de negocio y requerimientos
└── src/
    ├── main/java/org/softtech/            # Código fuente (Arquitectura Hexagonal)
    ├── main/resources/application.properties
    └── test/java/org/softtech/            # Pruebas unitarias e integración
```
