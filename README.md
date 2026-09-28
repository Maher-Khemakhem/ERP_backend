# Supermarket ERP backend

The `ERP` directory contains the Spring Boot REST API for the single-store supermarket ERP. It is a modular monolith: business capabilities are isolated as Spring Modulith modules, while PostgreSQL stores the application data and event-publication outbox.

The API owns authentication, inventory, pricing, point of sale, procurement, and finance. Domain events connect those areas without allowing modules to reach into one another's repositories or entities.

## Technology

| Area | Technology |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 4.1 |
| Web/API | Spring MVC, Jakarta Validation |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL 14 or newer |
| Migrations | Flyway (V1 through V18, including V2.1) |
| Module boundaries/events | Spring Modulith 2.1, JDBC event publication |
| Security | Spring Security, HS256 JWT resource server, BCrypt passwords |
| API documentation | Springdoc OpenAPI / Swagger UI |

## Modules

All application code is under `src/main/java/com/example/erp/`.

| Module | Responsibility |
| --- | --- |
| `auth` | First-admin setup, login, JWTs, users, roles, enable/disable state, login rate limiting |
| `inventory` | Products, product families, unit types, VAT rates, product images, lots, FEFO stock, reorder points |
| `pricing` | Per-product prices and time-bounded percentage promotions |
| `pos` | Tills, cash sessions, transactions, payments, voids, and phone-scanner pairing |
| `procurement` | Suppliers, purchase orders, goods receiving, and low-stock alerts |
| `finance` | Double-entry journals, ledger views, P&L, supplier invoices/payments, and card settlement |
| `ecommerce` | Customer catalog, authentication, server-side carts, online orders, checkout, and mock payment confirmation |
| `shared` | Cross-module value objects and contracts such as `Money`, `Quantity`, and `DomainEvent` |

Cross-module interaction uses public services or events. The important event flows are:

```text
TransactionCompleted       -> finance records revenue, VAT, payment, and COGS
OnlineOrderConfirmed       -> finance records online revenue, VAT, payment, and COGS
GoodsReceived              -> finance records inventory and GRNI
CashSessionClosed          -> finance records cash deposits and over/short variance
LowStockThresholdReached   -> procurement creates a low-stock alert
```

Event publications are persisted by Spring Modulith. Finance listeners use event keys and payment references for idempotency, so a retried event does not create duplicate accounting entries.

## Local setup

### Prerequisites

- Java 21
- PostgreSQL 14+
- A PostgreSQL database user that can create and update the application schema

### Create a database

```sql
CREATE DATABASE supermarket_erp_saas;
```

### Configure the application

For local development, copy `.env.example` to `.env` in this directory and replace the example JWT secret. `application.yaml` imports that file automatically when the application is started from `ERP`.

```powershell
Copy-Item .env.example .env
# Edit .env and set JWT_SECRET to a private value of at least 32 bytes.
```

The database defaults are suitable only for a local PostgreSQL installation. The JWT secret has no default: the application refuses to start without `JWT_SECRET`.

### Run and test

```powershell
# Run the complete test suite
.\mvnw.cmd test

# Start the API on http://localhost:8080
.\mvnw.cmd spring-boot:run

# Build the executable JAR
.\mvnw.cmd package
```

Flyway runs the migrations during startup. Hibernate uses `ddl-auto: validate`; Flyway is the source of truth for schema changes.

## Configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/supermarket_erp_saas` | JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Local database password; replace outside local development |
| `JWT_SECRET` | required | HS256 secret, at least 32 bytes |
| `JWT_ISSUER` | `supermarket-erp` | JWT issuer claim |
| `JWT_AUDIENCE` | `supermarket-web` | JWT audience claim |
| `JWT_EXPIRATION_SECONDS` | `900` | Token lifetime; 15 minutes by default |
| `APP_UPLOAD_DIR` | `uploads/products` | Product image storage directory |
| `LOGIN_RATE_LIMIT_IP_CAPACITY` | `30` | Login attempts per IP window |
| `LOGIN_RATE_LIMIT_IP_WINDOW_SECONDS` | `60` | IP rate-limit window |
| `LOGIN_RATE_LIMIT_USERNAME_CAPACITY` | `10` | Login attempts per username window |
| `LOGIN_RATE_LIMIT_USERNAME_WINDOW_SECONDS` | `60` | Username rate-limit window |
| `LOGIN_RATE_LIMIT_MAX_BUCKETS` | `10000` | Maximum in-memory rate-limit buckets |

