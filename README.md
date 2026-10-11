# Sistema de Préstamo de Libros — Microservicios con Quarkus

Proyecto multi-módulo Maven compuesto por **tres microservicios reactivos** construidos con **Quarkus 3.39.5** y **Java 25**, que colaboran para gestionar un catálogo de libros, registrar órdenes de préstamo y auditar cada operación de forma asíncrona mediante **Apache Kafka**.

| Microservicio | Puerto | Responsabilidad | Persistencia | Comunicación |
|---|---|---|---|---|
| `ms-book-availability-checker` | `8082` | Catálogo de libros y control de stock | PostgreSQL (`books_db`) | Expone API REST |
| `ms-book-loan` | `8083` | Creación y consulta de órdenes de préstamo | MySQL (`loans_db`) | Consume REST + produce eventos Kafka |
| `ms-auditor` | — (sin API REST) | Registro de auditoría | MongoDB (`audit_log_db`) | Consume eventos Kafka |

---

## Índice

1. [Arquitectura global](#1-arquitectura-global)
2. [Flujo global entre microservicios](#2-flujo-global-entre-microservicios)
3. [ms-book-availability-checker](#3-ms-book-availability-checker)
4. [ms-book-loan](#4-ms-book-loan)
5. [ms-auditor](#5-ms-auditor)
6. [Infraestructura (docker-compose)](#6-infraestructura-docker-compose)
7. [Cómo ejecutar](#7-cómo-ejecutar)
8. [Consideraciones conocidas](#8-consideraciones-conocidas)

---

## 1. Arquitectura global

```mermaid
flowchart LR
    Client([Cliente HTTP])

    subgraph Loan["ms-book-loan :8083"]
        OR[OrderResource]
        OS[OrderService]
        BRC[BookRestClient<br/>Timeout · CircuitBreaker · Fallback]
        AEP[AuditEventProducer]
    end

    subgraph Books["ms-book-availability-checker :8082"]
        BR[BookResource]
        BS[BookService]
    end

    subgraph Auditor["ms-auditor"]
        ALC[AuditLogConsumer]
    end

    MySQL[(MySQL<br/>loans_db)]
    PG[(PostgreSQL<br/>books_db)]
    Mongo[(MongoDB<br/>audit_log_db)]
    Kafka{{Kafka<br/>topic: audit-topic}}

    Client -- "/api/v1/orders" --> OR
    Client -- "/api/v1/books" --> BR
    OR --> OS
    OS --> MySQL
    OS --> BRC
    BRC -- "GET /books/{id}<br/>PATCH /books/{id}/stock" --> BR
    BR --> BS --> PG
    OS --> AEP
    AEP -- "AuditEvent (JSON)" --> Kafka
    Kafka --> ALC
    ALC --> Mongo
```

**Estilo de comunicación**

- **Síncrona (REST):** `ms-book-loan` → `ms-book-availability-checker`, usando MicroProfile Rest Client reactivo (`Uni<T>`).
- **Asíncrona (eventos):** `ms-book-loan` → Kafka → `ms-auditor`, usando SmallRye Reactive Messaging.
- **Base de datos por servicio:** cada microservicio es dueño exclusivo de su almacenamiento (PostgreSQL, MySQL y MongoDB).

---

## 2. Flujo global entre microservicios

El caso de uso principal es **crear una orden de préstamo**. Involucra a los tres microservicios:

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant L as ms-book-loan
    participant DBL as MySQL (loans_db)
    participant B as ms-book-availability-checker
    participant DBB as PostgreSQL (books_db)
    participant K as Kafka (audit-topic)
    participant A as ms-auditor
    participant M as MongoDB (audit_log_db)

    C->>L: POST /api/v1/orders {customerName, items[]}
    Note over L: Validación Bean Validation (400 si falla)

    par Para cada item (en paralelo)
        L->>B: GET /api/v1/books/{bookId}
        B->>DBB: SELECT book
        DBB-->>B: book
        B-->>L: {id, name, price, stock}
    end

    Note over L: Valida stock disponible (409 si es insuficiente)<br/>Calcula subtotales y totalAmount

    L->>DBL: INSERT orders + order_items (transacción)

    par Para cada item (en paralelo)
        L->>B: PATCH /api/v1/books/{bookId}/stock {quantity}
        B->>DBB: UPDATE stock = stock - q WHERE stock >= q
        B-->>L: libro actualizado
    end

    L->>K: AuditEvent {action: CREATE, resource: ORDER, request, response}
    K-->>A: AuditEvent
    A->>M: insert audit_logs (+timestamp)

    L-->>C: 201 Created {orderId, message}
```

> La publicación en Kafka desacopla la auditoría: `ms-auditor` procesa el evento de forma independiente y su disponibilidad no bloquea la respuesta al cliente más allá del `ack` del broker (`acks=1`).

---

## 3. ms-book-availability-checker

Servicio dueño del **catálogo de libros** y de su **inventario**. Expone un CRUD completo más una operación atómica para descontar stock.

**Stack:** Quarkus REST + Jackson · Hibernate Reactive Panache · Reactive PG Client · Hibernate Validator · MapStruct · Lombok · SmallRye OpenAPI · SmallRye Health.

### 3.1 Modelo de datos

```mermaid
erDiagram
    BOOKS {
        UUID id PK
        string name "NOT NULL, único a nivel de negocio"
        decimal price "NOT NULL"
        int stock
    }
```

Al iniciar, `import.sql` carga 10 libros de ejemplo (*Clean Code*, *Effective Java*, *Domain-Driven Design*, etc.). El esquema se recrea en cada arranque (`drop-and-create`).

### 3.2 Endpoints — `/api/v1/books`

| Método | Ruta | Descripción | Respuesta OK | Errores |
|---|---|---|---|---|
| `GET` | `/` | Lista paginada (`page`=0, `size`=10, `sort`=`name`) | `200` `PaginatedResponseDto<BookDto>` | — |
| `GET` | `/{id}` | Obtiene un libro por UUID | `200` `BookDto` | `404` |
| `POST` | `/` | Crea un libro | `201` (sin cuerpo) | `400` validación, `409` nombre duplicado |
| `PUT` | `/{id}` | Actualiza nombre, precio y stock | `200` | `400`, `404` |
| `PATCH` | `/{id}/stock` | Descuenta stock `{ "quantity": n }` | `200` `BookDto` | `400`, `404`, `409` stock insuficiente |
| `DELETE` | `/{id}` | Elimina un libro | `204` | `404` |

**Validaciones (`BookRequestDto`)**: `name` obligatorio (2–60 caracteres), `price` obligatorio, `stock` ≥ 1. En `StockDecreaseRequestDto`, `quantity` obligatorio y > 0.

### 3.3 Flujo: crear libro

```mermaid
flowchart TD
    A[POST /api/v1/books] --> B{¿Body válido?}
    B -- No --> E400[400 Bad Request<br/>lista de errores por campo]
    B -- Sí --> C[findByName - name.trim]
    C --> D{¿Existe un libro<br/>con ese nombre?}
    D -- Sí --> E409[409 Conflict<br/>Book with the same name already exists]
    D -- No --> F[persist BookEntity<br/>@WithTransaction]
    F --> G[201 Created]
```

### 3.4 Flujo: descontar stock (operación atómica)

El descuento se realiza con **un único `UPDATE` condicional**, lo que evita condiciones de carrera entre préstamos concurrentes:

```sql
UPDATE books SET stock = stock - :q WHERE id = :id AND stock >= :q
```

```mermaid
flowchart TD
    A["PATCH /api/v1/books/{id}/stock<br/>{quantity}"] --> B[UPDATE condicional]
    B --> C{¿Filas<br/>actualizadas > 0?}
    C -- Sí --> D[findById] --> OK[200 OK - BookDto actualizado]
    C -- No --> E[findById]
    E --> F{¿Libro existe?}
    F -- No --> E404[404 Book not found]
    F -- Sí --> E409[409 Insufficient stock]
```

### 3.5 Flujo: consulta paginada

```mermaid
flowchart LR
    A["GET /api/v1/books?page=0&size=10&sort=name"] --> B[Page.of + Sort.by]
    B --> C1[query.list]
    B --> C2[query.pageCount]
    B --> C3[query.count]
    C1 & C2 & C3 --> D["Uni.combine().all()"]
    D --> E[PaginatedResponseDto<br/>content · pageIndex · pageSize<br/>totalPages · totalElements]
```

**Ejemplo de respuesta**

```json
{
  "content": [
    { "id": "a3f60d90-e52b-4a97-a6dd-81ff7e5b4101", "name": "Clean Code", "price": 45.90, "stock": 12 }
  ],
  "pageIndex": 0,
  "pageSize": 10,
  "totalPages": 1,
  "totalElements": 10
}
```

**Formato de error**

```json
{ "status": 409, "message": "Insufficient stock" }
```

---

## 4. ms-book-loan

Servicio **orquestador** de las órdenes de préstamo. Consulta libros y descuenta stock en `ms-book-availability-checker`, persiste la orden en MySQL y publica un evento de auditoría en Kafka.

**Stack:** Quarkus REST + Jackson · Hibernate Reactive Panache · Reactive MySQL Client · REST Client · SmallRye Fault Tolerance · Messaging Kafka · Micrometer/Prometheus · Hibernate Validator · MapStruct · Lombok · OpenAPI · Health.

### 4.1 Modelo de datos (maestro–detalle)

```mermaid
erDiagram
    ORDERS ||--|{ ORDER_ITEMS : contiene
    ORDERS {
        UUID id PK
        string customerName "NOT NULL"
        decimal totalAmount "NOT NULL, suma de subtotales"
    }
    ORDER_ITEMS {
        UUID id PK
        UUID order_id FK
        string bookId "referencia lógica a books.id"
        int quantity
        double price "precio del libro al momento del préstamo"
    }
```

`bookId` es una **referencia lógica** (no hay FK entre bases de datos); el precio se copia al ítem para conservar el valor histórico.

### 4.2 Endpoints — `/api/v1/orders`

| Método | Ruta | Descripción | Respuesta OK | Tolerancia a fallos |
|---|---|---|---|---|
| `GET` | `/` | Lista paginada (`page`=0, `size`=10, `sort`=`id`) | `200` `PaginatedResponseDto<OrderResponse>` | — |
| `GET` | `/{id}` | Obtiene una orden con sus ítems y nombres de libro | `200` `OrderResponse` | `@Timeout(10s)`, `@Retry(2)`, `@CircuitBreaker` |
| `POST` | `/` | Crea una orden de préstamo | `201` `CreatedOrderResponse` | `@Timeout(10s)`, `@CircuitBreaker` |

**Ejemplo de petición**

```http
POST http://localhost:8083/api/v1/orders
Content-Type: application/json

{
  "customerName": "Ana Pérez",
  "items": [
    { "bookId": "a3f60d90-e52b-4a97-a6dd-81ff7e5b4101", "quantity": 2 },
    { "bookId": "bc8b723e-bcef-4eb8-90af-6d299b49e022", "quantity": 1 }
  ]
}
```

**Respuesta `201 Created`**

```json
{ "orderId": "5b0e…", "message": "Order created successfully" }
```

**Validaciones (`OrderRequest`)**: `customerName` obligatorio, `items` no vacío; cada ítem requiere `bookId` y `quantity` > 0.

### 4.3 Flujo: crear orden (pipeline reactivo)

`OrderService.save()` encadena los pasos dentro de una transacción (`@WithTransaction`):

```mermaid
flowchart TD
    A[POST /api/v1/orders] --> V{¿Body válido?}
    V -- No --> E400[400 Bad Request]
    V -- Sí --> F["fetchBooks<br/>GET /books/{id} por ítem, en paralelo<br/>Uni.join().all()"]
    F --> NF{¿Algún libro<br/>no existe?}
    NF -- Sí --> E404[404 Book not found with ID]
    NF -- No --> S{"¿stock ≥ quantity<br/>en cada ítem?"}
    S -- No --> E409[409 Insufficient stock<br/>for the requested book]
    S -- Sí --> C[createOrder<br/>agrega ítems · calcula totalAmount]
    C --> P[persist OrderEntity + OrderItemEntity<br/>MySQL]
    P --> D["decreaseStock<br/>PATCH /books/{id}/stock por ítem, en paralelo"]
    D --> K["publishOrderCreatedEvent<br/>Kafka: audit-topic"]
    K --> R[201 Created<br/>orderId · message]
```

### 4.4 Flujo: consultar orden por ID

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant L as ms-book-loan
    participant DB as MySQL
    participant B as ms-book-availability-checker

    C->>L: GET /api/v1/orders/{id}
    L->>DB: findById(id)
    alt Orden no existe
        L-->>C: 404 Order not found
    else Orden existe
        L->>DB: Mutiny.fetch(order.items) (carga LAZY)
        par Por cada ítem
            L->>B: GET /api/v1/books/{bookId}
            B-->>L: {name, ...}
        end
        Note over L: Mapea a OrderResponse<br/>con bookName en cada ítem
        L-->>C: 200 OrderResponse
    end
```

### 4.5 Resiliencia en la llamada a `ms-book-availability-checker`

`BookRestClient.findById` está protegido con **Timeout (2 s)**, **Circuit Breaker** (4 peticiones, 50 % de fallos, 10 s abierto) y **Fallback**:

```mermaid
stateDiagram-v2
    [*] --> Cerrado
    Cerrado --> Abierto: ≥50% fallos en ventana de 4 llamadas
    Abierto --> SemiAbierto: tras 10 s
    SemiAbierto --> Cerrado: llamada de prueba OK
    SemiAbierto --> Abierto: llamada de prueba falla

    note right of Abierto
        Las llamadas no llegan al servicio:
        se responde con el fallback
        "Libro No Disponible (Fallback)", price = 0
    end note
```

A nivel de endpoint, `OrderResource` añade su propio `@CircuitBreaker` (10 peticiones, 50 %) y, en `GET /{id}`, `@Retry` (2 reintentos, 200 ms ± 100 ms). Ambos **ignoran las excepciones de negocio** (`CustomBusinessException`: 404/409) para no reintentar ni abrir el circuito por errores del cliente.

### 4.6 Evento de auditoría publicado

```json
{
  "action": "CREATE",
  "resource": "ORDER",
  "request": "{\"customerName\":\"Ana Pérez\",\"items\":[...]}",
  "response": "{\"orderId\":\"5b0e…\",\"customerName\":\"Ana Pérez\",\"totalAmount\":146.30}"
}
```

Serializado con `ObjectMapperSerializer` en el canal `audit-topic` (`acks=1`).

### 4.7 Observabilidad

- **Métricas Micrometer** (exportadas a Prometheus en `/q/metrics`):
  `orders.get.all.timer|count`, `orders.get.byid.timer|count`, `orders.save.timer|count`.
- **Health checks:** `/q/health/live` (también en `ms-book-availability-checker`).
- **OpenAPI / Swagger UI:** `/q/openapi` y `/q/swagger-ui` (en modo dev).

### 4.8 Configuración relevante

| Propiedad | Valor por defecto |
|---|---|
| `quarkus.datasource.reactive.url` | `mysql://localhost:3307/loans_db` (`DB_HOST`, `DB_PORT`) |
| Datasource `loans-backup` | `mysql://localhost:3305/loans_db_bk` (`DB_BACKUP_PORT`) |
| Perfil `%qa` | `LOANS_DB_QA_URL`, `LOANS_DB_QA_USERNAME`, `LOANS_DB_QA_PASSWORD`, `LOANS_DB_BK_QA_URL` |
| `quarkus.rest-client.book-availability-checker-api.url` | `http://localhost:8082` |
| `kafka.bootstrap.servers` | `localhost:29092` (`KAFKA_BOOTSTRAP_SERVERS`) |

---

## 5. ms-auditor

Servicio **consumidor de eventos**. No expone endpoints REST: escucha el tópico `audit-topic` y guarda cada evento en MongoDB, añadiendo la marca de tiempo de recepción.

**Stack:** Quarkus Messaging Kafka · MongoDB Panache (reactivo) · Jackson.

### 5.1 Modelo de datos

```mermaid
erDiagram
    AUDIT_LOGS {
        ObjectId id PK "_id"
        string action "CREATE, UPDATE, DELETE"
        string resource "ej. ORDER"
        string request "JSON serializado"
        string response "JSON serializado"
        datetime timestamp "hora de recepción"
    }
```

### 5.2 Flujo: consumo y persistencia

```mermaid
flowchart LR
    K{{"Kafka<br/>audit-topic"}} -- "bytes JSON" --> D[AuditEventDeserializer<br/>ObjectMapperDeserializer]
    D -- AuditEvent --> C["AuditLogConsumer<br/>@Incoming(audit-topic)"]
    C --> E[new AuditLogEntity<br/>+ timestamp = now]
    E --> P["audit.persist()<br/>reactivo"]
    P --> M[(MongoDB<br/>audit_log_db.audit_logs)]
    P -. log .-> L["--> [MongoDB] Evento guardado: id"]
```

- `auto.offset.reset=earliest`: al iniciar por primera vez procesa todos los eventos pendientes del tópico.
- El método devuelve `Uni<Void>`; el offset se confirma cuando la escritura en MongoDB termina.

**Documento resultante**

```json
{
  "_id": { "$oid": "6650…" },
  "action": "CREATE",
  "resource": "ORDER",
  "request": "{\"customerName\":\"Ana Pérez\",\"items\":[...]}",
  "response": "{\"orderId\":\"5b0e…\",\"customerName\":\"Ana Pérez\",\"totalAmount\":146.30}",
  "timestamp": "2026-10-03T10:15:42.123"
}
```

---

## 6. Infraestructura (docker-compose)

```mermaid
flowchart TB
    subgraph net["red: library-network"]
        ZK[zookeeper<br/>:2181]
        KF[kafka<br/>:29092 externo · :9092 interno]
        KUI[kafka-ui<br/>:8081]
        PG[(pgsql_books_bd<br/>postgres:16 · :5432)]
        MY[(mysql-loans<br/>mysql:8.0 · :3307)]
        MYB[(mysql-loans-backup<br/>:3305)]
        MYQ[(mysql-loans-qa<br/>:3309)]
        MG[(mongodb-auditor<br/>mongo:7.0 · :27018)]
    end
    ZK --> KF --> KUI
```

| Servicio | Imagen | Puerto host | Usado por |
|---|---|---|---|
| `zookeeper` | `confluentinc/cp-zookeeper:7.0.1` | 2181 | Kafka |
| `kafka` | `confluentinc/cp-kafka:7.0.1` | 29092 | `ms-book-loan`, `ms-auditor` |
| `kafka-ui` | `provectuslabs/kafka-ui` | 8081 | Inspección de tópicos |
| `pgsql_books_bd` | `postgres:16` | 5432 | `ms-book-availability-checker` |
| `mysql-loans` | `mysql:8.0` | 3307 | `ms-book-loan` |
| `mysql-loans-backup` | `mysql:8.0` | 3305 | `ms-book-loan` (datasource `loans-backup`) |
| `mysql-loans-qa` | `mysql:8.0` | 3309 | `ms-book-loan` (perfil `qa`) |
| `mongodb-auditor` | `mongo:7.0` | 27018 | `ms-auditor` |

---

## 7. Cómo ejecutar

**Requisitos:** JDK 25, Docker y Docker Compose (Maven Wrapper incluido en cada módulo).

```bash
# 1. Levantar la infraestructura
docker compose up -d

# 2. Arrancar cada microservicio en modo dev (en terminales separadas)
cd ms-book-availability-checker && ./mvnw quarkus:dev   # :8082
cd ms-book-loan                 && ./mvnw quarkus:dev   # :8083
cd ms-auditor                   && ./mvnw quarkus:dev   # consumidor Kafka
```

> Todos los servicios intentan usar el puerto de depuración `5005` en modo dev; si choca, arranca los demás con `-Ddebug=<otro-puerto>` o `-Ddebug=false`.

**Prueba rápida del flujo completo**

```bash
# Crear una orden con libros precargados
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Ana Pérez","items":[{"bookId":"a3f60d90-e52b-4a97-a6dd-81ff7e5b4101","quantity":2}]}'

# Verificar el stock descontado (12 → 10)
curl http://localhost:8082/api/v1/books/a3f60d90-e52b-4a97-a6dd-81ff7e5b4101

# Ver el evento en Kafka UI → http://localhost:8081  (tópico audit-topic)
# Ver el registro en MongoDB
docker exec -it mongodb-auditor mongosh audit_log_db --eval "db.audit_logs.find().pretty()"
```

---

## 8. Consideraciones conocidas

Comportamientos del código actual que conviene tener presentes:

- **Sin compensación (Saga) en el descuento de stock.** El stock se descuenta en `ms-book-availability-checker` después de persistir la orden. Si un `PATCH` falla (o falla el envío a Kafka), la transacción de MySQL se revierte, pero los descuentos de stock que ya se aplicaron en otros ítems **no se restauran**.
- **El fallback enmascara caídas como "stock insuficiente".** Cuando `ms-book-availability-checker` no responde, el fallback devuelve un libro con `stock = null`, por lo que la creación de la orden responde `409 Insufficient stock` en lugar de un error de disponibilidad (`503`).
- **Llamadas N+1 al consultar órdenes.** `GET /api/v1/orders` consulta el nombre de cada libro de cada ítem vía REST.
- **Datasource `loans-backup` configurado pero sin uso** en el código actual.
- **Esquemas recreados en cada arranque** (`drop-and-create`) en PostgreSQL y MySQL: los datos no persisten entre reinicios de los servicios.
