# DviCheck — Claude Code Context

## What this app does
Smart spending tracker. Users scan grocery/utility bills via camera OCR,
get AI-powered suggestions on avoidable purchases, and get duplicate
alerts before buying items they already have (shopping list check).

## Tech stack
- Backend: Spring Boot 3.5, Java 21, Maven, PostgreSQL 16, Flyway
- Mobile: React Native + Expo (iOS + Android), Zustand, Axios
- AI: Anthropic Claude API (claude-sonnet-4-20250514)
- OCR: Google Cloud Vision API
- Auth: Supabase (phone OTP + JWT)
- Payments: RevenueCat
- Hosting: Railway

## Monorepo structure
~/dvicheck/
├── backend/     ← Spring Boot REST API (port 8080)
├── mobile/      ← React Native Expo app
├── docs/        ← Architecture notes
└── CLAUDE.md    ← This file

## Package structure (backend)
com.dvicheck.backend
├── controller/   ← REST only, no business logic
├── service/      ← All business logic here
├── repository/   ← JPA repositories only
├── model/        ← JPA entities (UUID PKs, Instant timestamps)
├── dto/          ← Records for request/response
├── config/       ← Spring config classes
└── exception/    ← Custom exceptions + GlobalExceptionHandler

## Code conventions
- Java: camelCase methods, PascalCase classes
- DTOs are Java records not classes
- Controllers return ResponseEntity<T> always
- Services throw custom exceptions (DvicheckException factory methods)
- React Native: functional components only, hooks only
- All colours from src/constants/index.js COLORS object
- All API calls go through src/api/apiClient.js

## Backend patterns (established Day 2)

### Exception handling
- All domain exceptions extend `DvicheckException(String errorCode, String message)`
- Use static factories: `DvicheckException.notFound("User")`, `.unauthorized()`, `.badRequest("msg")`
- `GlobalExceptionHandler` maps errorCode → HTTP status: NOT_FOUND→404, UNAUTHORIZED→401, BAD_REQUEST→400
- Never throw raw `RuntimeException` from service layer

### Response shape
Every endpoint returns `ResponseEntity<ApiResponse<T>>`:
```
{ "success": true,  "message": null, "data": { ... } }   // success
{ "success": false, "message": "User not found", "data": null }  // error
```
- Success: `ApiResponse.ok(data)` or `ApiResponse.ok(message, data)`
- Error: `ApiResponse.error(message)` — returned automatically by GlobalExceptionHandler

### Entity conventions
- UUID primary keys via `@GeneratedValue(strategy = GenerationType.UUID)`
- Timestamps always `Instant`, never `LocalDateTime`
- `@PrePersist` sets both `createdAt` + `updatedAt`; `@PreUpdate` sets only `updatedAt`
- Lombok: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` on every entity

### Service conventions
- `@Transactional` on writes, `@Transactional(readOnly = true)` on reads
- Services return entities; controllers map to response DTOs
- `@RequiredArgsConstructor` + `final` fields for injection (no `@Autowired`)

### Controller conventions
- `@Valid @RequestBody` on every mutating endpoint
- Phone numbers validated as E.164 format (`^\\+[1-9]\\d{7,19}$`)
- Entity → DTO mapping done in a private `toResponse()` method in the controller

## Key files
- mobile/src/constants/index.js — colours, API URL, limits
- mobile/src/api/apiClient.js — Axios instance with JWT interceptor
- mobile/src/store/useStore.js — Zustand global state
- backend/src/main/resources/application.yml — DB + app config
- backend/src/main/resources/db/migration/ — Flyway SQL files
- backend/src/main/java/.../exception/DvicheckException.java — base exception + factories
- backend/src/main/java/.../exception/GlobalExceptionHandler.java — @RestControllerAdvice
- backend/src/main/java/.../dto/ApiResponse.java — universal response wrapper

## Current day
Day 2 complete. Backend scaffold done: User entity, UserRepository, UserService, UserController,
DvicheckException, GlobalExceptionHandler, ApiResponse<T>.
Starting Day 3 — Bill domain (BillEntity, OCR upload endpoint, Claude AI analysis).

## Do NOT change
- application.yml datasource section
- SecurityConfig.java until Day 4
- Any committed Flyway migration files (never edit, only add new ones)
- COLORS object in constants/index.js
