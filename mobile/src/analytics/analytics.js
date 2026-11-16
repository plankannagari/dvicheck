// Mixpanel analytics wrapper (Day 36).
//
// NEVER send phone numbers, emails, amounts, totals, store names, item names or
// receipt text. Add a property key to ALLOWED_PROPS (analyticsSchema.js) only after
// deciding it is safe.
//
// Every export is fire-and-forget and can never throw or reject into the caller.
// Analytics is silently disabled when there is no token for this build type, or when
// the native module is missing (Expo Go, or a dev client built before
// mixpanel-react-native was added) — the app itself is unaffected.

import { Platform, NativeModules } from 'react-native';
import Constants from 'expo-constants';

import { sanitize } from './analyticsSchema';

export { EVENTS, proGateFeature } from './analyticsSchema';

// Written out literally: Expo only inlines EXPO_PUBLIC_* vars accessed as process.env.NAME
const TOKEN = __DEV__
  ? process.env.EXPO_PUBLIC_MIXPANEL_TOKEN_DEV
  : process.env.EXPO_PUBLIC_MIXPANEL_TOKEN_PROD;

const MAX_PENDING_EVENTS = 50;

let client = null;       // Mixpanel instance once init() has resolved
let initPromise = null;  // makes initAnalytics() idempotent
let disabled = false;    // true once we know analytics can't run on this build/device
// Local mirror of the SDK's persisted opt-out, so track() stops at once — including
// events still queued before init — rather than relying on the SDK alone
let optedOut = false;

// Calls made before init resolves, replayed in order. Only 'track' entries count toward
// the cap (oldest dropped first); identify/reset/pro/opt-out entries are never dropped,
// so the right identity is in place for the events around them.
let pending = [];

function devLog(...args) {
  if (__DEV__) console.log('[analytics]', ...args);
}

function safely(fn) {
  try {
    const result = fn();
    // Swallow async rejections from SDK calls that return promises
    if (result && typeof result.catch === 'function') {
      result.catch((e) => devLog('async call failed:', e?.message || e));
    }
  } catch (e) {
    devLog('call failed:', e?.message || e);
  }
}

function enqueue(op) {
  if (disabled) return;
  pending.push(op);
  let trackCount = pending.filter((p) => p.kind === 'track').length;
  while (trackCount > MAX_PENDING_EVENTS) {
    const oldest = pending.findIndex((p) => p.kind === 'track');
    pending.splice(oldest, 1);
    trackCount -= 1;
  }
}

// Runs op now if the client is ready, queues it while init is pending, drops it if disabled
function run(kind, apply) {
  if (disabled) return;
  if (client) {
    safely(() => apply(client));
  } else {
    enqueue({ kind, apply });
  }
}

function loadSdk() {
  // Without the native module the SDK silently falls back to its JavaScript mode —
  // check first so a missing native build means "off", as intended.
  if (!NativeModules.MixpanelReactNative) return null;
  try {
    // Lazy so a missing/broken package can never break app startup
    return require('mixpanel-react-native').Mixpanel;
  } catch (e) {
    devLog('mixpanel-react-native unavailable:', e?.message || e);
    return null;
  }
}

async function doInit() {
  if (!TOKEN) {
    devLog('no Mixpanel token for this build — analytics disabled');
    disabled = true;
    pending = [];
    return;
  }
  const Mixpanel = loadSdk();
  if (!Mixpanel) {
    devLog('native module missing — analytics disabled');
    disabled = true;
    pending = [];
    return;
  }

  // trackAutomaticEvents = false: only the events in analyticsSchema.js are sent
  const instance = new Mixpanel(TOKEN, false);
  await instance.init();
  // The opt-out is persisted by the SDK; seed the local flag before replaying anything.
  // A setOptOut() made before init finished is still queued and applied below.
  optedOut = (await instance.hasOptedOutTracking()) === true;
  if (optedOut) pending = pending.filter((op) => op.kind !== 'track');
  if (__DEV__) instance.setLoggingEnabled(true);
  // Mixpanel derives city/region/country from the request IP by default; off, so
  // "location: never collected" (docs/ANALYTICS.md) is actually true
  instance.setUseIpAddressForGeolocation(false);
  instance.registerSuperProperties({
    platform: Platform.OS,
    app_version: Constants.expoConfig?.version ?? 'unknown',
  });

  client = instance;
  const queued = pending;
  pending = [];
  queued.forEach((op) => safely(() => op.apply(instance)));
  if (__DEV__ && queued.length > 0) safely(() => instance.flush());
}

// Returns the same never-rejecting promise on every call, so callers can chain
// initAnalytics().then(...) without awaiting it in a UI path
export function initAnalytics() {
  if (!initPromise) {
    try {
      initPromise = doInit().catch((e) => {
        devLog('init failed — analytics disabled:', e?.message || e);
        disabled = true;
        client = null;
        pending = [];
      });
    } catch (e) {
      devLog('init failed — analytics disabled:', e?.message || e);
      disabled = true;
      initPromise = Promise.resolve();
    }
  }
  return initPromise;
}

export function track(event, props) {
  try {
    if (optedOut || typeof event !== 'string' || event.length === 0) return;
    const clean = sanitize(props);
    run('track', (c) => {
      if (optedOut) return; // opted out after this event was queued
      c.track(event, clean);
      // Dev only: send immediately so events show up in Live View without waiting for a batch
      if (__DEV__) c.flush();
    });
  } catch (e) {
    devLog('track failed:', e?.message || e);
  }
}

// userId = dvicheck's own user UUID. Never pass a phone number or email.
export function identify(userId) {
  try {
    if (userId === null || userId === undefined || userId === '') return;
    const id = String(userId);
    run('identify', (c) => c.identify(id));
  } catch (e) {
    devLog('identify failed:', e?.message || e);
  }
}

export function reset() {
  try {
    run('reset', (c) => c.reset());
  } catch (e) {
    devLog('reset failed:', e?.message || e);
  }
}

export function setProStatus(isPro) {
  try {
    const value = isPro === true;
    run('pro', (c) => {
      c.registerSuperProperties({ is_pro: value });
      c.getPeople().set('is_pro', value);
    });
  } catch (e) {
    devLog('setProStatus failed:', e?.message || e);
  }
}

// The SDK persists the opt-out flag itself, so it survives restarts
export function setOptOut(optOut) {
  try {
    const value = optOut === true;
    optedOut = value;
    // Drop anything not yet sent; identify/reset/pro ops stay queued
    if (value) pending = pending.filter((op) => op.kind !== 'track');
    run('optout', (c) => (value ? c.optOutTracking() : c.optInTracking()));
  } catch (e) {
    devLog('setOptOut failed:', e?.message || e);
  }
}

// Resolves to a boolean and never rejects. false when analytics is disabled.
export async function isOptedOut() {
  try {
    if (initPromise) await initPromise;
    if (!client) return optedOut;
    return (await client.hasOptedOutTracking()) === true;
  } catch (e) {
    devLog('isOptedOut failed:', e?.message || e);
    return false;
  }
}
