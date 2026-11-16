# dvicheck analytics

Product analytics for the mobile app, sent to **Mixpanel** via `mixpanel-react-native` (4.1.0).
Added on Day 36.

- Event names and the property allow-list: `mobile/src/analytics/analyticsSchema.js`
- The wrapper every call site uses: `mobile/src/analytics/analytics.js`. Import only from here, never from the SDK.

Every call is fire-and-forget and can never throw into the app. Analytics turns itself
off when there is no token for the build or no native module (Expo Go, or a dev client
built before the package was added). The app works the same either way.

**Rule:** never send phone numbers, emails, amounts, totals, store names, item names or
receipt text. Add a key to `ALLOWED_PROPS` only after deciding it is safe. `sanitize()`
drops every other key. It also drops any value that isn't a string, number or boolean,
and cuts strings to 40 characters. In dev builds it warns once for each dropped key.

## Events

| Event | Fires when | Properties |
|---|---|---|
| `app_opened` | Analytics finishes initialising at JS startup (`App.js`); once per launch | — |
| `screen_viewed` | The focused route changes (`NavigationContainer.onStateChange`); not for the first route | `screen`, `previous_screen` |
| `login_completed` | OTP verified and the session saved (`authStore.setAuth`) | `method` (`phone_otp`) |
| `logout` | Sign out, sent **before** the identity is reset (`authStore.clearAuth`) | — |
| `onboarding_step_viewed` | Each onboarding slide is shown (first slide, Next, swipe) | `step` (`'0'`–`'2'`) |
| `onboarding_completed` | Skip or Get Started | `result` (`finished` \| `skipped`) |
| `scan_started` | The user takes or picks a receipt photo | `source` (`camera` \| `gallery`) |
| `scan_succeeded` | The backend returns a parsed bill | `item_count` (number of line items), `duration_ms` |
| `scan_failed` | The scan request fails | `error_type` (`network` \| `ocr` \| `parse` \| `cap` \| `other`) |
| `scan_queued_offline` | An offline capture is saved to the sync queue | — |
| `offline_queue_flushed` | A sync-queue run finishes after trying at least one upload | `item_count` (number synced) |
| `paywall_viewed` | The Paywall screen mounts | `source` (`trends`, `profile`, `budget_alerts`, `pdf_report`, `coach`, `scan_cap`, `other`, `unknown`) |
| `purchase_started` | The user taps a package | `package_type` (RevenueCat, e.g. `MONTHLY`) |
| `purchase_completed` | The purchase succeeds with the Pro entitlement active. No price or revenue is sent | `package_type` |
| `purchase_cancelled` | The user closes the store sheet | `package_type` |
| `purchase_failed` | Any other purchase error | `error_type` (RevenueCat's `readableErrorCode`, an error name, not the message) |
| `restore_completed` | Restore purchases returns | `result` (`restored` \| `nothing`) |
| `pro_gate_hit` | The backend returns 402 for an action the user asked for (not background loads) | `feature` (`scan_cap` \| `pdf_report` \| `budget_alerts` \| `coach` \| `other`) |
| `budget_set` | Profile preferences saved with a changed budget | `enabled` (`false` = cleared) |
| `shopping_list_created` | A shopping list is created | — |
| `receipt_shared` | A bill is shared from History (only when the share completes) | `method` (`share_sheet`) |
| `notification_opened` | The user taps a push notification | `type` (`BUDGET_ALERT` \| `WEEKLY_COACH` \| `unknown`) |
| `coach_viewed` | A weekly coach message is visible on Insights; at most once each time Insights is focused | — |
| `pdf_report_generated` | The monthly PDF share sheet opens | — |

## Allowed properties

| Key | Meaning |
|---|---|
| `screen` | Route name now focused (names only, never route params) |
| `previous_screen` | Route name focused before |
| `source` | What started the action: the scan input, or the screen or feature that opened the paywall |
| `feature` | Which Pro gate returned 402 |
| `reason` | A short fixed reason code (allowed, not used yet) |
| `step` | Onboarding slide index |
| `method` | How something was done (`phone_otp`, `share_sheet`) |
| `package_type` | RevenueCat package type (`MONTHLY`, `ANNUAL`, …) |
| `product_id` | Store product id (allowed, not used yet) |
| `is_pro` | Whether the user has an active Pro subscription (also a super property) |
| `item_count` | A count: line items in a scan, or receipts synced |
| `duration_ms` | How long an operation took, in milliseconds |
| `error_type` | A fixed error bucket or SDK error name, never an error message |
| `threshold` | A budget-alert threshold percentage (allowed, not used yet) |
| `type` | Notification type from the push payload |
| `enabled` | An on/off setting (budget set or cleared) |
| `result` | Outcome label (`finished`/`skipped`, `restored`/`nothing`) |

## Never collected

- Phone numbers
- Email addresses
- Amounts, totals and prices, including budget values and purchase revenue
- Store names and item names
- Receipt images and receipt text
- Location. The app requests no location permission. Mixpanel's IP-based geolocation is switched off with `setUseIpAddressForGeolocation(false)`, so events carry no city, region or country.

There are also no bill, list or line-item ids, no route params and no free-text user input.

## Identity

- **Distinct ID = the dvicheck user UUID** (`users.id`, returned as `userId` by `POST /api/auth/verify-otp`). The same id is the RevenueCat `appUserID`.
  - Set by `identify()` after login (`authStore.setAuth`) and again on cold-start session restore (`authStore.loadStoredAuth`).
  - Never a phone number or email.
- **Before login**, events use Mixpanel's random anonymous device id.
- **On sign out**, `logout` is sent first, then `reset()`, so the next person on the same device starts with a new anonymous id.
  - Known gap: an expired session (401) clears the stored tokens but does not reset analytics.
- **Super properties** (attached to every event):
  - `platform`: `ios` / `android`
  - `app_version`: `expo.version` from `app.json`
  - `is_pro`: set from the backend's `isPro` whenever the profile loads or saves. It is also set as a People property. Right after a purchase it can read `false` until the RevenueCat webhook reaches the backend.
- **Automatic events are off** (`trackAutomaticEvents: false`). Only the events above are sent.

## Tokens

- **Two Mixpanel projects:** `dvicheck-dev` and `dvicheck-prod`.
- **Where they live:** `mobile/.env` (git-ignored via the root `.gitignore`):

  ```
  EXPO_PUBLIC_MIXPANEL_TOKEN_DEV=...
  EXPO_PUBLIC_MIXPANEL_TOKEN_PROD=...
  ```

  `mobile/.env.example` lists the keys with empty values.
- **Which one is used:** `__DEV__` builds use the dev token, release builds the prod token. A missing token turns analytics off for that build.
- **They are public.** `EXPO_PUBLIC_*` values are copied into the JS bundle at build time, which is fine for Mixpanel project tokens. Never put a Mixpanel API secret or service-account credential there.
- **Changing them:** after editing `.env`, restart Metro with `--clear`. EAS builds need the same variables set as EAS environment variables.

## Opting out

- **Where:** Profile → Privacy → **Share anonymous usage data**. On by default.
- **Turning it off** calls `optOutTracking()`. Nothing more is sent from that moment, including events still queued before init.
- **Turning it back on** calls `optInTracking()`. It does not re-send `app_opened`.
- The choice is saved by the Mixpanel SDK **on the device**, not on the backend. It survives restarts but applies to that device, not to the account.
- To verify on device: whether the choice survives sign-out and sign-in, since `reset()` runs at sign-out.

## Notes for App Store privacy labels

- **Data linked to the user:**
  - **Identifiers → User ID** (the dvicheck UUID used as the Mixpanel distinct id)
  - **Usage Data → Product Interaction** (the events above)
- **Purpose:** Analytics.
- **Not used for tracking.** Data isn't combined with other companies' apps or websites for advertising, and isn't shared with data brokers. Mixpanel acts as our processor.
- **No IDFA and no App Tracking Transparency prompt needed.** The app doesn't read the advertising identifier (no `AdSupport`, no `NSUserTrackingUsageDescription`).
- Purchases, phone number (account sign-in) and receipts are collected by the app itself, not by analytics. They get their own label entries outside the scope of this file.
