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

### Error Responses

All endpoints use a consistent error response format:

```json
{
  "error": "Session not found"
}
```

| Status Code | Meaning |
|-------------|---------|
| `400 Bad Request` | Invalid input, missing required fields, or resource not found |
| `403 Forbidden` | Missing, invalid, or expired JWT token |
| `500 Internal Server Error` | Unexpected server error |

---

### Frontend Heartbeat Strategy

Use two endpoints to check connectivity and auth status independently:

1. **`GET /api/health`** — unauthenticated, no token needed. Returns `200 { "status": "ok" }` if the server is reachable. Poll this periodically (e.g. every 30s) to detect network/server outages.

2. **`GET /api/me`** — authenticated. Returns `200` with user info if the JWT is valid, or `403` if expired/missing. Call this to detect when the user's token has expired and a re-login is needed.

**Recommended pattern:**

- On app load: call both. `/api/health` confirms the backend is up, `/api/me` confirms the stored token is still valid.
- Background: poll `/api/health` every 30s for connectivity.
- On navigation/protected action: call `/api/me` to gate access before making API calls. If it returns `403`, redirect to login.

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

### `PUT /api/events/{id}`

Update an existing event. Requires authentication. Only the event's owner can update it.

**Request:**

Same format as `POST /api/events`. Tags are fully replaced by the new list.

```json
{
  "name": "Updated Study Session",
  "datetime": "2026-04-01T15:00:00",
  "description": "Updated description",
  "tags": ["math", "review"]
}
```

| Field       | Rules |
|-------------|-------|
| name        | Required, max 255 characters |
| datetime    | Required, ISO 8601 format (`YYYY-MM-DDTHH:mm:ss`) |
| description | Optional, free text |
| tags        | Optional, list of tag name strings (each max 50 characters). Existing tags are reused; the event's tag associations are replaced entirely. |

**Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Updated Study Session",
  "datetime": "2026-04-01T15:00:00",
  "description": "Updated description",
  "tags": ["math", "review"],
  "createdAt": "2026-03-28T10:00:00"
}
```

**Errors:**

- `400 Bad Request` — event not found, event belongs to another user, or invalid input
- `403 Forbidden` — no JWT token provided

---

### `DELETE /api/events/{id}`

Delete an event and its tag associations. Requires authentication. Only the event's owner can delete it.

**Response (200 OK):**

```json
{ "message": "Event deleted" }
```

**Errors:**

- `400 Bad Request` — event not found or event belongs to another user
- `403 Forbidden` — no JWT token provided

---

### `POST /api/agent/sessions`

Create a new chat session. Requires authentication.

**Response (201 Created):**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Use the returned `sessionId` in subsequent `/api/agent/chat` requests.

**Errors:**

- `403 Forbidden` — no JWT token provided

---

### `POST /api/agent/chat`

Chat with the Cirno AI assistant. Requires authentication. Streams responses via Server-Sent Events (SSE).

A valid session must exist before calling this endpoint. Create one first with `POST /api/agent/sessions`.

**Request:**

```json
{
  "message": "What events do I have tomorrow?",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field     | Rules |
|-----------|-------|
| message   | Required, the user's message |
| sessionId | Required, UUID of an existing session (obtained from `POST /api/agent/sessions`) |

**Response (SSE stream):**

The response is streamed as Server-Sent Events with `Content-Type: text/event-stream`. Each event carries a JSON `data` field:

| Event type | Description |
|------------|-------------|
| `session`  | The session UUID (first event, so the frontend can store it) |
| `text`     | The LLM's response text |
| `error`    | Error message (if something went wrong) |
| `done`     | Signals the stream is complete |

**Example events:**

```
data: {"type":"session","content":"550e8400-e29b-41d4-a716-446655440000"}

data: {"type":"text","content":"You have 2 events tomorrow: ..."}

data: {"type":"done"}
```

**Errors:**

- `400 Bad Request` — missing `message` field, invalid `sessionId`, or session not found
- `403 Forbidden` — no JWT token provided

---

### `GET /api/agent/sessions`

List the authenticated user's chat sessions, ordered by most recently updated. Each session includes a preview of the last message (truncated to ~80 characters).

**Response (200 OK):**

```json
{
  "sessions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "What events do I have tomorrow?",
      "createdAt": "2026-03-28T10:00:00",
      "updatedAt": "2026-03-28T10:05:00",
      "lastMessagePreview": "You have 2 events tomorrow..."
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| id | Session UUID |
| title | Auto-generated from the first user message (truncated to 50 chars) |
| createdAt | Session creation time (ISO 8601) |
| updatedAt | Last message time (ISO 8601) |
| lastMessagePreview | Truncated content of the last message, or `null` if empty |

**Errors:**

- `403 Forbidden` — no JWT token provided

---

### `GET /api/agent/sessions/{id}`

Retrieve the full message history for a chat session. Requires authentication.

**Response (200 OK):**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    { "role": "user", "content": "What events do I have tomorrow?", "createdAt": "2026-03-28T10:00:00" },
    { "role": "assistant", "content": "You have 2 events tomorrow: ...", "createdAt": "2026-03-28T10:00:05" }
  ]
}
```

| Field | Description |
|-------|-------------|
| role | Message role: `user`, `assistant`, or `system` |
| content | Message text |
| createdAt | Timestamp (ISO 8601) |

**Errors:**

- `403 Forbidden` — no JWT token provided

---

### `DELETE /api/agent/sessions/{id}`

Delete a chat session and all its messages. Requires authentication. Only the session's owner can delete it.

**Response (200 OK):**

Empty body.

**Errors:**

- `403 Forbidden` — no JWT token provided, or session belongs to another user

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

---

### `GET /api/user/identity`

Retrieve the authenticated user's identity and goal. Requires authentication.

**Response (200 OK):**

```json
{
  "identity": "secondary school student",
  "goal": "full A* in physics"
}
```

**Response (204 No Content):**

Returned if the user has not set an identity yet.

**Errors:**

- `403 Forbidden` — no JWT token or invalid token provided

---

### `GET /api/user/identity/goal`

Retrieve only the authenticated user's goal. Requires authentication.

**Response (200 OK):**

```json
{
  "goal": "full A* in physics"
}
```

**Response (204 No Content):**

Returned if the user has not set an identity yet.

**Errors:**

- `403 Forbidden` — no JWT token or invalid token provided

---

### `POST /api/user/identity`

Create a new identity and goal for the authenticated user. Requires authentication. Fails if an identity already exists.

**Request:**

```json
{
  "identity": "secondary school student",
  "goal": "full A* in physics"
}
```

| Field    | Rules |
|----------|-------|
| identity | Required, max 255 characters |
| goal     | Required, max 255 characters |

**Response (201 Created):**

```json
{
  "identity": "secondary school student",
  "goal": "full A* in physics"
}
```

**Errors:**

- `400 Bad Request` — missing or invalid fields, or identity already exists (use PATCH to update)
- `403 Forbidden` — no JWT token or invalid token provided

---

### `PATCH /api/user/identity`

Edit the authenticated user's identity and goal. Requires authentication. Fails if no identity exists. At least one field must be provided.

**Request:**

```json
{
  "identity": "undergraduate"
}
```

| Field    | Rules |
|----------|-------|
| identity | Optional, max 255 characters |
| goal     | Optional, max 255 characters |

Omitted fields are left unchanged.

**Response (200 OK):**

```json
{
  "identity": "undergraduate",
  "goal": "full A* in physics"
}
```

**Errors:**

- `400 Bad Request` — no fields provided, invalid field values, or identity not found (use POST to create)
- `403 Forbidden` — no JWT token or invalid token provided

---

## Errorbook

Records academic errors for tracking weak areas and revision. Each error must have at least a **description** (markdown) or an **image**. Tags are shared with events (reuses the same `tags` table).

### `POST /api/errorbook`

Create a new error record. Requires authentication. Images are uploaded separately via `POST /api/errorbook/{id}/image`.

**Request:**

```json
{
  "description": "## Mistake\nForgot to apply the SUVAT equations correctly for non-uniform acceleration.\n\n**Correct approach:** use v² = u² + 2as only when a is constant.",
  "tags": ["physics", "mechanics", "suvat"],
  "date": "2026-03-29T14:00:00"
}
```

| Field       | Rules |
|-------------|-------|
| description | Optional (but at least one of description or image must be provided), markdown text, max 50000 characters |
| tags        | Optional, list of tag name strings (each trimmed, deduplicated, max 50). Reuses existing tags. |
| date        | Optional, ISO 8601 datetime. If set, links to an event on that date. |
| eventId     | Optional, UUID of an existing event to link this error to. |

**Response (201 Created):**

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440000",
  "description": "## Mistake\nForgot to apply...",
  "imagePath": null,
  "date": "2026-03-29T14:00:00",
  "eventId": null,
  "tags": ["physics", "mechanics", "suvat"],
  "createdAt": "2026-03-29T14:05:00",
  "updatedAt": "2026-03-29T14:05:00"
}
```

**Errors:**

- `400 Bad Request` — missing both description and image, invalid date format, or invalid field values
- `403 Forbidden` — no JWT token provided

---

### `GET /api/errorbook`

List the authenticated user's error records. Requires authentication. Supports optional filtering.

**Query Parameters:**

| Parameter  | Type   | Description |
|------------|--------|-------------|
| `tag`      | String | Filter by tag name (case-insensitive) |
| `dateFrom` | String | Filter errors with date >= this ISO 8601 datetime |
| `dateTo`   | String | Filter errors with date < this ISO 8601 datetime |

**Example:** `GET /api/errorbook?tag=physics&dateFrom=2026-03-01T00:00:00&dateTo=2026-04-01T00:00:00`

Returns all physics errors from March 2026.

**Response (200 OK):**

```json
{
  "errors": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440000",
      "description": "## Mistake\nForgot to apply...",
      "imagePath": "uploads/errorbook/abc123-screenshot.png",
      "date": "2026-03-29T14:00:00",
      "eventId": null,
      "tags": ["physics", "mechanics"],
      "createdAt": "2026-03-29T14:05:00",
      "updatedAt": "2026-03-29T14:05:00"
    }
  ]
}
```

**Errors:**

- `400 Bad Request` — invalid date format for query parameters
- `403 Forbidden` — no JWT token provided

---

### `GET /api/errorbook/{id}`

Retrieve a single error record by ID. Requires authentication. Only the owner can view their errors.

**Response (200 OK):**

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440000",
  "description": "## Mistake\nForgot to apply...",
  "imagePath": null,
  "date": "2026-03-29T14:00:00",
  "eventId": null,
  "tags": ["physics", "mechanics"],
  "createdAt": "2026-03-29T14:05:00",
  "updatedAt": "2026-03-29T14:05:00"
}
```