The login limiter is in-memory and per backend instance. A multi-instance deployment also needs a shared edge or distributed limiter.

Never commit real database credentials or JWT secrets. Caddy is intended to be the public HTTPS boundary; Spring Boot binds to `127.0.0.1:8080` by default.

## Security and roles

Authentication is stateless. Send the token returned by login in every protected request:

```http
Authorization: Bearer <jwt>
```

| Role | Backend permissions |
| --- | --- |
| `ADMIN` | All areas, including user management |
| `MANAGER` | Inventory, pricing, POS, procurement, finance |
| `CASHIER` | POS |
| `INVENTORY_MANAGER` | Inventory, pricing, procurement |
| `ACCOUNTANT` | Finance |

Public endpoints are limited to first-run setup status, first-admin registration, login, product image reads, and scanner-session routes. Selected read endpoints require any authenticated user; mutations are role-protected by module. The frontend hides unavailable screens for usability, but the backend remains the security boundary.

The first registered user becomes `ADMIN`. Once a user exists, registration is closed and only an `ADMIN` can create users or change roles and enabled state.

## API conventions

- Base URL: `http://localhost:8080`
- Business endpoints start with `/api`.
- IDs are UUIDs.
- Dates use `yyyy-MM-dd`; timestamps use ISO-8601.
- `EACH` quantities must be whole numbers. `WEIGHT` quantities are kilograms with three-decimal precision.
- Money is Tunisian dinar (TND) with three decimal places. Request bodies use numeric values such as `12.500`; responses serialize money as strings such as `"12.500 TND"` or the plain money representation used by the endpoint.
- A validation or business-rule failure normally returns `400`; a missing resource returns `404`; an invalid state or database conflict returns `409`.

Typical error response:

```json
{
  "timestamp": "2026-09-22T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable explanation"
}
```

## REST endpoint map

The generated OpenAPI document is the authoritative contract for exact schemas. The main routes are listed here for orientation.

### Authentication

| Method | Path | Access |
| --- | --- | --- |
| `GET` | `/api/auth/setup-status` | Public |
| `POST` | `/api/auth/register` | Public, first user only |
| `POST` | `/api/auth/login` | Public |
| `GET` | `/api/auth/me` | Authenticated |
| `GET` | `/api/auth/users` | `ADMIN` |
| `POST` | `/api/auth/users` | `ADMIN` |
| `PATCH` | `/api/auth/users/{userId}/roles` | `ADMIN` |
| `PATCH` | `/api/auth/users/{userId}/enabled` | `ADMIN` |

First-admin and login request examples:

```json
{
  "username": "admin",
  "displayName": "Store Administrator",
  "password": "at-least-8-characters"
}
```

```json
{
  "username": "admin",
  "password": "at-least-8-characters"
}
```

