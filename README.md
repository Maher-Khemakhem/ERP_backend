# Supermarket ERP — Backend

Spring Boot 4.1 REST API for a single-store grocery ERP.  
The backend is a **modular monolith** structured with Spring Modulith and backed by PostgreSQL.  
Domain events connect the business modules so that sales, goods receipts, and cash-session closings all produce balanced finance journal entries automatically.

---

## Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| Web | Spring Web MVC |
| Persistence | Spring Data JPA · Hibernate |
| Database migrations | Flyway (V1–V8) |
| Module boundaries | Spring Modulith 2.1 |
| Security | Spring Security · Spring OAuth2 Resource Server · HS256 JWT |
| Validation | Jakarta Bean Validation |
| API docs | Springdoc OpenAPI / Swagger UI |
| Database | PostgreSQL 14+ |

---

## Business modules

All modules live under `src/main/java/com/example/erp/`.  
Spring Modulith enforces that no module directly imports the internal packages of another; cross-module communication uses public service APIs or domain events.

| Module | Responsibility |
|---|---|
| `auth` | Users, BCrypt password hashing, JWT issuance, role management, first-admin setup |
| `inventory` | Product catalog, product families, unit types, VAT rates, FEFO lot-based stock, reorder points |
| `pricing` | Per-product prices (TND), time-bounded percentage promotions, POS price calculation |
| `pos` | Tills, cashier cash sessions, sale transactions, line items, cash/card payments, voids, phone-scanner QR pairing |
| `procurement` | Suppliers, purchase orders (DRAFT → PLACED → RECEIVED), goods receipt into lots, low-stock alerts |
| `finance` | Double-entry journal, ledger view, P&L reporting, supplier invoices, payments, card settlement — all fed by domain events |
| `shared` | `Money` (TND, 3 dp), `Quantity` (EACH / WEIGHT), `DomainEvent` base type |

### Domain event flow

```
TransactionCompleted  ──→  Finance: posts sales revenue, VAT, COGS, payment entries
GoodsReceived         ──→  Finance: posts inventory and GRNI entries
CashSessionClosed     ──→  Finance: posts cash deposit and over/short variance
LowStockThresholdReached ──→  Procurement: creates a low-stock alert record
```

---

## Database schema

The schema is managed by Flyway. Key migration history:

| Version | Purpose |
|---|---|
| V1 | Platform / tenancy bootstrap tables |
| V2 | Business module schemas (`inventory`, `pricing`, `pos`, `procurement`, `finance`, `auth`) |
| V3–V5 | Experimental multi-store columns (superseded) |
| V6 | Removes multi-store tenancy; returns to clean single-store model |
| V7 | Adds product catalog metadata (image URL) |
| V8 | Normalises product families into a dedicated table |

`ddl-auto: update` is kept for local bootstrap. Use `validate` or `none` in production after the migrations are reviewed.

---

## Running locally

### 1 — Create the database

```sql
CREATE DATABASE supermarket_erp_saas;
```

### 2 — Set environment variables (optional override)

```powershell
$env:DB_URL      = "jdbc:postgresql://localhost:5432/supermarket_erp_saas"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-password"
$env:JWT_SECRET  = "replace-with-at-least-32-random-chars"
```

### 3 — Start the application

```powershell
.\mvnw.cmd spring-boot:run
```

The API is available at `http://localhost:8080`. Flyway applies all migrations on first start.

### Configuration reference

All settings are in `src/main/resources/application.yaml`.

| Setting | Environment variable | Default (local) |
|---|---|---|
| PostgreSQL URL | `DB_URL` | `jdbc:postgresql://localhost:5432/supermarket_erp_saas` |
| PostgreSQL user | `DB_USERNAME` | `postgres` |
| PostgreSQL password | `DB_PASSWORD` | `postgres` |
| JWT signing secret | `JWT_SECRET` | `local-only-change-this-jwt-secret-32-characters-minimum` |
| JWT token lifetime | `JWT_EXPIRATION_SECONDS` | `28800` (8 h) |
| Product image upload dir | `APP_UPLOAD_DIR` | `uploads/products` |
| HTTP port | — | `8080` |
| Hibernate schema mode | — | `update` |

> **Security** The default JWT secret must be replaced in every shared or production environment. It must be at least 32 characters for HS256.

