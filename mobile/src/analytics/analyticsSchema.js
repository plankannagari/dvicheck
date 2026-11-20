// Analytics event names + property allow-list (Day 36).
//
// NEVER send phone numbers, emails, amounts, totals, store names, item names or
// receipt text. Add a property key to ALLOWED_PROPS only after deciding it is safe.
//
// Deliberately no react-native / expo imports: this file must stay runnable with
// plain node so sanitize() can be unit-tested without a device.

export const EVENTS = Object.freeze({
  APP_OPENED: 'app_opened',
  SCREEN_VIEWED: 'screen_viewed',
  LOGIN_COMPLETED: 'login_completed',
  LOGOUT: 'logout',
  ONBOARDING_STEP_VIEWED: 'onboarding_step_viewed',
  ONBOARDING_COMPLETED: 'onboarding_completed',
  SCAN_STARTED: 'scan_started',
  SCAN_SUCCEEDED: 'scan_succeeded',
  SCAN_FAILED: 'scan_failed',
  SCAN_QUEUED_OFFLINE: 'scan_queued_offline',
  OFFLINE_QUEUE_FLUSHED: 'offline_queue_flushed',
  PAYWALL_VIEWED: 'paywall_viewed',
  PURCHASE_STARTED: 'purchase_started',
  PURCHASE_COMPLETED: 'purchase_completed',
  PURCHASE_CANCELLED: 'purchase_cancelled',
  PURCHASE_FAILED: 'purchase_failed',
  RESTORE_COMPLETED: 'restore_completed',
  PRO_GATE_HIT: 'pro_gate_hit',
  BUDGET_SET: 'budget_set',
  SHOPPING_LIST_CREATED: 'shopping_list_created',
  RECEIPT_SHARED: 'receipt_shared',
  NOTIFICATION_OPENED: 'notification_opened',
  COACH_VIEWED: 'coach_viewed',
  PDF_REPORT_GENERATED: 'pdf_report_generated',
  REFERRAL_SHARED: 'referral_shared',
  REFERRAL_REDEEMED: 'referral_redeemed',
});

export const ALLOWED_PROPS = new Set([
  'screen', 'previous_screen', 'source', 'feature', 'reason', 'step', 'method',
  'package_type', 'product_id', 'is_pro', 'item_count', 'duration_ms', 'error_type',
  'threshold', 'type', 'enabled', 'result',
]);

// Which Pro gate a 402 came from, derived from the request path only (no query values)
export function proGateFeature(url) {
  const path = typeof url === 'string' ? url.split('?')[0] : '';
  if (path.includes('/bills') && path.includes('scan')) return 'scan_cap';
  if (path.includes('/reports/monthly/pdf')) return 'pdf_report';
  if (path.includes('/budget-alerts')) return 'budget_alerts';
  if (path.includes('/coach')) return 'coach';
  return 'other';
}

const MAX_STRING_LENGTH = 40;

// Key names already warned about, so a dropped key warns once per session, not per event
const warnedKeys = new Set();

function warnDropped(key, why) {
  if (typeof __DEV__ === 'undefined' || !__DEV__) return;
  if (warnedKeys.has(key)) return;
  warnedKeys.add(key);
  console.warn(`[analytics] dropped property "${key}" (${why})`);
}

function isSafeValue(value) {
  if (typeof value === 'string' || typeof value === 'boolean') return true;
  // NaN/Infinity serialise as null — drop them like any other unusable value
  return typeof value === 'number' && Number.isFinite(value);
}

// Returns a NEW object with only allow-listed keys and primitive values.
// Strings are truncated to 40 chars; objects, arrays, null and undefined are dropped.
export function sanitize(props) {
  const clean = {};
  if (props === null || typeof props !== 'object' || Array.isArray(props)) return clean;

  for (const key of Object.keys(props)) {
    const value = props[key];
    if (!ALLOWED_PROPS.has(key)) {
      warnDropped(key, 'not in ALLOWED_PROPS');
      continue;
    }
    if (!isSafeValue(value)) {
      warnDropped(key, `unsupported value type: ${value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value}`);
      continue;
    }
    clean[key] = typeof value === 'string' ? value.slice(0, MAX_STRING_LENGTH) : value;
  }
  return clean;
}
