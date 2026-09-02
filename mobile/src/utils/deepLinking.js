import * as Linking from 'expo-linking';

import { navigate } from '../navigation/navigationRef';
import useHistoryStore from '../store/historyStore';
import useToastStore from '../store/toastStore';

// Module-level singleton — one pending URL at a time is enough for this app's
// scope (a user can only be mid-auth for one deep link at once).
let pendingDeepLink = null;

export function setPendingDeepLink(url) {
  pendingDeepLink = url;
}

export function consumePendingDeepLink() {
  const url = pendingDeepLink;
  pendingDeepLink = null;
  return url;
}

// expo-linking's exact hostname/path split for a custom scheme (dvicheck://...)
// is assumed here, not yet empirically verified against a real running app —
// e.g. it's possible some versions parse `dvicheck://bill/123` as
// { hostname: 'bill', path: '123' } while others fold the whole thing into
// `path` with a null hostname. Step 7's live testing should confirm the real
// shape (logged below in __DEV__) before this is trusted in production; the
// fallback branch below exists specifically to cover that second case.
export function parseDeepLink(url) {
  if (!url) return null;

  const parsed = Linking.parse(url);
  if (__DEV__) {
    console.log('parseDeepLink parsed shape:', JSON.stringify(parsed));
  }

  const { hostname, path } = parsed;

  if (hostname === 'insights') {
    return { type: 'insights' };
  }

  if (hostname === 'bill' && path) {
    const billId = path.split('/').filter(Boolean)[0];
    if (billId) {
      return { type: 'bill', billId };
    }
  }

  // Fallback for the case where hostname isn't split out the way we expect
  // above and the whole thing lands in `path` instead.
  if (!hostname && path) {
    const segments = path.split('/').filter(Boolean);
    if (segments[0] === 'insights') {
      return { type: 'insights' };
    }
    if (segments[0] === 'bill' && segments[1]) {
      return { type: 'bill', billId: segments[1] };
    }
  }

  return null;
}

async function processDeepLink(url) {
  const target = parseDeepLink(url);
  if (!target) return;

  if (target.type === 'bill') {
    // loadBillDetail catches its own errors internally (sets store `error`
    // state) rather than rejecting, so failure is detected by checking state
    // after the call, not via .catch() — it always resets error:null at the
    // start of its own run, so this reading is reliable for this call.
    await useHistoryStore.getState().loadBillDetail(target.billId);
    const { error } = useHistoryStore.getState();
    if (error) {
      useToastStore.getState().showToast('Could not open that receipt', 'error');
      return;
    }
    navigate('MainApp', { screen: 'History' });
    return;
  }

  if (target.type === 'insights') {
    // 'Insights' is the real, only registered route (App.js) — there is no
    // 'InsightsDetail' screen anywhere in this app.
    navigate('Insights');
  }
}

export function handleIncomingUrl(url, isAuthenticated) {
  if (!url) return;
  if (isAuthenticated) {
    processDeepLink(url);
  } else {
    setPendingDeepLink(url);
  }
}

export async function consumeAndProcessPendingDeepLink() {
  const url = consumePendingDeepLink();
  if (url) {
    await processDeepLink(url);
  }
}