---

## API conventions

- **Base URL**: `http://localhost:8080`
- All business endpoints are prefixed `/api`.
- IDs are UUIDs.
- Dates: ISO-8601 `yyyy-MM-dd`. Timestamps: ISO-8601 with time zone.
- `unitType` is `EACH` (whole numbers) or `WEIGHT` (kilograms, three decimal places).
- Monetary **input**: plain numeric JSON, e.g. `12.500`.
- Monetary **output**: formatted string, e.g. `"12.500 TND"`.
- Authentication: stateless JWT bearer token — `Authorization: Bearer <token>`.
- Roles: `ADMIN`, `MANAGER`, `CASHIER`, `INVENTORY_MANAGER`, `ACCOUNTANT`. Role changes take effect on the next login.

### Error shape

```json
{
  "timestamp": "2026-09-12T14:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable explanation"
}
```

| HTTP status | Meaning |
|---|---|
| `400` | Invalid input or business rule violation |
| `404` | Resource not found |
| `409` | Invalid state transition |
| `500` | Unexpected server error |

---

## Authentication endpoints

The first account is created through the one-time setup endpoint.  
After that, public registration is closed; only `ADMIN` can create or manage users.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/auth/setup-status` | Public | Check whether the first admin still needs to be created |
| `POST` | `/api/auth/register` | Public — first user only | Create the first `ADMIN` account and receive a token |
| `POST` | `/api/auth/login` | Public | Authenticate and receive a JWT |
| `GET` | `/api/auth/me` | Authenticated | Return the current user |
| `GET` | `/api/auth/users` | `ADMIN` | List all users |
| `POST` | `/api/auth/users` | `ADMIN` | Create a user with specified roles |
| `PATCH` | `/api/auth/users/{userId}/roles` | `ADMIN` | Replace a user's roles |
| `PATCH` | `/api/auth/users/{userId}/enabled` | `ADMIN` | Enable or disable a user account |

**First-admin request body**

```json
{
  "username": "admin",
  "displayName": "Store Administrator",
  "password": "at-least-8-characters"
}
```

**Login response**

```json
{
  "token": "eyJ...",
  "user": {
    "id": "uuid",
    "username": "admin",
    "displayName": "Store Administrator",
    "roles": ["ADMIN"],
    "enabled": true,
    "createdAt": "2026-09-07T10:00:00Z"
  }
}
```

Send the token on every protected request:

```http
Authorization: Bearer eyJ...
```

---

## REST API reference

### Inventory

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/inventory/products` | List all products |
| `POST` | `/api/inventory/products` | Create a product |
| `GET` | `/api/inventory/products/{productId}` | Get one product |
| `GET` | `/api/inventory/product-families` | List product families |
| `POST` | `/api/inventory/product-families` | Create a product family |
| `GET` | `/api/inventory/stock/{productId}` | Get total on-hand stock and non-empty lots |
| `POST` | `/api/inventory/receive-stock` | Receive stock (creates a lot) |

**Create product — request body**

```json
{
  "sku": "SAFIA-15L-001",
  "name": "Safia 1.5L",
  "familyId": "uuid-of-water-family",
  "imageUrl": "https://example.com/safia-water.jpg",
  "unitType": "EACH",
  "perishable": false,
  "reorderPoint": 24,
  "vatRate": 19
}
```

- `familyId` is optional. Create a family (e.g. `Water`) first, then assign product variants to it.
- `vatRate` defaults to `19` when omitted.
- `unitType` must be `EACH` or `WEIGHT`.

**Receive stock — request body**

```json
{
  "productId": "uuid",
  "quantityValue": 48,
  "unitType": "EACH",
  "lotNumber": "LOT-2026-09",
  "expiryDate": "2027-06-01",
  "receivedDate": "2026-09-12",
  "supplierReference": "INV-1234",
  "unitCost": 0.850
}
```

Returns `{ "status": "received" }`.

---

