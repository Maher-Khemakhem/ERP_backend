# Supermarket ERP Backend

Spring Boot REST API for the supermarket ERP. The backend is a modular monolith built with Spring Modulith and Spring Data JPA, backed by PostgreSQL.

## Technology and modules

- Java 21
- Spring Boot 4.1
- Spring Web MVC, Bean Validation, Spring Data JPA
- Spring Modulith events and JDBC event publication registry
- PostgreSQL
- Springdoc OpenAPI / Swagger UI

Business modules under `src/main/java/com/example/erp/`:

| Module | Responsibility |
| --- | --- |
| `inventory` | Products, stock levels, lots, stock receipts, and stock movements |
| `pricing` | Product prices, promotions, and POS price calculation |
| `pos` | Tills, cash sessions, transactions, payments, and phone scanning |
| `procurement` | Suppliers, purchase orders, receiving, and low-stock alerts |
| `finance` | Journals, ledger entries, P&L, supplier invoices, payments, and settlements |
| `auth` | Users, password hashing, JWT tokens, and role management |
| `shared` | Money, quantities, and domain-event abstractions |

## Run locally

Create the database first:

```sql
CREATE DATABASE supermarket_erp_saas;
```

Then run the application from this directory:

```powershell
.\mvnw.cmd spring-boot:run
```

The API is available at `http://localhost:8080`.

Configuration is in `src/main/resources/application.yaml`:

| Setting | Default |
| --- | --- |
| PostgreSQL URL | `${DB_URL:jdbc:postgresql://localhost:5432/supermarket_erp_saas}` |
| PostgreSQL user | `${DB_USERNAME:postgres}` |
| PostgreSQL password | `${DB_PASSWORD:postgres}` |
| HTTP port | `8080` |
| Hibernate schema mode | `update` (local development only) |

Override the database connection before starting the app, for example:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/supermarket_erp_saas"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-password"
.\mvnw.cmd spring-boot:run
```

Flyway currently creates the `platform` schema and the initial `stores` and
`store_users` tables. Tenant columns, identity foreign keys, and RLS policies
are added in the next Stage 1 migration. The legacy `supermarket_erp` database
is not modified by this application configuration.

## API conventions

- Base URL: `http://localhost:8080`
- All business endpoints start with `/api`.
- IDs are UUIDs.
- Dates use ISO-8601 `yyyy-MM-dd`; timestamps use ISO-8601 timestamps.
- `unitType` is `EACH` or `WEIGHT`. `EACH` quantities must be whole numbers; `WEIGHT` quantities are kilograms with three-decimal precision.
- Monetary input is numeric JSON (for example `12.500`); monetary response fields are usually strings such as `12.500 TND`.
- Authentication uses a stateless JWT bearer token. Send it as `Authorization: Bearer <token>` on protected requests.
- Roles are `ADMIN`, `MANAGER`, `CASHIER`, `INVENTORY_MANAGER`, and `ACCOUNTANT`. Role changes apply when the user signs in again.
- Validation and business errors use this shape:

  ```json
  {
    "timestamp": "2026-09-06T12:00:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "readable explanation"
  }
  ```

  Typical statuses are `400` for invalid input, `404` for missing resources, `409` for invalid state transitions, and `500` for unexpected errors.

## Authentication and roles

The first account is created through the one-time setup endpoint. Once a user exists, public registration is closed and only an administrator can create or manage users.

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/auth/setup-status` | Public | Check whether the first admin must be created |
| `POST` | `/api/auth/register` | Public, first user only | Create the first `ADMIN` account and receive a token |
| `POST` | `/api/auth/login` | Public | Authenticate and receive a JWT token |
| `GET` | `/api/auth/me` | Authenticated | Get the current user |
| `GET` | `/api/auth/users` | `ADMIN` | List users |
| `POST` | `/api/auth/users` | `ADMIN` | Create a user with roles |
| `PATCH` | `/api/auth/users/{userId}/roles` | `ADMIN` | Replace a user's roles |
| `PATCH` | `/api/auth/users/{userId}/enabled` | `ADMIN` | Enable or disable a user |

First-admin request:

```json
{
  "username": "admin",
  "displayName": "Store Administrator",
  "password": "at-least-8-characters"
}
```

Login response:

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

Use the returned token on protected API calls:

```text
Authorization: Bearer eyJ...
```

The phone scanner endpoints remain public at the HTTP layer because the QR link contains a separate short-lived scanner token. The scanner token still only permits scans for its paired, in-progress transaction.

## REST API reference

The following catalog reflects the controllers currently in the source tree. Request bodies are JSON unless stated otherwise.

### Inventory

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/inventory/products` | List products |
| `POST` | `/api/inventory/products` | Create a product |
| `GET` | `/api/inventory/products/{productId}` | Get one product |
| `GET` | `/api/inventory/stock/{productId}` | Get total stock and non-empty lots |
| `POST` | `/api/inventory/receive-stock` | Receive stock directly |