### Inventory and pricing

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`, `POST` | `/api/inventory/products` | List or create products |
| `GET`, `DELETE` | `/api/inventory/products/{productId}` | Read or delete a product |
| `GET`, `POST` | `/api/inventory/product-families` | List or create families |
| `GET` | `/api/inventory/stock` | Stock summary for all products |
| `GET` | `/api/inventory/stock/{productId}` | Lots and stock for one product |
| `POST` | `/api/inventory/receive-stock` | Receive one inventory lot |
| `POST` | `/api/inventory/product-images` | Upload an image as multipart field `file` |
| `GET` | `/uploads/products/{filename}` | Read an uploaded image |
| `GET`, `POST` | `/api/pricing/prices` | List prices or upsert a product price |
| `GET`, `DELETE` | `/api/pricing/prices/{productId}` | Read or delete a price |
| `GET`, `POST` | `/api/pricing/promotions` | List or create promotions |
| `GET` | `/api/pricing/promotions/{productId}` | List a product's promotions |
| `DELETE` | `/api/pricing/promotions/{promotionId}` | Deactivate a promotion |

Product creation accepts `sku`, `name`, optional `familyId` and `imageUrl`, `unitType`, `perishable`, `reorderPoint`, and `vatRate`. VAT defaults to 19 when omitted. Uploaded images are limited to 5 MB and are stored outside the database.

Pricing first calculates the base unit/weight price and then applies the first active promotion. POS line VAT is calculated from the product's catalog VAT rate, so a basket can contain products with different rates.

### E-commerce storefront

Customer-facing routes are grouped under `/api/store`. Catalog routes are
public; cart, checkout, and order routes require a JWT with the `CUSTOMER`
role.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/store/auth/register` | Register a customer account |
| `POST` | `/api/store/auth/login` | Authenticate a customer |
| `GET` | `/api/store/products` | List/search online products and availability |
| `GET` | `/api/store/products/{productId}` | Read one online product |
| `GET` | `/api/store/cart` | Read the authenticated customer's cart |
| `POST` | `/api/store/cart/items` | Add a product quantity to the cart |
| `PUT` | `/api/store/cart/items/{productId}` | Replace a cart item quantity |
| `DELETE` | `/api/store/cart/items/{productId}` | Remove a cart item |
| `POST` | `/api/store/checkout` | Create a pending-payment order and reserve stock |
| `POST` | `/api/store/checkout/{orderId}/confirm-payment` | Confirm the mock card payment and commit the sale |
| `GET` | `/api/store/orders` | List the authenticated customer's orders |
| `GET` | `/api/store/orders/{orderId}` | Read one owned order |
| `POST` | `/api/store/orders/{orderId}/cancel` | Cancel an unpaid order and release its reservation |

Cart changes do not reserve stock. Checkout recalculates prices and reserves
stock in the same transaction that creates the `PENDING_PAYMENT` order.
Payment confirmation commits the reserved stock and publishes
`OnlineOrderConfirmed` for Finance. Unpaid orders expire after the configured
timeout (`ECOMMERCE_PAYMENT_TIMEOUT_MINUTES`, 15 minutes by default); expiry and
cancellation return reserved quantities to available stock.

Staff order-management routes are available separately under `/api/online-orders`
and require an `ADMIN`, `MANAGER`, or `CASHIER` token. Staff can list and inspect
orders, then advance paid orders through `CONFIRMED -> PREPARING -> READY ->
COMPLETED`. Staff may cancel only unpaid orders; cancellation of a paid order
is rejected because refunds and restocking are not implemented.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/online-orders` | List online orders for staff |
| `GET` | `/api/online-orders/{orderId}` | Read an order with its lines and totals |
| `PATCH` | `/api/online-orders/{orderId}/status` | Advance fulfilment status using `{ "status": "PREPARING" }` |
| `POST` | `/api/online-orders/{orderId}/cancel` | Cancel an unpaid order and release its reservation |

### Point of sale

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`, `POST` | `/api/pos/tills` | List or create tills |
| `GET` | `/api/pos/tills/{tillId}` | Read a till |
| `POST` | `/api/pos/sessions/open` | Open a cash session |
| `GET` | `/api/pos/sessions/{cashSessionId}` | Read a session |
| `GET` | `/api/pos/sessions/active?tillId={tillId}` | Find the active session |
| `POST` | `/api/pos/sessions/{cashSessionId}/close` | Close a session and publish cash variance |
| `GET`, `POST` | `/api/pos/transactions` and `/api/pos/transactions/start` | List or start transactions |
| `GET` | `/api/pos/transactions/{transactionId}` | Read a transaction |
| `POST` | `/api/pos/transactions/{transactionId}/lines` | Add a product line |
| `DELETE` | `/api/pos/transactions/{transactionId}/lines/{lineId}` | Remove a line |
| `POST` | `/api/pos/transactions/{transactionId}/complete` | Complete a cash/card sale |
| `POST` | `/api/pos/transactions/{transactionId}/void` | Void an in-progress transaction |
| `DELETE` | `/api/pos/transactions/{transactionId}` | Delete an in-progress transaction |
| `POST` | `/api/pos/transactions/{transactionId}/scanner-session` | Create a 30-minute phone pairing |
| `POST` | `/api/pos/scanner-sessions/{sessionId}/scans` | Submit a scan with `X-Scanner-Token` |
| `DELETE` | `/api/pos/scanner-sessions/{sessionId}` | Close a phone pairing |