### Pricing

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/pricing/prices` | List all product prices |
| `POST` | `/api/pricing/prices` | Create or update a price (upsert by product) |
| `GET` | `/api/pricing/prices/{productId}` | Get the price for one product |
| `DELETE` | `/api/pricing/prices/{productId}` | Delete a product price |
| `GET` | `/api/pricing/promotions` | List all promotions |
| `POST` | `/api/pricing/promotions` | Create a time-bounded percentage promotion |
| `GET` | `/api/pricing/promotions/{productId}` | List promotions for one product |
| `DELETE` | `/api/pricing/promotions/{promotionId}` | Deactivate a promotion |

**Set price** — `{ "productId": "uuid", "unitPrice": 3.200 }`

**Create promotion**

```json
{
  "productId": "uuid",
  "discountPercent": 10,
  "validFrom": "2026-09-01T00:00:00Z",
  "validUntil": "2026-09-30T23:59:59Z"
}
```

---

### Point of sale

#### Tills and cash sessions

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/pos/tills` | List tills with latest session summary |
| `POST` | `/api/pos/tills` | Create a till — `{ "tillNumber": "TILL-01" }` |
| `GET` | `/api/pos/tills/{tillId}` | Get one till |
| `POST` | `/api/pos/sessions/open` | Open a cashier session |
| `GET` | `/api/pos/sessions/{cashSessionId}` | Get a session |
| `GET` | `/api/pos/sessions/active?tillId={tillId}` | Get the active session, or `204` if none |
| `POST` | `/api/pos/sessions/{cashSessionId}/close` | Close a session |

**Open session** — `{ "tillId": "uuid", "cashierName": "Alex", "openingFloat": 100 }`  
**Close session** — `{ "countedCash": 245.500 }`

#### Transactions

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/pos/transactions` | List transactions, newest first |
| `POST` | `/api/pos/transactions/start` | Start a transaction — `{ "cashSessionId": "uuid" }` |
| `GET` | `/api/pos/transactions/{transactionId}` | Get a transaction with all lines |
| `POST` | `/api/pos/transactions/{transactionId}/lines` | Add a product line |
| `DELETE` | `/api/pos/transactions/{transactionId}/lines/{lineId}` | Remove a line |
| `POST` | `/api/pos/transactions/{transactionId}/complete` | Complete and pay |
| `POST` | `/api/pos/transactions/{transactionId}/void` | Void an in-progress transaction |
| `DELETE` | `/api/pos/transactions/{transactionId}` | Delete an in-progress transaction |

**Add line** — `{ "productId": "uuid", "quantityValue": 2, "unitType": "EACH" }`

**Complete transaction**

```json
{
  "paymentMethod": "CASH",
  "amountReceived": 20.000
}
```

`paymentMethod` is `CASH` or `CARD`. For cash, `amountReceived` must cover the total. For card, it must equal the total exactly. Completing a sale deducts inventory (FEFO order) and publishes a `TransactionCompleted` event for finance.

> **Note** POS prices are VAT-exclusive. The ticket applies the store's configured 19 % VAT rate to the net subtotal.

#### Phone scanner

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/pos/transactions/{transactionId}/scanner-session` | Create a 30-minute phone pairing; returns `sessionId`, `token`, `expiresAt` |
| `POST` | `/api/pos/scanner-sessions/{sessionId}/scans` | Submit a barcode scan (header `X-Scanner-Token`, body `{ "barcode": "SKU" }`) |
| `DELETE` | `/api/pos/scanner-sessions/{sessionId}` | Close the pairing (header `X-Scanner-Token`) |

The scanner resolves the barcode as a product SKU. `EACH` items add 1 unit; `WEIGHT` items add 0.1 kg. Pairings are in-memory and are lost on backend restart.

---

### Procurement

#### Suppliers

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/procurement/suppliers` | List all suppliers |
| `POST` | `/api/procurement/suppliers` | Create a supplier |
| `GET` | `/api/procurement/suppliers/{id}` | Get one supplier |
| `PUT` | `/api/procurement/suppliers/{id}` | Update supplier details |
| `DELETE` | `/api/procurement/suppliers/{id}` | Delete a supplier |

Fields: `name`, `contactEmail`, `phone`, `address`. Note: `address` is accepted by the DTO but not currently persisted.

#### Purchase orders

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/procurement/orders` | List all purchase orders |
| `POST` | `/api/procurement/orders` | Create a draft order |
| `GET` | `/api/procurement/orders/{id}` | Get an order |
| `POST` | `/api/procurement/orders/{id}/place` | Advance draft → `PLACED` |
| `POST` | `/api/procurement/orders/{id}/cancel` | Cancel a draft or placed order |
| `POST` | `/api/procurement/orders/{id}/receive` | Receive goods and update stock |

