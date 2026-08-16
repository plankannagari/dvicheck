# DviCheck — Claude Code Context

## What this app does
Smart spending tracker. Users scan grocery/utility bills via camera OCR,
get AI-powered suggestions on avoidable purchases, and get duplicate
alerts before buying items they already have (shopping list check).

## Tech stack
- Backend: Spring Boot 3.5, Java 21, Maven, PostgreSQL 16, Flyway
- Mobile: React Native + Expo (iOS + Android), Zustand, Axios
- AI: Anthropic Claude API (`claude-sonnet-5` — `claude-sonnet-4-20250514` was retired by Anthropic; switched Day 15 after hitting a 404 `not_found_error`)
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

## Backend patterns (established Day 3)
- Money fields always use `BigDecimal` (never `double` or `float`)
- Dates use `LocalDate`, timestamps use `Instant` — never mix them
- Bill type controlled by `BillType` enum (GROCERY, UTILITY, OTHER)
- Item category controlled by `ItemCategory` enum (ESSENTIAL, REDUCIBLE, AVOIDABLE, DUPLICATE)
- Shopping list status controlled by `ListStatus` enum (DRAFT, ACTIVE, COMPLETED)
- `PantryMemory.normalise(name)` used whenever storing item names for matching
- All `@OneToMany` relationships use `FetchType.LAZY` to avoid N+1 queries

## Entity relationships
```
User → (1:many) → Bills → (1:many) → LineItems
User → (1:many) → ShoppingLists → (1:many) → ShoppingItems
User → (1:many) → PantryMemory
```

## Backend patterns (established Day 4)
- Auth endpoints are at `/api/auth/**` (`POST /api/auth/send-otp`, `POST /api/auth/verify-otp` — public, no JWT needed; explicitly `permitAll()` in `SecurityConfig`)
- All `/api/**` routes (except `/api/health`) require a valid JWT in the `Authorization` header
- Get current user ID in any controller: `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`
- Cast principal to `String`, then `UUID.fromString(principal)` to get userId
- Dev OTP is always `123456` — replace `AuthService` with real Supabase before production
- JWT expiry: 1 hour access token, 30 days refresh token

## Mobile patterns (established Day 5)
- All API calls go through `src/api/apiClient.js` (axios with JWT interceptor)
- Auth API functions in `src/api/authApi.js`
- Global state managed by Zustand stores in `src/store/`
- Tokens stored securely via `expo-secure-store`
- Navigation: `NavigationContainer` → `Stack.Navigator` in `App.js`
- Screen files in `src/screens/`
- Constants (COLORS, API_BASE_URL) in `src/constants/index.js`
- Use `navigation.replace()` to prevent back navigation after auth
- `KeyboardAvoidingView` wraps all screens with text inputs

## Mobile patterns (established Day 6)
- Bottom tab navigator in `src/navigation/MainTabNavigator.js`
- Tab screens: Home, Scan, Lists, Insights, History
- Reusable components in `src/components/` (Toast, ErrorBoundary)
- Custom hooks live in `src/hooks/` — currently empty; toast state moved to `useToastStore` (Zustand) on Day 9
- Mock data in screens until API wired — label clearly as `MOCK_DATA`
- `ScrollView` with `SafeAreaView` on all full-page screens
- `navigation.navigate('Scan')` to jump to a specific tab from any screen

## Backend patterns (established Day 7)
- `HomeController` pattern: get userId via a private `currentUserId()` helper reading `SecurityContextHolder`
- All dashboard endpoints live under `/api/home/**`

## Mobile patterns (established Day 7)
- Zustand stores: a `loadX()` action fetches data, `isLoading`/`error` state managed inside the store
- Use `Promise.all` for parallel fetches when a screen needs multiple endpoints
- Show loading skeletons when `isLoading && data === null` (first load only)
- Tie `RefreshControl.refreshing` to `isLoading`
- Always provide empty states — never blank screens

