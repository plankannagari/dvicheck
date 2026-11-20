// Referral invite text (Day 37). No react-native imports: plain JS, safe to unit-test.

// TODO Day 50: set to the App Store / Play listing once the app is published.
// While empty, the invite message carries only the code and the dvicheck:// link.
export const APP_STORE_URL = '';

export function buildInviteMessage(code, days) {
  const lines = [
    `I use dvicheck to track my spending. Use my invite code ${code} to get ${days} days of Pro free.`,
  ];
  if (APP_STORE_URL) lines.push(APP_STORE_URL);
  // Always last. Only opens the app if it's already installed — the code above is
  // what a new user types in. parseDeepLink doesn't handle 'invite' yet.
  lines.push(`dvicheck://invite/${code}`);
  return lines.join('\n');
}
