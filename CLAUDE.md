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

## AI-powered weekly narrative (established Day 17)
- `SpendingNarrativeService.generateNarrative(ctx, userId, weekStart)` — calls Gemini (`app.google.gemini.model`) to write a personalised 2-3 sentence weekly spending coach message, same OkHttp/`ObjectMapper` pattern as `GeminiReceiptParser`/`GeminiItemAnalyser`
- Gemini `temperature: 0` for the narrative call, same as extraction/categorisation — deliberately deterministic, not creative; no `responseMimeType` is set (unlike the JSON-extraction calls) since the output is free-form prose, not structured data
- `NarrativeContext` record (nested in `SpendingNarrativeService`): `totalSpent`, `avoidableSpend`, `prevWeekTotal`, `vsLastWeekPercent`, `billsScanned`, `householdSize`, `topCategory`, `topItemNames`
- In-memory cache: `ConcurrentHashMap<String, String>` keyed by `userId + "_" + weekStart` — narrative is generated once per user per week, not on every `GET /api/insights/weekly` call. Cache clears on backend restart (no Redis yet); `SpendingNarrativeService.clearCache()` exists for manual invalidation. Note: a fallback narrative from a failed Gemini call is cached too, same as a real one — a transient Gemini failure locks that user into the fallback for the rest of the week until restart or manual clear
- Fallback on any failure (non-2xx response, empty text, exception): `"Your weekly spending summary is ready. Check your top items below."`
- `WeeklyInsightDto` now carries both `pattern` (the short pre-existing hardcoded one-liner from `determinePattern()`, unchanged) and `narrative` (the longer AI-generated one) as separate fields
- `InsightsService.getWeeklyInsights()` also injects `UserRepository` (to read `householdSize` for the narrative context, defaulting to `1`) alongside the new `SpendingNarrativeService`
- `InsightsScreen`: the old amber "pattern" card is now the fallback branch — shown only when `insights.narrative` is null/blank. Otherwise a "Spending Coach" card renders instead (🧠 icon + uppercase label, narrative text at 14px/`lineHeight: 22`, a divider, then `insights.pattern` as a smaller italic footer line) — `COLORS.card` background, `borderLeftWidth: 3` in `COLORS.accent`, subtle shadow/elevation

