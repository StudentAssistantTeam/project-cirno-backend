CIRNO Application Back-End

The Application Back-End is the core component responsible for managing user interactions, handling data routing
and providing middleware services between the front-end and various system components. Developed using Spring Boot,
this module will provide essential services such as:

User Management: Handles user authentication, registration, and profile management.
Middleware Services: Acts as a bridge between the front-end interface and backend services like file management or 
routing agents.
File Management: Facilitates storage, retrieval, and management of files, ensuring smooth operation of the 
application.

This back-end service is designed to be scalable, secure, and efficient, ensuring smooth communication and data 
handling between the client-side and other system components. It plays a crucial role in providing the business 
logic and managing the underlying data flow.

## API Endpoints

### `POST /api/auth/signup`

Create a new user account and receive a JWT token.

**Request:**

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "securePass123"
}
```

**Validation:**

| Field    | Rules |
|----------|-------|
| username | 3-50 characters, letters, digits, dashes, underscores only |
| email    | Must be a valid email format |
| password | 8-64 characters, must include at least one letter and one digit, ASCII letters/digits and these special characters only: `` ~!@#$%^&*()_+-={}:"<>?[];',./ `` |

**Response (201 Created):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "email": "john@example.com",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Errors:**

- `400 Bad Request` — invalid input, username already taken, or email already in use

---

### `POST /api/auth/login`

Authenticate with username or email and receive a JWT token.

**Request:**

```json
{
  "usernameOrEmail": "johndoe",
  "password": "securePass123"
}
```

The `usernameOrEmail` field accepts either a username or email address. If it contains `@`, it is treated as an email lookup.

**Response (200 OK):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "johndoe",
  "email": "john@example.com"
}
```

**Errors:**

- `400 Bad Request` — missing `usernameOrEmail` or `password`
- `401 Unauthorized` — invalid credentials

---

### Using the JWT Token

Include the token in the `Authorization` header for authenticated requests:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

### `POST /api/events`

Create a new event. Requires authentication.

**Request:**

```json
{
  "name": "Study Session",
  "datetime": "2026-04-01T14:00:00",
  "description": "Review algorithms",
  "tags": ["math", "study"]
}
```

| Field       | Rules |
|-------------|-------|
| name        | Required, max 255 characters |
| datetime    | Required, ISO 8601 format (`YYYY-MM-DDTHH:mm:ss`) |
| description | Optional, free text |
| tags        | Optional, list of tag name strings (each max 50 characters). Tags are created if they don't already exist and reused if they do. |

**Response (201 Created):**

Echoes back the original request body so the frontend can verify creation.

```json
{
  "name": "Study Session",
  "datetime": "2026-04-01T14:00:00",
  "description": "Review algorithms",
  "tags": ["math", "study"]
}
```

**Errors:**

- `400 Bad Request` — missing or invalid fields, invalid datetime format, or tag name exceeds 50 characters
- `403 Forbidden` — no JWT token provided

---

### `GET /api/events`

Retrieve events within a time range. Requires authentication.

**Query Parameters:**

| Parameter | Type   | Description |
|-----------|--------|-------------|
| `start`   | String | Start datetime in ISO 8601 format (`YYYY-MM-DDTHH:mm:ss`) |
| `length`  | Int    | Number of months from the start date defining the end date |

**Example:** `GET /api/events?start=2000-01-01T00:00:00&length=12`

Returns events from 2000-01-01 (inclusive) to 2001-01-01 (exclusive).

**Response (200 OK):**

```json
{
  "events": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Study Session",
      "datetime": "2026-04-01T14:00:00",
      "description": "Review algorithms",
      "tags": ["math", "study"],
      "createdAt": "2026-03-28T10:00:00"
    }
  ],
  "total": 1
}
```

Events are sorted by datetime in ascending order.

**Errors:**

- `400 Bad Request` — invalid `start` datetime format (must be valid ISO 8601)
- `403 Forbidden` — no JWT token provided

---

### `GET /api/health`

Unauthenticated health check. Use this to verify server reachability.

**Response (200 OK):**

```json
{ "status": "ok" }
```

---

### `GET /api/me`

Returns the authenticated user's profile. Use this to verify both connectivity and auth status (token validity).

**Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "email": "john@example.com"
}
```

**Errors:**

- `403 Forbidden` — no JWT token or invalid token provided
