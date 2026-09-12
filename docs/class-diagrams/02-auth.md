# Class Diagram — `auth` Module

The `auth` module owns user accounts, password hashing, JWT issuance, and role management.  
It exposes a one-time first-admin setup flow; after that, only `ADMIN` users can create or modify accounts.

```mermaid
classDiagram

    %% ── Entities ───────────────────────────────────────────────
    class User {
        <<Entity — table: app_user>>
        -UUID id PK
        -String username UK
        -String displayName
        -String passwordHash
        -boolean enabled
        -Instant createdAt
        -Set~Role~ roles
        -long version
        +create(username, displayName, passwordHash, roles) User$
        +replaceRoles(Set~Role~) void
        +setEnabled(boolean) void
    }
    note for User "Username is lowercased on creation.\nAt least one role is always required.\nPassword is stored as a BCrypt hash — never plain text."

    class AppUserRoles {
        <<ElementCollection — table: app_user_roles>>
        -UUID user_id FK
        -Role role
    }

    %% ── Enum ───────────────────────────────────────────────────
    class Role {
        <<enumeration>>
        ADMIN
        MANAGER
        CASHIER
        INVENTORY_MANAGER
        ACCOUNTANT
    }

    %% ── Service ────────────────────────────────────────────────
    class AuthService {
        <<Service>>
        -UserRepository userRepository
        -PasswordEncoder passwordEncoder
        -JwtUtil jwtUtil
        +setupStatus() boolean
        +register(request) LoginResponse
        +login(request) LoginResponse
        +createUser(request) UserResponse
        +replaceRoles(userId, roles) UserResponse
        +setEnabled(userId, enabled) UserResponse
        +listUsers() List~UserResponse~
    }
    note for AuthService "register() is gated: only allowed when the user table is empty.\nLogin returns a signed HS256 JWT token valid for 8 hours (configurable)."

    %% ── Exception ──────────────────────────────────────────────
    class InvalidCredentialsException {
        <<RuntimeException>>
    }

    %% ── Relationships ──────────────────────────────────────────
    User "1" *-- "many" AppUserRoles : roles (ElementCollection)
    AppUserRoles --> Role : role
    AuthService --> User : manages
    AuthService ..> InvalidCredentialsException : throws on bad password
```

## Enumerations

| Value | Description |
|---|---|
| `ADMIN` | Full system access, including user management |
| `MANAGER` | All business modules except Users |
| `CASHIER` | Dashboard and POS only |
| `INVENTORY_MANAGER` | Dashboard, Inventory, Pricing, Procurement |
| `ACCOUNTANT` | Dashboard and Finance only |

## Key behaviours

- **First-admin setup** — `register()` is only accepted when `app_user` is empty. The first account always receives `ADMIN`.
- **Subsequent users** — created by `ADMIN` through `POST /api/auth/users`; roles are set at creation time.
- **JWT** — HS256, signed with `JWT_SECRET`, carries the `roles` claim, valid for `JWT_EXPIRATION_SECONDS` (default 8 h). Role changes take effect on the next login.
- **Optimistic locking** — `version` column prevents concurrent role/enabled changes.