## Error handling & loading states (established Day 18)
- Backend: `ApiErrorResponse { success:false, code, message, timestamp }` is the single shape returned for every 4xx/5xx from `GlobalExceptionHandler` — `DvicheckException` (Day 2, unchanged — `.notFound()`/`.unauthorized()`/`.badRequest()`/`.serviceUnavailable()` still the pattern for all "not found"/domain errors, `code` = its `errorCode`), `ExternalApiException` (new — 502/`EXTERNAL_AI_ERROR`, for external API failures with no fallback path; nothing throws it yet), `MethodArgumentNotValidException` (400/`VALIDATION_ERROR`, first field error only — was previously all field errors joined), `MissingServletRequestPartException` (Day 10 fix, unchanged, 400/`VALIDATION_ERROR`), `AccessDeniedException` (Spring Security's built-in class, 403/`FORBIDDEN`), catch-all `Exception` (500/`INTERNAL_ERROR`, generic message, `log.error` with full stack trace server-side only — never leaked to the client). A `ResourceNotFoundException` was created and wired in, then deliberately deleted — explicit decision to keep `DvicheckException.notFound(...)` as the only "not found" pattern, no parallel type
- Mobile: `apiClient.js`'s existing response interceptor (401 → clears tokens, predates today) now also attaches `error.appError = { code, message, isNetworkError }` to every failed request before re-rejecting — `error.response` present → reads `data?.code`/`data?.message` from the backend's `ApiErrorResponse`; `error.request` with no response → `NETWORK_ERROR`; neither → `CLIENT_ERROR`. Screens/stores read `err.appError?.message` instead of writing their own error-parsing
- Mobile: `homeStore`, `insightsStore`, `shoppingStore` (`loadLists`/`selectList`) catch blocks now set `error: error.appError?.message || 'Something went wrong.'` instead of a store-specific hardcoded string
- Mobile: `EmptyState` (`src/components/EmptyState.jsx`) and `SkeletonCard`/`SkeletonList` (`src/components/SkeletonCard.jsx`/`SkeletonList.jsx`) are the shared components for loading/empty states — reuse them, don't build screen-specific versions. Note the `.jsx` extension is new for this directory (`Toast.js`/`ErrorBoundary.js` are still plain `.js`) — an intentional deviation for these three files only, not a repo-wide rename
- Mobile: no `src/theme` module exists (despite being referenced when these components were speced) — `COLORS` from `src/constants/index.js` is still the actual, only color source; no shared spacing constants exist either, screens just use literal padding/margin numbers
- Mobile render pattern per data screen: `isFirstLoad` (`isLoading && data === null`, or `isLoading && data.length === 0` for array-backed stores) → `<SkeletonList count={3} />`; else `error` → centered message + "Retry" button calling the same `loadX()` (which already resets `error` to `null` at the start, so no separate "clear error" step is needed); else empty-data check → `<EmptyState icon title subtitle actionLabel? onAction?} />`; else → real content, unchanged. Deliberately **not** a strict top-level `if (loading)` — pull-to-refresh of already-loaded content only shows the `RefreshControl` spinner, not a full-screen skeleton takeover, matching the pre-existing Day 7 convention ("skeletons on first load only")
- Wired into: `HomeScreen` (empty = `recentBills.length === 0`; whole hero/stats/suggestion/bills block is now one exclusive branch, replacing the old per-section skeletons — previously the hero/stats cards still rendered even with zero bills, now the full-screen `EmptyState` replaces everything when there's no data at all), `InsightsScreen` (empty = `totalSpent === 0`, no action button), `ListsScreen`'s **detail view only** (empty = `items.length === 0`, "Add item" action focuses the existing add-item input via ref) — the index view ("no lists yet") was intentionally left untouched, since the given empty-state copy ("List is empty" / "Add an item") only matches the detail view, not "no lists exist"
- **`ShoppingListScreen` didn't exist as of Day 18** (the real shopping screen is `ListsScreen.js`, Day 9 naming) — still true. **`PantryScreen` didn't exist as of Day 18 either, but was created Day 19** — see below; that gap is now closed.

## Pantry screen + History search (established Day 19)
- `PantryController` — `GET /api/pantry` → `ApiResponse<List<PantryItemDto>>`, same `currentUserId()`/`SecurityContextHolder` pattern as every other controller. `PantryService.getPantryItems(userId)` queries `PantryMemoryRepository.findByUserIdOrderByLastBoughtDateDesc` (previously dead code since Day 9-12, now actually used) and maps each `PantryMemory` to `PantryItemDto` **inside the service** — a deliberate deviation from the Day 2 convention ("services return entities; controllers map to response DTOs"), done because the task explicitly specified it this way
- `PantryItemDto`: `id`, `itemName`, `normalisedName`, `lastBoughtDate`, `purchaseCount`, `typicalQuantity`, `estimatedRemainingDays`, and a derived `depletionStatus` (`LOW` ≤2 days, `MEDIUM` ≤5 days, `OK` otherwise) — computed from the still-flat `estimatedRemainingDays` placeholder (Day 10's known tech debt, unchanged), so in practice every item shows `OK` today until that heuristic becomes real
- Mobile: `pantryApi.js` (`fetchPantry()`) + `pantryStore.js` (`items`/`isLoading`/`error`, `loadPantry()`/`clearPantry()`) follow the exact `billApi`/`historyStore` shape; `PantryScreen.js` follows the Day 18 render pattern (first-load-only skeleton → error+retry → `EmptyState` → content) with a `FlatList`, colored status dot + depletion badge (red/amber/green for LOW/MEDIUM/OK) per row, and a `daysAgo()` helper (`Today` / `1 day ago` / `X days ago`, both dates normalised to local midnight before diffing)
- History search: `BillRepository.findByUserIdAndStoreNameContainingIgnoreCase(userId, search, pageable) -> Page<Bill>`, explicit `@Query` using `LOWER(b.storeName) LIKE LOWER(CONCAT('%', :search, '%'))` (not a derived method, even though Spring Data could auto-generate this). `BillHistoryController.getBills()` takes an optional `search` param — blank/null uses the existing `findRecentByUserId` path, non-blank uses the new query; `GET /api/bills/{billId}` untouched
- Mobile: `fetchBills(page, size, search='')` only appends `&search=` when non-empty; `HistoryScreen`'s debounce is a 400ms `setTimeout` inside a `useEffect` keyed on `[search]` calling `loadBills(true, search)` — note this also refires on mount (harmless extra fetch, implemented exactly as specified rather than adding an unrequested first-render guard). `historyStore.loadBills(refresh, search)` now also tracks `search` in store state so `loadBills(false)` (pagination via `onEndReached`, which never passes a search arg) reuses the currently active search term instead of silently reverting to unfiltered results mid-scroll. `historyStore`'s error handling was also brought in line with the Day 18 `error.appError?.message` convention (it had been missed then, since `HistoryScreen` wasn't one of that day's four target screens)
- `HistoryScreen`'s index-view empty state now branches through `EmptyState` for both cases: non-blank search + zero results → 🔍/"No results"/`No bills matching "search"`; blank search + zero bills → 🧾/"No bills yet"/"Scan your first receipt!" (the second case previously used its own inline markup — consolidated onto the shared component while touching this exact spot). Detail view (bill detail) untouched
- **Tab bar changed**: there were already 5 tabs (Home, Scan, Lists, Insights, History) before adding Pantry, so — per the task's own "if already crowded" branch — `Insights` was removed from the tab bar and replaced by `Pantry` in the same slot, rather than inserted as a 6th tab. Actual tab order is now **Home | Scan | Lists | Pantry | History**. `InsightsScreen` moved to a root-level `Stack.Screen` in `App.js` (sibling of `Profile`, same pattern), reachable via a new "📈 View Insights →" button on `HomeScreen` (`navigation.navigate('Insights')`, bubbles up to the root stack exactly like the existing `navigate('Profile')` call)
- A stale `pantry_memory` row (`item_name = 'USD'`, predating Day 15's currency-code extraction filter) was manually deleted from the dev DB — not a code fix, just cleanup of old test data

## Monthly report + budget goal (established Day 20)
- `MonthlyReportController` — `GET /api/reports/monthly` → `ApiResponse<MonthlyReportDto>`, same `currentUserId()` pattern as every other controller. `MonthlyReportService.getMonthlyReport(userId)`: current month = `today.withDayOfMonth(1)` to `today`; previous month = `monthStart.minusMonths(1)` to `monthStart.minusDays(1)`
- `MonthlyReportDto`: `monthStart`, `monthEnd`, `totalSpent`, `avoidableSpend`, `budgetAmount` (nullable), `budgetUsedPercent`, `billsScanned`, `prevMonthTotal`, `vsLastMonthPercent`, `spendByCategory`, `topStores` (`List<StoreTotal>` — nested `StoreTotal(storeName, total, billCount)`, top 5 by spend)
- `BillRepository` additions: `countBillsBetween`, `sumAvoidableBetween` (both mirror the existing `sumTotalBetween` shape), and `findStoreTotalsBetween` — a JPQL `GROUP BY b.storeName ORDER BY SUM(...) DESC` query returning `List<Object[]>`, capped to top 5 via a `Pageable` param (JPQL has no `LIMIT` keyword, so this reuses the same pagination mechanism `findRecentByUserId` already established)
- `vsLastMonthPercent`/`spendByCategory` use the exact same formulas/pre-seeded-zero pattern as `InsightsService` (Day 11/17) — kept consistent rather than reinventing
- Budget: `users.budget_amount` (`V7__add_budget_goal.sql`, nullable `DECIMAL(10,2)`, no default), `User.budgetAmount` (`BigDecimal`). Set via `PATCH /api/users/me/preferences` with a `budgetAmount` field, validated `@DecimalMin(value = "0.0", inclusive = false)` (rejects exactly `0`, allows omission). `budgetUsedPercent` in `MonthlyReportService` guards against a null/zero `budgetAmount` before dividing (defensive addition beyond the literal spec, avoids a possible `ArithmeticException`)
- **Known gap**: `UserService.updatePreferences()` follows the Day 12 partial-update convention (`if (req.budgetAmount() != null) ...` — null means "field omitted, don't touch it," not "clear it"). `ProfileScreen` sends `budgetAmount: null` when the user clears the input field, intending to clear the budget — but the backend will silently no-op instead of clearing it, since it can't distinguish "omitted" from "explicitly cleared." Not fixed yet; would need either a dedicated clear signal or different partial-update semantics just for this field
- Mobile: `homeStore` — `monthlyReport: null` added to state; `loadMonthlyReport()` fetches `fetchMonthlyReport()` (new, in `homeApi.js`) and sets `monthlyReport`, catching internally with only `console.error` (never touches `error` — a report failure must never surface as a dashboard-level error, since it's supplementary, not core data). `loadDashboard()` calls `get().loadMonthlyReport()` unconditionally after its own try/catch (not inside the `Promise.all`, not awaited) — fires regardless of whether the critical summary/recentBills fetch succeeded, and never extends `isLoading`'s window
- `HomeScreen`: a "Monthly snapshot" card renders only when `monthlyReport != null`, placed directly below the existing weekly hero card. Budget progress: the `"$X.XX of $Y.YY budget"` label always shows when `budgetAmount` is set; only the bar itself swaps for red "Over budget" text above 100% (label stays either way — read "instead of bar" as replacing just the bar element, not the whole section). Bar color: `<70%` green, `<90%` amber, `≥90%` red. No budget set → "Set a monthly budget" link → `navigation.navigate('Profile')`. Top stores: up to 3 chips, uses the app's standard 2-decimal `fmt()` (not the whole-dollar style from the task's example)
- `profileStore.setBudget(amount)` — thin wrapper: `await get().savePreferences({ budgetAmount: amount })`, reuses all of `savePreferences`'s existing `isSaving`/`profile`/`error` handling rather than duplicating it. Not currently called from anywhere — `ProfileScreen`'s budget input goes through the normal `handleSave`/`savePreferences` path (all preference fields saved together), not this dedicated action
- `ProfileScreen`: Monthly budget row inserted between household size and currency (reuses `prefRow`'s existing `borderBottomWidth`, so the divider is automatic — no separate divider element). `$`-prefixed, right-aligned `TextInput`, `width: 100`. Local `budget` state initialised once inside the existing `initialized.current` guard (so it doesn't clobber in-progress edits on profile re-fetch) — note the `isDirty` comparison uses `profile?.budgetAmount?.toString()` (optional-chained on `profile` too), not just `profile.budgetAmount?.toString()` as literally specified, since this component's body runs fully before `profile` loads and the literal version would throw on initial mount

## Bill editing (established Day 21)
- `UpdateBillRequest` (`dto/`): `storeName`, `billType`, `purchaseDate`, all nullable/optional, no validation annotations — every field is a "apply only if provided" partial update
- `BillHistoryController.updateBill()` — `PATCH /api/bills/{billId}`: `findById` → `DvicheckException.notFound("Bill")`; ownership check → `DvicheckException.unauthorized()` if `bill.user.id` doesn't match `currentUserId()`; per-field partial update (`storeName` applied if non-null/non-blank and `.trim()`med, `billType` parsed via `BillType.valueOf(...toUpperCase())` with an `IllegalArgumentException` catch → `DvicheckException.badRequest("Invalid bill type")`, `purchaseDate` applied if non-null); saves, returns the same `toDetailResponse()` mapping `GET /api/bills/{billId}` already uses, so response shapes match. `GET /api/bills` and `GET /api/bills/{billId}` untouched
- **Note**: this controller carries a class-level `@Transactional(readOnly = true)` (Day 13, since it talks to `BillRepository` directly with no service layer). `updateBill()` needs a method-level `@Transactional` (no `readOnly`) to override that, since a read-only transaction would block or silently fail the `save()` — Spring lets method-level annotations take precedence over class-level ones
- Mobile: `billApi.updateBill(billId, updates)` → `PATCH /bills/{billId}`, returns `response.data.data`. `historyStore.editBill(billId, updates)` calls it, then updates the matching entry in the `bills` array **immutably** (`bills.map(b => b.id === billId ? {...b, ...updates} : b)` — new array, new object, no direct mutation), and if `activeBill.id === billId`, replaces `activeBill` with the full `BillDetailResponse` the backend returned (no separate re-fetch needed). Errors: `set({ error: error.appError?.message || 'Something went wrong.' })`, then rethrow — matches the Day 18 convention
- `HistoryScreen`: long press on an index-view bill row opens a bottom-sheet `Modal` (`transparent` + `animationType="slide"`, `flex-end` overlay so the sheet anchors to the bottom) with a store-name input and `GROCERY`/`UTILITY`/`OTHER` chips — **not** `RESTAURANT`, despite that being in the original task spec, since it isn't a valid `BillType` (enforced by both the Java enum and a DB `CHECK` constraint in `V2__create_core_tables.sql`; adding it for real would need a new migration). A persistent low-opacity ✏️ hint renders on every row (not a transient "just long-pressed" indicator — the modal itself already provides full feedback on long-press). All new styles (`editSheet`, `editHeader`, `modalOverlay`, etc.) live in `HistoryScreen`'s own local `StyleSheet.create` — same as every other screen in this app; there is no shared/global style object anywhere in the codebase (see Day 18 note re: no `src/theme`). Detail view (bill detail) untouched throughout

## Code review — Days 19-20 (Day 21)
Reviewed `PantryController`/`PantryService`, `MonthlyReportService`, `BillRepository`'s new queries, `HomeScreen`'s monthly card, `ProfileScreen`'s budget input, and `historyStore.editBill`. One fix applied; three findings deferred as tech debt (below) — none were reachable through any current app code path, only through direct DB manipulation or edge-case input the review flagged as unlikely but real.
- **Fixed**: `ProfileScreen.handleSave` — `parseFloat("")`/invalid input produces `NaN`, and `JSON.stringify` serialises `NaN` as `null`; combined with the backend's "null means omitted" partial-update semantics, this made invalid budget input **silently no-op while still showing "Preferences saved."** Now guarded: if `budget` is non-blank and `parseFloat` yields `NaN`, the save is blocked client-side with an error toast instead of silently succeeding. Also fixed the same catch block to read `err.appError?.message` (it was hardcoded to a generic string, unlike every other error handler since Day 18) — so a legitimate backend validation rejection (e.g. a negative budget via `@DecimalMin`) now surfaces its real reason instead of a generic failure message
- **Confirmed correct, no fix needed**: `PantryController`/`PantryService` `@Transactional` boundaries (mapping happens entirely inside the transactional method, `toDto()` never touches the one `LAZY` field); `MonthlyReportService` has `@Transactional(readOnly = true)`; no N+1 risk anywhere in `MonthlyReportService` (all aggregate queries, plus one flat list read where only plain columns are accessed); `countBillsBetween`/`sumAvoidableBetween` JPQL syntax; `HomeScreen`'s monthly card null-safety (gated on `monthlyReport &&`, `budgetAmount != null` checked before use)

## Spending trends (established Day 22)
- `SpendingTrendsService.getTrends(userId) -> SpendingTrendsDto` — builds 8 `WeeklyDataPoint`s, 7 weeks ago through the current week (index 7); uses only the existing `BillRepository.sumTotalBetween` — no new DB queries. `maxWeekTotal` defaults to `BigDecimal.ONE` when every week is zero, purely to keep the mobile chart's `total / maxWeekTotal` division from ever hitting zero. `avgWeeklySpend` = sum / 8, scaled to 2dp
- `GET /api/reports/trends` added to `MonthlyReportController` (alongside `/monthly`, not a new controller) — same `currentUserId()` pattern, `ApiResponse<SpendingTrendsDto>`
- `HomeScreen`'s "Spending trend" card: pure React Native `View`s, no charting library — 8 flex-1 columns, each bar height = `(total / maxWeekTotal) * 72` clamped to a 2px minimum (so zero-spend weeks still show a visible sliver), current week (index 7) in `COLORS.accent`, the other 7 in `COLORS.border`. Labels only under weeks 0/2/4/6 (every other, to avoid crowding) — rendered as empty string rather than omitted for the other 4, so every column's label row stays the same height and bars line up evenly
- `homeStore.loadDashboard()`: the two critical fetches (`fetchHomeSummary()`, `fetchRecentBills(5)`) are inlined directly in its own `Promise.all` — there are no separate `loadSummary`/`loadRecentBills` actions. After that (success or failure), it unconditionally fires `get().loadMonthlyReport()` and `get().loadTrends()` — both fire-and-forget, neither awaited, neither inside the `Promise.all`, both catching internally with only a console log (never touching `error`) since both are supplementary cards, not core dashboard data
- `InsightsScreen`'s "Category mix this week": single horizontal stacked bar using **current week only** (no per-category previous-week data exists yet — `WeeklyInsightDto.spendByCategory` is current-week-only, `prevWeekTotal` has no category breakdown). Segment width = `categoryAmount / insights.totalSpent` (not the sum of categories) — segments under 5% aren't drawn in the bar (too thin to render meaningfully) but **do still appear in the legend below**, so a small-but-real category is never silently dropped from the summary, just from the bar itself

## Bug fixes + item feedback (established Day 23)
- **Edit-sheet keyboard fix** (`HistoryScreen`): the long-press edit `Modal` now wraps its content in `KeyboardAvoidingView` (`behavior: 'padding'` on iOS, `'height'` on Android) so the keyboard no longer covers the store-name input/Save button. The dark backdrop is a separate `TouchableWithoutFeedback`-wrapped `View` (`StyleSheet.absoluteFillObject`, tap-to-dismiss) rendered *before* the sheet `View` as a sibling — not a single tappable overlay wrapping everything — so taps inside the sheet itself don't propagate and close it. No `ScrollView` exists inside the sheet, so `keyboardShouldPersistTaps` didn't apply anywhere
- **`InsightsScreen` missing back button** — root cause was **not** "navigate('Insights') switches tabs" (`Insights` was already removed from the tab bar on Day 19, replaced by `Pantry`; `HomeScreen` already pushes the existing root-level `Insights` stack screen, unchanged since Day 19). The real cause: the root `Stack.Navigator` has `screenOptions={{ headerShown: false }}` globally, so every screen renders its own header manually — `InsightsScreen` never got one when it stopped being a tab screen. Fix: `InsightsScreen` now accepts a `navigation` prop (previously wasn't destructuring one, even though React Navigation always passes it) and has a manual `←` back button in its own header, mirroring `ProfileScreen`'s existing pattern. **No new route was added** — there is no `InsightsDetail` screen; the existing `Insights` route in `App.js` and `HomeScreen`'s `navigate('Insights')` call are both unchanged
- `V8__add_item_feedback.sql`: `item_feedback` table (`user_id`/`line_item_id` FKs, both `ON DELETE CASCADE`, `feedback` constrained to `HELPFUL`/`UNHELPFUL` via a DB `CHECK`, `UNIQUE(user_id, line_item_id)`). `ItemFeedback` entity uses `@Getter @Setter` rather than every other entity's `@Data` — deliberate, since `@Data`'s generated `equals`/`hashCode`/`toString` would traverse its two lazy `@ManyToOne` fields (`user`, `lineItem`), a known Lombok+JPA footgun
- `FeedbackController.submitFeedback()` — `POST /api/feedback/{lineItemId}` with `{feedback: "HELPFUL"|"UNHELPFUL"}`: validates against that literal set → `badRequest("Invalid feedback value")`; `notFound("Line item")`; ownership via `lineItem.getBill().getUser().getId()` → `unauthorized()`; upsert via `ItemFeedbackRepository.findByUserIdAndLineItemId` (update in place if found, else build new via `userRepository.getReferenceById(userId)` — a lazy proxy, no extra `SELECT`, same trick `PantryService.updateFromBill()` uses). Needs an explicit method-level `@Transactional` (no service layer here, and `lineItem.getBill().getUser()` is a two-hop lazy traversal) — this makes **9** controllers now carrying the copy-pasted `currentUserId()` tech debt (was 8 as of Day 21)
- On the *update* path (existing feedback flipped to the other value), `createdAt` in the response stays the original timestamp — no `@PreUpdate` on `ItemFeedback`, so it always reflects "first given," not "last changed." Intentional, not a bug
- Mobile: `feedbackApi.js` (new file, one-domain-per-file convention like `billApi`/`homeApi`/`pantryApi`) — `submitFeedback(lineItemId, feedback)`. `ScanScreen`'s `ItemRow`: feedback row gated on category alone (`REDUCIBLE`/`AVOIDABLE`, independent of whether `suggestion` text exists) — optimistic update via `handleFeedback` (capture previous value, set new value, revert + toast on failure), tap shows "Thanks!" via a **local** `showThanks` state (not lifted to the screen) for 1.5s via `setTimeout`, cleared via a ref both on the next tap and on unmount. `feedbackState` resets in `reset()` alongside `scanResult`/`manualItems`
- `HistoryScreen`'s detail view mirrors the same feedback row/styles (`feedbackRow` etc., explicitly duplicated rather than shared — a comment flags both copies as candidates for extraction into one component later) but gated more narrowly: reuses the existing `showSuggestion` boolean as-is (category **and** non-null `suggestion`), since there's no per-row component here to hold local transient state the way `ScanScreen`'s `ItemRow` does — `thankYouItems` (screen-level state, keyed by `lineItemId`) fills that role instead, via a plain `setTimeout` with no ref-based cancellation (a rapid double-tap on the same item just means two independent timers both eventually set it back to `false` — harmless)
- **Also fixed this session, not in the original Day 23 plan**: `ScanScreen.handleScan`'s catch block was still reading the pre-Day-18 `err.response?.data?.message` instead of `err.appError?.message` — meaning real backend error messages (e.g. a 503 `"Receipt scanning is temporarily unavailable"`) never reached the user, silently replaced by a hardcoded "try better lighting" fallback that's actively misleading for infrastructure failures (a 503 from `OcrService` has nothing to do with image quality — that's always a 400). Fixed to read `err.appError?.message` first, matching every other screen since Day 18
- **Diagnosed, not a code bug**: a `503` scan failure this session traced back to a stale/broken backend process (previous run had died from an OOM-kill, exit code 137, days earlier; the then-running process wasn't logging anywhere visible) — a clean restart with the three API keys re-exported resolved it immediately, confirmed via a full successful scan through Gemini end-to-end

## Budget alerts (established Day 24)
- No separate budget-goal entity/table — `budget_amount` still lives on `users` (`V7`, Day 20). `V9__add_budget_alert_log.sql` adds `budget_alert_log` purely as a dedupe log (`user_id`/`threshold`/`period_month`, `UNIQUE(user_id, threshold, period_month)`, `threshold` DB-`CHECK`ed to `80`/`100`, `period_month` a `VARCHAR(7)` `'YYYY-MM'` string via `YearMonth.now().toString()`). One doc-comment fix made before creating it: the comment text specified had `%%` (printf-style escaping) — corrected to a single `%`, since this is a plain SQL file with no format-string templating (Flyway placeholders use `${...}`, not `%`)
- `BudgetAlertLog` entity: same `@Getter @Setter` (not `@Data`) convention as `ItemFeedback`, same lazy-relation reasoning
- `BudgetAlertService.checkAndSendAlerts(userId)` — whole body wrapped in try/catch, logs and returns, never throws outward. Pulls `totalSpent`/`budgetAmount`/`budgetUsedPercent` **only** from `MonthlyReportService.getMonthlyReport()` — no separate bill-summing query — so the numbers in a push notification always match what `HomeScreen` shows. Returns early if `budgetAmount` is null (no budget set) or `percentSpent < 80`. Checks `[80, 100]` in ascending order — **both can fire in the same call** (e.g. a user at 105% who's never been alerted gets two separate notifications + two log rows in one pass, not just the higher one) — each gated independently by `existsByUserIdAndThresholdAndPeriodMonth`. One naming bug caught before writing: the spec's pseudocode named the new row `log`, which would have shadowed the class's `@Slf4j`-generated `log` field — used `alertLog` instead
- Hooked in directly inside `BillScanController.scanBill()`, right after `pantryService.updateFromBill(...)` — **not** via a `BillService.saveBill()` method, which doesn't exist (`BillService` is read-only, used only by `HomeController`; the actual bill save has always been `billRepository.save(bill)` directly in the controller). Confirmed safe to call inline rather than needing to move to a different layer: `BillScanController` has no `@Transactional` anywhere, so `billRepository.save(bill)` already commits as its own immediate transaction the instant that line runs — there's no wider transaction that could later roll back and leave a false alert-log row
- `NotificationService`: added a 4-arg `sendNotification(pushToken, title, body, Map<String, Object> data)` overload — the 3-arg version now just delegates to it with `Map.of()`, so there's exactly one place building the Expo payload. The payload itself had to switch from `Map.of(...)` (immutable, fixed at construction) to a mutable `LinkedHashMap`, since `data` is only conditionally added (non-null and non-empty). `sendWeeklyInsightsReminder()` untouched, still calls the 3-arg version, behavior unaffected either way
- Mobile: `navigationRef.js` (new — `createNavigationContainerRef()` + a guarded `navigate()` helper) attached via `<NavigationContainer ref={navigationRef}>` in `App.js`. `NotificationListener.js` (new, in `src/components/` alongside `Toast`/`ErrorBoundary` — a headless component, returns `null`) sets up `addNotificationResponseReceivedListener`, checks `data.type === 'BUDGET_ALERT'`, cleans up on unmount
- **`registerForPushToken()` in `src/utils/notifications.js` untouched**, as instructed — token registration is unrelated to this listener
- **Correction, same recurring issue as Day 23's note above**: there is still no `InsightsDetail` route anywhere in this app — confirmed again by re-reading `App.js` before writing this. `NotificationListener` navigates to `'Insights'`, the one real route that's existed unchanged since Day 19. No new route was added or needed

## Onboarding flag (established Day 25)
- No separate onboarding/goal table — `onboarding_completed` lives directly on `users`, same pattern as `budget_amount` (`V7`, Day 20). This is now the established pattern for **any** new boolean column on `users` that existing accounts must not be disrupted by: `ADD COLUMN ... NOT NULL DEFAULT TRUE` first (Postgres backfills every existing row to `TRUE` at that statement, satisfying `NOT NULL`), **then** a separate `ALTER COLUMN ... SET DEFAULT FALSE` (changes only what future `INSERT`s get — never rewrites existing rows). Collapsing these into one `DEFAULT FALSE` statement would flip every existing test account back into seeing onboarding again. `V10__add_onboarding_completed.sql` does exactly this
- `User.onboardingCompleted` (`Boolean`, `@Builder.Default = false`) — also given the same defensive `@PrePersist` null-guard every other `@Builder.Default` field on `User` already has (not explicitly asked, but this entity also carries `@AllArgsConstructor`, which bypasses `@Builder.Default` entirely if ever invoked directly)
- `AuthResponse` and `UserProfileDto` both now carry `onboardingCompleted` (last field in each) — so mobile gets it directly from login/profile-fetch, never needs a separate round trip to check onboarding state
- `UserService.completeOnboarding(userId)` / `POST /api/users/me/onboarding-complete` — straightforward set-and-save, logs on success. Uses the controller's existing `currentUserId()`, no second helper added
- Mobile: `OnboardingScreen.js` (new) — 3 slides via a horizontal `pagingEnabled` `FlatList` (`getItemLayout` added so `scrollToIndex` from the "Next" button works reliably — without it, RN's `scrollToIndex` is unreliable against unmeasured items), dot indicator, "Skip" hidden on the last slide, "Get Started" replaces "Next" there. `handleFinish` (Skip and Get Started both call it): `completeOnboarding()` wrapped in try/catch that only logs — **never blocks** `markOnboardingComplete()` or `navigation.replace('MainApp')` on that network call failing, so a flaky connection can't strand a user on the onboarding screen after they've already dismissed it
- `authStore`: `onboardingCompleted` persisted to `SecureStore` (`setAuth` writes it, `loadStoredAuth` reads it back as `stored === '1'`, `clearAuth` deletes the key), initial state defaults to `true` — deliberately, so the app never flashes onboarding before `loadStoredAuth` has actually resolved
- `SplashScreen`: the instructions for this rewrite assumed a purely reactive effect watching `[isLoading, isAuthenticated, onboardingCompleted]` — but nothing in that shape actually calls `loadStoredAuth()` anymore, which would have left the app stuck on the splash screen forever (`isLoading` never flips to `false`). Fixed by keeping a separate mount-effect that calls `loadStoredAuth()`, with the reactive effect only handling the resulting navigation decision
- **Three real bugs found and fixed via live device testing this session, none in the original Day 25 plan**:
  1. `authApi.js`'s `verifyOtp()` explicitly destructured only 4 fields from the response and dropped `onboardingCompleted` entirely — so `data.onboardingCompleted` was always `undefined` (falsy) in `OTPVerifyScreen`, meaning **every** login routed to `Onboarding`, existing accounts included, regardless of what the backend correctly returned. Confirmed backend was fine throughout (DB value and a live `verify-otp` call both correctly showed `true` for the pre-existing test account) before finding this
  2. `OnboardingScreen`'s "Skip" button wasn't appearing at all on a real device — likely `zIndex` alone being unreliable for absolutely-positioned siblings on Android. Fixed by rendering it *after* the `FlatList` in JSX (guarantees paint-order-on-top regardless of platform) and adding `elevation: 10` alongside the existing `zIndex: 10`
  3. `PhoneEntryScreen`'s validation regex (`/^\+\d{10,}$/`) had a lower bound but no upper bound, so a 15+ digit string passed silently. Replaced with the backend's actual E.164 pattern (`^\+[1-9]\d{7,19}$`, the Day 2 convention) so invalid input is now rejected client-side instead of silently passing through

## App icon + splash branding (established Day 26)
- No Flyway migration today — `V11` is still the next free version; this was a mobile-asset-only day (backend untouched)
- `mobile/scripts/generate-assets.js` — a build-time-only Node script (`node scripts/generate-assets.js`), never imported by app code. Uses `sharp` to rasterize hand-built SVG strings into PNGs; `sharp` is installed as a **devDependency only**, since it never ships in the app bundle. One shared receipt-glyph path (rounded top, 8-tooth torn-edge bottom) is reused at every output size via scale/translate, so the silhouette is identical across all four files. Colors are hardcoded from the real `COLORS` values (`bg #f5f2ee`, `ink #1a1612`, `accent #e8622a`), not placeholders
- **`icon.png`** (1024×1024, full bleed — safe to fill edge-to-edge since iOS/generic launchers apply their own corner mask) vs **`adaptive-icon.png`** (1024×1024, transparent, glyph confined to the center 660×660 / 66% safe zone with an extra 8% margin) — these are deliberately different files with different geometry, not the same image reused. Android's adaptive-icon mask can crop right up to that safe-zone edge, so anything closer to the border than that gets clipped on some devices/launcher shapes. Also generated: `splash-icon.png` (400×400, transparent, sized for `contain` against the splash background) and `favicon.png` (48×48, full bleed, simplified — no internal detail lines, which would just be noise at that size)
- `app.json`: `icon`, new `splash` block (`image`/`resizeMode: contain`/`backgroundColor` = exact `COLORS.bg`), and `android.adaptiveIcon` (`foregroundImage` + `backgroundColor` = exact `COLORS.accent`) all point at the new assets. `android.adaptiveIcon` was collapsed from the old 3-layer form (`backgroundImage`/`monochromeImage`, referencing now-unwired old template files that still exist on disk) down to the simpler 2-key `foregroundImage`+`backgroundColor` form — a deliberate reduction, not an oversight. `name`/`slug` (still the generic `"mobile"`/`com.anonymous.mobile"`) and the `plugins` array were untouched
- `expo-splash-screen` installed as a **regular dependency** (unlike `sharp`) — it's a native module the running app actually calls. `ExpoSplashScreen.preventAutoHideAsync()` runs at module scope in `App.js`. **Note**: this is aliased as `ExpoSplashScreen`, not `SplashScreen` — that name was already taken by the existing screen-component import (`./src/screens/SplashScreen`), and importing the native module under the same name would have silently shadowed it. `hideAsync()` is called from an `onLayoutRootView` callback wired to `onLayout` on a **new outermost `<View style={{flex:1}}>`** wrapping `ErrorBoundary` — not `NavigationContainer`'s `onReady`, since `NavigationContainer` doesn't render a native view itself and doesn't forward `onLayout`; wrapping the whole tree in a plain `View` is Expo's own documented pattern for this handoff. This only covers the native-splash-to-JS-render gap — completely separate from Day 25's `isAuthenticated`/`onboardingCompleted` routing logic inside `SplashScreen.js` itself, which is untouched
- `SplashScreen.js`: the emoji `<Text>` logo replaced with `<Image source={require('../../assets/splash-icon.png')} resizeMode="contain" />`; the `logo` style changed from `fontSize`-based to `width: 96, height: 96`, keeping the **real** existing `marginBottom: 12` (not the `16` used in the illustrative example given for this task) so spacing to the title below is unaffected
- **Real home-screen app icon and native splash are only verifiable in a custom dev client (`expo run:ios`/`expo run:android`) or a standalone/EAS build** — Expo Go always shows its own icon and its own branded loading screen regardless of `app.json` config. Confirmed today via live testing: the project has no `expo-dev-client` installed, so on-device testing this session was Expo Go only — the in-JS `SplashScreen.js` `Image` swap is verifiable that way, but the actual icon/native-splash verification is deferred until a real dev-client or EAS build exists
- **Also fixed this session, dev-workflow only, not app code**: found two stale/conflicting Metro bundler processes — one running since Aug 11 (19 days idle) still holding port 8081, plus a same-day second instance that couldn't bind and silently fell back to another port. If the device had been scanning a QR code pointed at the stale instance, **none** of this session's changes (or anything since Aug 11) would have shown up at all. Killed both and started a single fresh instance. Same root-cause pattern as the backend's recurring stale-process issue (Days 16/23) — orphaned dev-server processes surviving past when they were forgotten about

## Migrations
- `V1`–`V6` exist. **`V7`** = `add_budget_goal` (`budget_amount` on `users`, Day 20). **`V8`** = `add_item_feedback` (`item_feedback` table, Day 23). **`V9`** = `add_budget_alert_log` (`budget_alert_log` table, Day 24). **`V10`** = `add_onboarding_completed` (`onboarding_completed` on `users`, Day 25) — still the last migration; no new one on Day 26. Next free version is **`V11`**.

## Tech debt
From Day 9-12 code review (Day 13) — only the `BillHistoryController` transaction issue was fixed then:
- `AddItemRequest.quantityOrDefault()` is dead code — `ShoppingListService.addItem()` re-implements the same "default to `1`" rule inline instead of calling it; the two could drift
- `PantryMemoryRepository.findByUserIdOrderByLastBoughtDateDesc()` — **no longer dead code as of Day 19**, now used by `PantryService.getPantryItems()`
- `currentUserId()` is copy-pasted into **9** controllers as of Day 23 (`ShoppingListController`, `BillHistoryController`, `InsightsController`, `UserController`, `BillScanController`, `HomeController`, `PantryController`, `MonthlyReportController`, `FeedbackController`) — up from 5 at the time this note was first written (Day 13); behaviorally identical today, but drift-prone and growing
- `AddItemRequest.quantity` has no length/format validation, unlike `name`'s `@NotBlank` — an oversized value hits Postgres's `VARCHAR(50)` constraint as a raw 500 instead of a clean 400
- `manualItems` in `ScanScreen`'s result phase are still local-state-only (tracked separately above, resurfaced here since it's still open)

From Days 19-20 code review (Day 21) — all deferred, none currently reachable through the app's own code paths:
- `PantryItemDto`'s `estimatedRemainingDays`/`purchaseCount` are primitive `int`, but the underlying DB columns (`estimated_remaining_days`, `purchase_count`) are nullable with no `NOT NULL` constraint (`V2__create_core_tables.sql`) and the entity fields are boxed `Integer`. If either is ever actually `NULL` (only reachable today via manual DB edit — `PantryService.updateFromBill()` always sets both), `PantryService.toDto()`'s auto-unboxing throws an NPE that takes down all of `GET /api/pantry` for that user
- `BillRepository.findStoreTotalsBetween()` groups by raw `b.storeName` with no case/whitespace normalization — the same merchant could appear as separate `topStores` entries if `storeName` varies across bills (e.g. AI-extracted casing vs. a manual correction via the new `PATCH /api/bills/{id}`, which only `.trim()`s). Degrades `MonthlyReportDto.topStores` accuracy, doesn't crash anything
- `historyStore.editBill()`: the `bills` array entry is patched with the client's raw `updates` object, while `activeBill` gets the server-confirmed `BillDetailResponse` — if the server ever transforms a value (today: `storeName.trim()`), the list row and detail view could disagree until the next `loadBills()`. Currently invisible in practice (whitespace doesn't render visibly)

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
✅ Day 17 complete — `SpendingNarrativeService` (Gemini), personalised weekly coach narrative, `InsightsScreen` coach card, narrative caching
✅ Day 18 complete — `GlobalExceptionHandler` + `ApiErrorResponse` (backend), Axios response interceptor with normalised `error.appError` (mobile), `EmptyState` + `SkeletonCard`/`SkeletonList` components, loading/error/empty states wired into `HomeScreen`/`InsightsScreen`/`ListsScreen` (detail view) — no Pantry screen exists to wire
✅ Day 19 complete — `PantryController` + `GET /api/pantry`, `PantryScreen` with depletion status, History search (debounced, `?search=` param), Pantry tab in navigation (replaced `Insights`, which moved to a `HomeScreen` button — tab order is Home | Scan | Lists | Pantry | History, not the originally-planned 6-tab layout)
✅ Day 20 complete — Flyway `V7` (`budget_amount`), `MonthlyReportService`, `GET /api/reports/monthly`, budget preference, `HomeScreen` monthly card with budget progress bar, `ProfileScreen` budget input (budget-clearing has a known gap — see Day 20 section above)
✅ Day 21 complete — `PATCH /api/bills/{id}` (`storeName`, `billType`, `purchaseDate`), `HistoryScreen` long-press edit sheet, code review pass on Days 19-20 (one real fix applied — see Day 21 section above — plus three deferred tech-debt findings)
✅ Day 22 complete — `SpendingTrendsService`, `GET /api/reports/trends`, 8-week sparkline bar chart on `HomeScreen`, category mix stacked bar on `InsightsScreen`
✅ Day 23 complete — keyboard fix on edit sheet, `InsightsScreen` back button (own header, no new route), Flyway `V8` (`item_feedback` table), `FeedbackController`, thumbs feedback on `ScanScreen` and `HistoryScreen` detail view
✅ Day 24 complete — Flyway `V9` (`budget_alert_log`), `BudgetAlertService` threshold detection (80%/100%, reuses `MonthlyReportService` for spend numbers), hooked into the bill save flow, `NotificationService` data-payload overload, new `navigationRef.js` + notification-tap navigation to the existing `Insights` route (no `InsightsDetail` — that route has never existed)
✅ Day 25 complete — Flyway `V10` (`onboarding_completed`, backfilled true for existing users), `AuthResponse`/`UserProfileDto` carry the flag, `POST /api/users/me/onboarding-complete`, 3-slide `OnboardingScreen`, `authStore` persists the flag via `SecureStore`, Splash/OTPVerify route on `onboardingCompleted` — plus 3 real bugs found and fixed via live device testing (see Day 25 section above)
✅ Day 26 complete — app icon + splash branding: `generate-assets.js` (`sharp`, devDependency only) produces `icon.png`/`adaptive-icon.png`/`splash-icon.png`/`favicon.png` from the real `COLORS` palette, `app.json` wired to the new assets, `expo-splash-screen` handles the native-to-JS handoff, `SplashScreen.js` uses the real logo — icon/native-splash verification deferred to a real dev-client/EAS build (Expo Go can't show them)
🔄 Day 27 next — Deep links + share receipt feature

## Do NOT change
- application.yml datasource section
- SecurityConfig.java until Day 4
- Any committed Flyway migration files (never edit, only add new ones)
- COLORS object in constants/index.js