**Errors:**

- `400 Bad Request` — error record not found or belongs to another user
- `403 Forbidden` — no JWT token provided

---

### `PATCH /api/errorbook/{id}`

Partial update of an error record. Requires authentication. Only provided fields are updated; tags are fully replaced if given.

**Request:**

```json
{
  "description": "## Updated Mistake\nClarified the correct SUVAT approach.",
  "tags": ["physics", "kinematics"]
}
```

| Field       | Rules |
|-------------|-------|
| description | Optional, markdown text, max 50000 characters |
| tags        | Optional, list of tag names (replaces all existing tags) |
| date        | Optional, ISO 8601 datetime |
| eventId     | Optional, UUID of event to link to |

**Response (200 OK):**

Returns the updated error record.

**Errors:**

- `400 Bad Request` — error record not found or invalid input
- `403 Forbidden` — no JWT token provided

---

### `DELETE /api/errorbook/{id}`

Delete an error record, its tag associations, and any attached image file. Requires authentication.

**Response (200 OK):**

```json
{ "message": "Error record deleted" }
```

**Errors:**

- `400 Bad Request` — error record not found or belongs to another user
- `403 Forbidden` — no JWT token provided

---

### `POST /api/errorbook/{id}/image`

Upload an image for an existing error record. Requires authentication. Replaces any previously uploaded image.

**Request:**

`Content-Type: multipart/form-data` with a `file` field containing the image.

| Field  | Rules |
|--------|-------|
| file   | Required, must be an image (`image/*` content type) |

**Response (201 Created):**

```json
{
  "message": "Image uploaded",
  "path": "uploads/errorbook/550e8400-screenshot.png"
}
```

Images are stored locally in `uploads/errorbook/` with a UUID-prefixed filename to avoid collisions.

**Errors:**

- `400 Bad Request` — error record not found, file is empty, or file is not an image
- `403 Forbidden` — no JWT token provided

---

### `GET /api/errorbook/{id}/image`

Retrieve the image attached to an error record. Requires authentication. Returns the raw image bytes with the appropriate `Content-Type`.

**Response (200 OK):**

Binary image data with `Content-Type: image/png` (or the detected type).

**Errors:**

- `400 Bad Request` — error record not found
- `403 Forbidden` — no JWT token provided
- `404 Not Found` — no image attached, or image file missing from disk

---

### AI Tools

The Cirno AI assistant can manage error records through conversation. Available tools:

| Tool | Description |
|------|-------------|
| `createErrorRecord` | Record a new error with markdown description, optional tags, and optional date |
| `updateErrorRecord` | Update an existing error's description, tags, or date |
| `deleteErrorRecord` | Delete an error record by ID |

Recent errors are embedded in the system prompt so Cirno can reference them when scheduling revision or identifying weak areas.