Create product:

```json
{
  "sku": "TOM-001",
  "name": "Tomatoes",
  "unitType": "WEIGHT",
  "perishable": true,
  "reorderPoint": 10,
  "vatRate": 19
}
```

`vatRate` defaults to `19` when omitted. Receive stock accepts `productId`, `quantityValue`, `unitType`, and optional `lotNumber`, `expiryDate`, `receivedDate`, `supplierReference`, and `unitCost`; it returns `{ "status": "received" }`.

### Pricing

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/pricing/prices` | List product prices |
| `POST` | `/api/pricing/prices` | Create or update a product price (idempotent by product) |
| `GET` | `/api/pricing/prices/{productId}` | Get one product price |
| `DELETE` | `/api/pricing/prices/{productId}` | Delete a product price |
| `GET` | `/api/pricing/promotions` | List promotions |
| `POST` | `/api/pricing/promotions` | Create a percentage promotion |
| `GET` | `/api/pricing/promotions/{productId}` | List promotions for a product |
| `DELETE` | `/api/pricing/promotions/{promotionId}` | Deactivate a promotion |

Price input is `{ "productId": "uuid", "unitPrice": 3.2 }`. Promotion input is:

```json
{
  "productId": "uuid",
  "discountPercent": 10,
  "validFrom": "2026-09-01T00:00:00Z",
  "validUntil": "2026-09-30T23:59:59Z"
}
```

### Point of sale

#### Tills and cash sessions

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/pos/tills` | List tills with their latest session summary |
| `POST` | `/api/pos/tills` | Create a till; body `{ "tillNumber": "TILL-01" }` |
| `GET` | `/api/pos/tills/{tillId}` | Get one till |
| `POST` | `/api/pos/sessions/open` | Open a cash session |
| `GET` | `/api/pos/sessions/{cashSessionId}` | Get a session |
| `GET` | `/api/pos/sessions/active?tillId={tillId}` | Get the open session, or `204` if none exists |
| `POST` | `/api/pos/sessions/{cashSessionId}/close` | Close a session |

Open and close bodies:

```json
// POST /api/pos/sessions/open
{
  "tillId": "uuid",
  "cashierName": "Alex",
  "openingFloat": 100
}

// POST /api/pos/sessions/{cashSessionId}/close
{
  "countedCash": 245.5
}
```

#### Transactions

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/pos/transactions` | List transactions, newest first |
| `POST` | `/api/pos/transactions/start` | Start a transaction; body `{ "cashSessionId": "uuid" }` |
| `GET` | `/api/pos/transactions/{transactionId}` | Get a transaction and lines |
| `POST` | `/api/pos/transactions/{transactionId}/lines` | Add a product line |
| `DELETE` | `/api/pos/transactions/{transactionId}/lines/{lineId}` | Remove a line |
| `POST` | `/api/pos/transactions/{transactionId}/complete` | Complete and pay a transaction |
| `POST` | `/api/pos/transactions/{transactionId}/void` | Void an in-progress transaction |
| `DELETE` | `/api/pos/transactions/{transactionId}` | Delete an in-progress transaction |

Add a line with `{ "productId": "uuid", "quantityValue": 2, "unitType": "EACH" }`. Complete with `paymentMethod` `CASH` or `CARD`; `amountReceived` is optional, but if supplied cash must cover the total and card must equal the total:

```json
{
  "paymentMethod": "CASH",
  "amountReceived": 20
}
```

POS prices are VAT-exclusive. The ticket applies the configured 19% VAT rate to the final net subtotal. Completing a sale deducts inventory and publishes a finance event.

#### Phone scanner

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/pos/transactions/{transactionId}/scanner-session` | Create a 30-minute pairing; returns `sessionId`, `token`, and `expiresAt` |
| `POST` | `/api/pos/scanner-sessions/{sessionId}/scans` | Scan a barcode using `X-Scanner-Token` and `{ "barcode": "SKU" }` |
| `DELETE` | `/api/pos/scanner-sessions/{sessionId}` | Close a pairing using `X-Scanner-Token` |