**Create order**

```json
{
  "supplierId": "uuid",
  "lines": [
    {
      "productId": "uuid",
      "quantityValue": 12,
      "unitType": "EACH",
      "unitCost": 1.250
    }
  ]
}
```

Order statuses: `DRAFT` → `PLACED` → `RECEIVED`; or `CANCELLED` from `DRAFT`/`PLACED`.

#### Low-stock alerts

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/procurement/alerts` | List low-stock alerts |
| `POST` | `/api/procurement/alerts/{id}/acknowledge` | Acknowledge an alert |

Alerts are raised automatically when a product's on-hand quantity falls below its `reorderPoint`. They do not automatically create purchase orders.

---

### Finance

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/finance/ledger` | Flattened ledger view (backward-compatible) |
| `GET` | `/api/finance/journals` | Balanced journal entries with debit/credit lines |
| `GET` | `/api/finance/reports/profit-and-loss` | P&L for the current month or a date range |
| `GET` | `/api/finance/supplier-invoices` | List supplier invoices |
| `POST` | `/api/finance/supplier-invoices` | Create a supplier invoice |
| `POST` | `/api/finance/supplier-invoices/{invoiceId}/payments` | Record a payment against an invoice |
| `POST` | `/api/finance/settlements/card` | Settle card clearing to the bank account |

**P&L query parameters**: `from=yyyy-MM-dd` and `to=yyyy-MM-dd` (both optional; defaults to current month).

**Create invoice**

```json
{
  "supplierId": "uuid",
  "purchaseOrderId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "subtotal": 150.000,
  "vatRate": 19,
  "issueDate": "2026-09-10",
  "dueDate": "2026-10-10"
}
```

**Record payment** — `{ "paymentReference": "PAY-001", "amount": 150.000, "paymentMethod": "BANK_TRANSFER", "paidDate": "2026-09-12" }`

Journal entries are created idempotently using event keys, so replaying a domain event never duplicates a journal entry.

---

## Observability and API discovery

| URL | Description |
|---|---|
| `http://localhost:8080/swagger-ui/index.html` | Interactive Swagger UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3 JSON document |

> `spring-boot-starter-actuator` is not currently in `pom.xml`. Add it before relying on `/actuator/health`, `/actuator/info`, or `/actuator/modulith`.

---

## Tests and build

```powershell
# Run all tests (unit, integration, Spring Modulith boundary verification)
.\mvnw.cmd test

# Build the production JAR
.\mvnw.cmd package
```

Tests live under `src/test/java/com/example/erp/` and cover application context startup, module boundary enforcement (`ModularityTests`), POS workflows, procurement, finance event listeners, and the phone-scanner pairing.

---

## Production checklist

> **⚠ Review every item below before opening the store to real data.**

- [ ] Replace `JWT_SECRET` with a strong, randomly generated value (≥ 32 chars).
- [ ] Use a dedicated database user and strong `DB_PASSWORD`; revoke `postgres` superuser access.
- [ ] Restrict CORS in `WebConfig.java` to the deployed frontend origin only.
- [ ] Change `ddl-auto: update` → `validate` (or `none`) after Flyway migrations are established.
- [ ] Verify product VAT rates, currency (TND), and accounting rules with the business owner.
- [ ] Move scanner pairings to a shared store (Redis, etc.) if multiple backend instances are deployed.
- [ ] Add `spring-boot-starter-actuator` for health and readiness probes.
- [ ] Configure and test nightly PostgreSQL backups — see [`../ops/windows/`](../ops/windows/).

---

## Backup and restore (Windows store PC)

Use the PowerShell scripts in [`../ops/windows/`](../ops/windows/):

- `Backup-Postgres.ps1` — custom-format dump + globals + SHA-256 checksums, with retention management.
- `Register-ERPBackupTask.ps1` — registers a nightly Windows Task Scheduler job.
- `Verify-PostgresBackup.ps1` — verifies checksum integrity before a restore.
- `Restore-Postgres.ps1` — restores into a named database (use a separate test database first).

Always test a restore into `supermarket_erp_restore` and verify data before touching the live database. Take a fresh backup before every schema upgrade.