Completing a sale validates payment, deducts stock in FEFO order, and publishes `TransactionCompleted` in the same transaction. If stock is insufficient, the sale is rolled back. Cash must cover the total; card amount must equal it exactly.

### Procurement

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`, `POST` | `/api/procurement/suppliers` | List or create suppliers |
| `GET`, `PUT`, `DELETE` | `/api/procurement/suppliers/{id}` | Read, update, or delete a supplier |
| `GET`, `POST` | `/api/procurement/orders` | List or create draft orders |
| `GET` | `/api/procurement/orders/{id}` | Read an order |
| `POST` | `/api/procurement/orders/{id}/place` | Move `DRAFT` to `PLACED` |
| `POST` | `/api/procurement/orders/{id}/cancel` | Cancel a draft or placed order |
| `POST` | `/api/procurement/orders/{id}/receive` | Receive all order lines into inventory |
| `GET` | `/api/procurement/alerts` | List low-stock alerts |
| `POST` | `/api/procurement/alerts/{id}/acknowledge` | Acknowledge an alert |

Order status is `DRAFT -> PLACED -> RECEIVED`; draft and placed orders can be cancelled. A receipt must include every order line exactly once and match its ordered quantity and unit. Supplier address is persisted.

### Finance

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/finance/ledger` | Flattened ledger view |
| `GET` | `/api/finance/journals` | Balanced journal entries and lines |
| `GET` | `/api/finance/reports/profit-and-loss` | P&L; optional `from` and `to` dates |
| `GET`, `POST` | `/api/finance/supplier-invoices` | List or create supplier invoices |
| `POST` | `/api/finance/supplier-invoices/{invoiceId}/payments` | Pay an invoice |
| `POST` | `/api/finance/settlements/card` | Settle card clearing to the bank |

The P&L defaults to the current month. Journal entries must balance. Event keys, invoice numbers, payment references, and settlement references provide idempotency where applicable.

## Database migrations

Flyway applies all scripts in `src/main/resources/db/migration/` in order:

| Versions | Scope |
| --- | --- |
| V1-V2 | Platform tables and business schemas |
| V2.1 | Core business tables required by V3 and later migrations |
| V3-V6 | Historical tenancy experiment and its removal for the single-store model |
| V7-V8 | Product catalog metadata and normalized product families |
| V9-V11 | Spring Modulith event-publication schema fixes |
| V12 | Supplier address |
| V13 | Performance indexes |
| V14-V18 | Storefront catalog, orders, customer role, and unpaid-order expiry |

Do not edit an applied migration. Add a new versioned migration for every schema change. Existing databases that reached V3 or later before V2.1 was added apply V2.1 once out of order; the migration only creates missing tables.

## API discovery and operations

With the backend running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Management endpoint properties are present in `application.yaml`, but `spring-boot-starter-actuator` is not currently declared in `pom.xml`; do not assume `/actuator/health` or `/actuator/modulith` is available until that dependency is added.

For Windows backup, restore, and scheduled jobs, see [`../ops/windows/README.md`](../ops/windows/README.md). Test a restore against a separate database before relying on a backup.

## Tests and contribution rules

```powershell
.\mvnw.cmd test
```

The full suite uses Testcontainers for fresh-database migration and stock-concurrency tests, so Docker must be running. Test configuration supplies a test-only JWT secret. Tests cover application startup with Flyway and schema validation, Spring Modulith boundaries, authentication rate limiting, POS transactions and scanner sessions, procurement state changes, and finance journal behavior.

## GitHub Actions

`.github/workflows/ci.yml` runs `mvn verify` on pushes, pull requests, and manual runs with Java 21 and Docker support for Testcontainers. Successful push and manual runs attach the tested executable JAR as a `backend-<commit SHA>` artifact for 14 days. Pull requests run the checks without publishing an artifact.

The workflow belongs to the `ERP_backend` repository. The local Docker and Kubernetes deployment files are outside that repository and are not part of this workflow.

When changing the backend:

- Keep module boundaries intact; use public services or events across modules.
- Keep aggregate changes inside service transactions.
- Publish events after the aggregate is saved.
- Preserve `Money` and `Quantity` validation, especially `EACH` versus `WEIGHT`.
- Add focused tests for state transitions, rollback behavior, listeners, and boundaries.
- Replace the local JWT/database defaults and restrict CORS before any shared deployment.