## Backend patterns (established Day 8)
- `BillScanController`: `POST /api/bills/scan` accepts `multipart/form-data` (image file + optional `storeName`/`billType`/`purchaseDate`), persists the `Bill` + `LineItem`s directly (no separate confirm/edit step)
- `OcrService` calls the Vision REST API via Spring's `RestClient` — this resolves to the JDK's built-in `HttpClient` under the hood (Spring's `RestClient` auto-detection only checks Apache HttpComponents → Jetty → JDK, never OkHttp) — the `okhttp` dependency in `pom.xml` was unused as of Day 8; `NotificationService` (Day 13) is the first thing that actually uses it directly
- Vision API key: `dvicheck.google.vision.api-key`, sourced from `GOOGLE_VISION_API_KEY` env var — never hardcoded in `application.yml`; in production, set it as a Railway env var
- `ReceiptParser` is pure regex/heuristic parsing (store name, date, total, line items, BillType inference) — a placeholder until an AI parsing engine replaces/augments it on Day 15; all parsed `LineItem`s default to `ESSENTIAL`
- New `DvicheckException.serviceUnavailable()` factory → `SERVICE_UNAVAILABLE` errorCode → HTTP 503, for upstream OCR failures (distinct from `badRequest` for unreadable images)

## Mobile patterns (established Day 8)
- Camera: `CameraView` + `useCameraPermissions` from `expo-camera` (config plugin in `app.json`, not the legacy `Camera` component)
- `ScanScreen` phases (`camera` / `processing` / `result`) are local component state — there is no `scanStore`
- `scanApi.uploadReceiptImage(uri, options)` compresses the photo to 1200px wide / 80% JPEG via `expo-image-manipulator`, then uploads as `multipart/form-data`
- Toasts on `ScanScreen` come from the shared `useToastStore` (Zustand) — there is no `hooks/useToast.js`

## Backend patterns (established Day 9)
- Shopping endpoints at `/api/shopping/lists/**` (`ShoppingListController` / `ShoppingListService`)
- Duplicate detection: normalise the item name via `PantryMemory.normalise()`, look up in `pantry_memory` via `PantryMemoryRepository.findByUserIdAndNormalisedName`, flag as a duplicate if `last_bought_date` is within the last 10 days (inclusive)
- `ShoppingListService.checkForDuplicate()` returns a private `DuplicateCheckResult` record (`isDuplicate`, `warning`, `lastPurchasedDate`)
- `pantry_recent_purchases` DB view (`V4__add_pantry_view.sql`) precomputes a HIGH/MEDIUM/LOW `duplicate_risk` tier for future risk-level queries — not currently queried by `ShoppingListService` itself
- `toggleItem`/`deleteItem`/`addItem` all verify the list belongs to the requesting user (`findByIdAndUserId`) before touching an item — never trust a matching `listId`/`itemId` pair alone

## Mobile patterns (established Day 9)
- `shoppingStore.addItem()` returns the newly created item — screens check `item.isDuplicate` on the return value to decide whether to show a toast, rather than the store surfacing it itself
- `ListsScreen` uses conditional rendering keyed off `activeList` (index vs. detail mode) in a single screen — no navigation push for list detail
- Long press on a shopping item row reveals an inline "Delete" button for that row (no `Alert.alert()` confirmation)

## Backend patterns (established Day 10)
- `PantryService.updateFromBill()` is called after every scan (from `BillScanController`, right after `billRepository.save()`) — the whole method is wrapped in one try/catch logged at `WARN`, so a pantry-update failure can never block or roll back the bill save
- Pantry memory tracks purchase frequency (`purchaseCount`, `lastBoughtDate`) per normalised item name, but `estimatedRemainingDays` is currently a flat `DEFAULT_ESTIMATED_REMAINING_DAYS = 7` placeholder constant — not yet a computed heuristic
- Bill history: `BillHistoryController` — `GET /api/bills` (paginated via `page`/`size`, reuses `RecentBillDto`) and `GET /api/bills/{id}` (full `BillDetailResponse` with line items), both scoped to the requesting user; coexists with `BillScanController` on the same `/api/bills` base path without route conflicts (`POST /scan` vs. `GET /` / `GET /{id}`)
- `GlobalExceptionHandler` also maps `MissingServletRequestPartException` → 400 (previously fell through to the generic 500 handler)

## Mobile patterns (established Day 10)
- `HistoryScreen` uses the same index/detail conditional-render pattern as `ListsScreen` (keyed off `activeBill`, no navigation push)
- `historyStore` paginates via `page`/`hasMore`; `loadBills(refresh)` resets to page 0 when `refresh` is true, otherwise appends
- After a successful scan save, `ScanScreen`'s "Save and Done" fires `loadDashboard()` and `loadBills(true)` (fire-and-forget, no `await`) before navigating, so `HomeScreen` and `HistoryScreen` both show fresh data immediately

## End-to-end flow (as of Day 10)
```
Scan receipt → OCR → Parse → Save Bill → Update Pantry Memory
  → Duplicate detection available for shopping lists
  → History screen shows the bill
  → Home screen stats refresh
```

## Backend patterns (established Day 11)
- Insights endpoint: `GET /api/insights/weekly` (`InsightsController` / `InsightsService`)
- `InsightsService` aggregates current week vs. previous week (Monday-to-today vs. the prior full Monday–Sunday)
- `sumTotalBetween` query added to `BillRepository`
- `LineItemRepository.findByBillUserIdAndBillPurchaseDateBetween` for item-level queries

## Mobile patterns (established Day 11)
- Manual item entry in `ScanScreen`'s result phase: local state `manualItems[]` only — TODO Day 15 to persist to backend via a PATCH endpoint
- `vsLastWeekPercent`: negative = spending down (green), positive = up (red)

## Phase 2 status
- Core loop: COMPLETE (scan → save → history → home refresh)
- Shopping lists: COMPLETE (create, add items, duplicate detection)
- Weekly insights: COMPLETE (real data, category breakdown)
- AI suggestions: PENDING Day 15
- Manual receipt entry: PARTIAL (display only — backend persistence Day 15)

## Backend patterns (established Day 12)
- User preferences: `GET /api/users/me`, `PATCH /api/users/me/preferences`
- Partial update pattern: only non-null fields on the request are applied in `UserService.updatePreferences()` — omitted fields keep their existing value
- `household_size` stored on the `users` table — intended for the AI engine's pantry estimates on Day 15
- `currency` stored as a 3-letter ISO code, always uppercased server-side before saving

## Mobile patterns (established Day 12)
- `ProfileScreen` is accessed via the gear icon on `HomeScreen` — a stack push (registered as a sibling of `PhoneEntry`/`MainApp` in the root `Stack.Navigator` in `App.js`), not a tab
- isDirty pattern: local preference state is compared against the loaded `profile` object; the header's "Save" button only renders when they differ
- Sign out: `authStore.clearAuth()` + `profileStore.clearProfile()`, then `navigation.reset()` to the `PhoneEntry` route

## Backend patterns (established Day 13)
- Push token: `users.push_token` (`V6__add_push_token.sql`), `POST /api/users/me/push-token` (`PushTokenRequest` → `UserService.savePushToken()`)
- `NotificationService.sendNotification(pushToken, title, body)` calls the Expo Push API (`https://exp.host/--/api/v2/push/send`) directly via `okhttp3.OkHttpClient` + the injected `ObjectMapper` bean — skips silently (`log.debug`) on a null/blank token, and the whole call is wrapped in try/catch logged at `WARN`, so a notification failure can never throw into calling code
- `NotificationService.sendWeeklyInsightsReminder()` — `@Scheduled(cron = "0 0 19 * * SUN")`, queries `UserRepository.findByPushTokenIsNotNullAndNotificationsEnabledTrue()`; `@EnableScheduling` is on `BackendApplication`
- Code review pass (Days 9-12): only one fix applied — `BillHistoryController` now has class-level `@Transactional(readOnly = true)` (it talks to `BillRepository` directly instead of through a service, so it had no transaction boundary of its own for its lazy `lineItems`/`user` access; this previously only worked because `spring.jpa.open-in-view` defaults to `true` and was never set explicitly). Remaining findings left as tech debt — see below.

## Mobile patterns (established Day 13)
- `src/utils/notifications.js` → `registerForPushToken()`: requests permission (`expo-notifications`), no-ops on simulators (`expo-device`'s `Device.isDevice` check), gets the Expo push token, POSTs it to the backend — every step is defensive (never throws, only `console.log`s)
- `authStore.setAuth()` calls `registerForPushToken()` fire-and-forget (no `await`) right after the `SecureStore` writes complete
- Requires `expo-notifications` + `expo-device` (`npx expo install expo-notifications expo-device`) — not yet confirmed installed as of Day 13; the app won't bundle until that's run

## AI Pipeline (Day 15 updated)

Flow:
```
Google Vision OCR -> raw text
GeminiReceiptParser.parse(rawText) -> List<ParsedLineItem>  [FREE]
  Fallback: ReceiptParser heuristic if Gemini fails
ClaudeItemAnalyser.analyseItems(items, householdSize) -> categories  [~$0.003/scan]
  OR GeminiItemAnalyser if app.ai.use-gemini-analyser=true  [FREE]
LineItem saved with category, suggestion, savingEstimate
Bill.avoidableAmount = sum AVOIDABLE + REDUCIBLE items
```

Key fixes applied Day 15:
- `temperature: 0` set on **Gemini calls only**, for consistent extraction/categorisation results — Claude's Messages API (`claude-sonnet-5`) rejects `temperature` entirely (`400 "temperature is deprecated for this model"`), even at `0`, so Claude calls in `ClaudeReceiptParser`/`ClaudeItemAnalyser` deliberately omit it
- Currency codes (e.g. `"USD"`) excluded from extracted line items via a post-parse filter (`isLikelyNotAnItem()` — currency-code regex + non-item keyword list), not just a prompt instruction
- Fuzzy pantry matching for duplicate detection: exact normalised-name match first, then substring containment in either direction as a fallback (e.g. `'Milk'` matches `'Milk 2%'`) — see `ShoppingListService.findSubstringMatch()`
- `GeminiReceiptParser` is now the primary line-item extractor; its own fallback is the original heuristic `ReceiptParser`, not Claude. `ClaudeReceiptParser` (the original Day 15 plan below) still exists and compiles but is **no longer wired into `BillScanController`** — orphaned/reference code only
- `GeminiItemAnalyser` added as a drop-in swap for `ClaudeItemAnalyser` (same `ClaudeItemAnalyser.ItemAnalysis` record), toggled via `app.ai.use-gemini-analyser` in `application.yml` — switch providers with no code change

Cost at 100K scans:
- Gemini extraction (free tier): $0
- Claude categorisation: ~$780/month
- OR full Gemini (extraction + categorisation): ~$30/month total
- Google Vision: $150/month

- Gemini API key: `app.google.gemini.api-key` in yml, env var `GEMINI_API_KEY`
- Claude API key: `app.anthropic.api-key` in yml, env var `ANTHROPIC_API_KEY`

### Gemini config (fixed Day 16)
- Correct model name: **`gemini-flash-latest`** (not `gemini-1.5-flash`, not `gemini-1.5-flash-latest` — both are hard 404s, fully removed from the API surface; verified directly via curl). `gemini-2.5-flash`/`gemini-2.5-flash-lite` still show up in `ListModels` but `generateContent` rejects them with `404 "no longer available to new users"`
- Key source: `aistudio.google.com/apikey` (not Google Cloud Console)
- API to enable: Generative Language API, in Google Cloud Console
- **Actual root cause of the Day 15/16 Gemini failures: depleted prepayment credits** on the AI Studio key's project — `429 RESOURCE_EXHAUSTED "Your prepayment credits are depleted"`. The key was valid, the API was enabled, and there were no IP restrictions the whole time; every `generateContent` call failed at the billing layer, not auth/config. Fixed by adding prepayment credits at `ai.studio/projects`
- Config key: `app.google.gemini.api-key`
- Production env var: `GEMINI_API_KEY`

### Original Day 15 plan (Claude-only parser, superseded above)

**Problem:** `ReceiptParser`'s heuristic fails whenever Google Vision's OCR splits an item's name and price across separate lines (confirmed real-world occurrence, not just a synthetic-image artifact — see Day 8/Day 10 notes above).

**Solution:** replace/augment it with a Claude API call that understands receipt context well enough to survive that line-splitting.

**New class:** `ClaudeReceiptParser.java` in `service/`
- `parse(String rawOcrText) -> List<ParsedLineItem>`
- Fallback path: `receiptParser.parse(rawOcrText).items()`
- Still exists, still compiles, but superseded by `GeminiReceiptParser` above — kept only as reference/backup code, not called from `BillScanController`

**Expected Claude response shape** (used in the prompt):
```json
[
  {"name": "Milk 2L", "quantity": 1, "unitPrice": 2.20, "totalPrice": 2.20},
  {"name": "Bread", "quantity": 1, "unitPrice": 5.50, "totalPrice": 5.50}
]
```

## Scan result UI patterns (established Day 16)
- `BillScanResponse.LineItemResult` now includes `suggestion` (`String`) and `savingEstimate` (`BigDecimal`), populated from `LineItem.getSuggestion()`/`getSavingEstimate()` in `BillScanController.toResponse()` — previously only `id`/`name`/`unitPrice`/`totalPrice`/`category` were returned, so the AI-generated suggestion/saving data was computed and saved but never reached the client
- `BillDetailResponse.LineItemDetail` already had both fields (plus `quantity`/`confidence`) — no change needed there
- `ScanScreen`'s result phase (`ItemRow` component): suggestion text (italic, 12px, `COLORS.inkLight`) shown below the item name, only for `REDUCIBLE`/`AVOIDABLE` categories; a per-item "Save $X.XX" pill (`COLORS.greenLight` bg / `COLORS.green` text) shows next to the price whenever `savingEstimate > 0`, with no category restriction; a colored uppercase category label (`REDUCIBLE`/`AVOIDABLE`/`DUPLICATE`) sits next to the dot — `ESSENTIAL` shows just the dot
- `HistoryScreen`'s detail view mirrors the same suggestion-text rule (11px here, same `REDUCIBLE`/`AVOIDABLE`-only gating) and a right-aligned green "Save $X.XX" line below the price
- Savings summary card: `ScanScreen` replaced its old small header badge with a dedicated card showing the total avoidable+reducible amount prominently (large green `$X.XX`, "potential savings this shop" label, "X avoidable or reducible items" subtext), shown only when that total is `> 0`; `HistoryScreen`'s detail view kept its existing badge as-is and added an "X items flagged" line below it (same `REDUCIBLE + AVOIDABLE` count)
- Client-side `avoidableAmount` in `ScanScreen` sums `AVOIDABLE` **and** `REDUCIBLE` totals (previously `AVOIDABLE` only) — the scan response has no server-computed `avoidableAmount` field (unlike `BillDetailResponse`, which does), so this stays a client-side calc for now

## Migrations
- `V1`–`V6` exist (`V6` = `push_token`). Next free version is **`V7`** — no migration was needed for Day 16 (response-DTO-only change, no schema change).

## Tech debt (from Day 9-12 code review, Day 13)
Only the `BillHistoryController` transaction issue was fixed (see Day 13 backend patterns above). Everything else below was deliberately left as-is:
- `AddItemRequest.quantityOrDefault()` is dead code — `ShoppingListService.addItem()` re-implements the same "default to `1`" rule inline instead of calling it; the two could drift
- `PantryMemoryRepository.findByUserIdOrderByLastBoughtDateDesc()` is declared but never called anywhere
- `currentUserId()` is copy-pasted into 5 controllers (`ShoppingListController`, `BillHistoryController`, `InsightsController`, `UserController`, `BillScanController`) — behaviorally identical today, but drift-prone
- `AddItemRequest.quantity` has no length/format validation, unlike `name`'s `@NotBlank` — an oversized value hits Postgres's `VARCHAR(50)` constraint as a raw 500 instead of a clean 400
- `manualItems` in `ScanScreen`'s result phase are still local-state-only (tracked separately above, resurfaced here since it's still open)

## Key files
- mobile/src/constants/index.js — colours, API URL, limits
- mobile/src/api/apiClient.js — Axios instance with JWT interceptor
- mobile/src/store/authStore.js — Zustand auth state (user, tokens, isAuthenticated)
- backend/src/main/resources/application.yml — DB + app config
- backend/src/main/resources/db/migration/ — Flyway SQL files
- backend/src/main/java/.../exception/DvicheckException.java — base exception + factories
- backend/src/main/java/.../exception/GlobalExceptionHandler.java — @RestControllerAdvice
- backend/src/main/java/.../dto/ApiResponse.java — universal response wrapper

## Current phase
✅ Day 1 complete — scaffold, health endpoint, Expo running
✅ Day 2 complete — DvicheckException, GlobalExceptionHandler, ApiResponse, User entity/repo/service
✅ Day 3 complete — Full DB schema V2, all JPA entities
✅ Day 4 complete — JWT auth, OTP flow, AuthController, Spring Security configured
✅ Day 5 complete — React Native auth screens, navigation stack, token storage
✅ Day 6 complete — Bottom tab navigator, HomeScreen dashboard, Toast, ErrorBoundary
✅ Day 7 complete — HomeScreen wired to real API, BillService, HomeController, homeStore
✅ Day 8 complete — Camera UI, Google Vision OCR, ReceiptParser, BillScanController, ScanScreen
✅ Day 9 complete — Shopping lists backend + mobile UI, duplicate detection via pantry memory
✅ Day 10 complete — PantryService auto-update, BillHistoryController, HistoryScreen, scan save refreshes HomeScreen
✅ Day 11 complete — InsightsService, InsightsScreen wired to real data, manual item entry on scan result
✅ Day 12 complete — User preferences (household size, currency), ProfileScreen, gear icon navigation
✅ Day 13 complete — push notifications, V6 migration, code review pass
🔄 Day 14 — buffer day (rest, catch up, test on physical device)
✅ Day 15 complete — Gemini Flash (free) for extraction, Claude for categorisation, fuzzy duplicate detection, temperature 0 for consistency
✅ Day 16 complete — Gemini fixed (root cause was depleted prepayment credits on the AI Studio key, not the model name or key source; model corrected to `gemini-flash-latest`), scan result UI shows suggestions and saving estimates, `BillScanResponse` includes `suggestion`/`savingEstimate`
🔄 Day 17 next — AI-powered weekly insights narrative, spending pattern analysis

## Do NOT change
- application.yml datasource section
- SecurityConfig.java until Day 4
- Any committed Flyway migration files (never edit, only add new ones)
- COLORS object in constants/index.js