The phone scanner looks up the barcode as a product SKU. A scanned `WEIGHT` item adds `0.1 kg`; an `EACH` item adds one unit. Pairings are held in memory and are invalidated when the backend restarts.

### Procurement

#### Suppliers

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/procurement/suppliers` | List suppliers |
| `POST` | `/api/procurement/suppliers` | Create a supplier |
| `GET` | `/api/procurement/suppliers/{id}` | Get one supplier |
| `PUT` | `/api/procurement/suppliers/{id}` | Replace supplier details |
| `DELETE` | `/api/procurement/suppliers/{id}` | Delete a supplier |

Supplier input uses `name`, `contactEmail`, `phone`, and `address`. The current domain model persists the name, phone, and email; `address` is accepted by the DTO but is not currently stored.

#### Purchase orders and alerts

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/procurement/orders` | List purchase orders |
| `POST` | `/api/procurement/orders` | Create a draft order |
| `GET` | `/api/procurement/orders/{id}` | Get an order |
| `POST` | `/api/procurement/orders/{id}/place` | Move a draft to `PLACED` |
| `POST` | `/api/procurement/orders/{id}/cancel` | Cancel a draft or placed order |
| `POST` | `/api/procurement/orders/{id}/receive` | Receive one or more order lines |
| `GET` | `/api/procurement/alerts` | List low-stock alerts |
| `POST` | `/api/procurement/alerts/{id}/acknowledge` | Acknowledge an alert |

Create order body:

```json
{
  "supplierId": "uuid",
  "lines": [
    {
      "productId": "uuid",
      "quantityValue": 12,
      "unitType": "EACH",
      "unitCost": 1.25
    }
  ]
}
```

Receive order lines with `purchaseOrderLineId`, `quantityValue`, `unitType`, and optional `lotNumber`, `expiryDate`, `receivedDate`, and `supplierReference`. Order statuses are `DRAFT`, `PLACED`, `RECEIVED`, and `CANCELLED`.

### Finance

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/finance/ledger` | Backward-compatible flattened ledger view |
| `GET` | `/api/finance/journals` | Balanced journal entries and debit/credit lines |
| `GET` | `/api/finance/reports/profit-and-loss` | P&L for the current month or a requested date range |
| `GET` | `/api/finance/supplier-invoices` | List supplier invoices |
| `POST` | `/api/finance/supplier-invoices` | Create an invoice |
| `POST` | `/api/finance/supplier-invoices/{invoiceId}/payments` | Record an invoice payment |
| `POST` | `/api/finance/settlements/card` | Settle card clearing to the bank |

The P&L accepts optional `from=yyyy-MM-dd` and `to=yyyy-MM-dd` query parameters. Invoice creation accepts `supplierId`, optional `purchaseOrderId`, `invoiceNumber`, `subtotal`, `vatRate`, optional `issueDate`, and optional `dueDate`. Payment accepts `paymentReference`, `amount`, `paymentMethod`, and optional `paidDate`. Card settlement accepts `settlementReference`, `amount`, and optional `settlementDate`.

Journal event keys are used for idempotency when domain events are replayed, so a replay does not create duplicate journal entries.

## Observability and API discovery

With the backend running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI document: `http://localhost:8080/v3/api-docs`

`application.yaml` contains management endpoint exposure settings, but Actuator is not currently declared as a Maven dependency. Add `spring-boot-starter-actuator` before relying on `/actuator/health` or `/actuator/modulith`.

## Tests and build

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Tests are under `src/test/java` and cover application startup, modularity, procurement, POS, finance, and scanner behavior.

## Production checklist

- Add authentication, authorization, and audit logging.
- Replace `ddl-auto: update` with versioned database migrations.
- Use a secret-managed database password and a non-default database user.
- Restrict CORS in `WebConfig` to the deployed frontend origin.
- Move temporary scanner pairings to a shared store if multiple backend instances are deployed.
- Review VAT, currency, accounting, and date/time rules with the business owner before production use.

## Owner-PC backups

For the single-store Windows deployment, use the scripts in [../ops/windows/](../ops/windows/). Back up the complete `supermarket_erp` database with PostgreSQL `pg_dump` and export globals separately. Store a second copy on an external USB drive and protect the password file and globals dump as confidential data.

Restore into a separate database such as `supermarket_erp_restore` first. Verify application data and authenticated workflows before replacing a live database. Take a fresh backup before every schema or application upgrade. The current `ddl-auto: update` setting is for local bootstrap only; production upgrades require reviewed Flyway or Liquibase migrations before changing to `validate` or `none`.
