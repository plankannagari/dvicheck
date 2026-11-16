// SDK 54: the classic downloadAsync/cacheDirectory API only exists under '/legacy'
// (same as syncQueue.js — see the Day 28 note).
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import * as SecureStore from 'expo-secure-store';

import { API_BASE_URL } from '../constants';
import { navigate, navigationRef } from '../navigation/navigationRef';
import useToastStore from '../store/toastStore';
import { track, EVENTS } from '../analytics/analytics';

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

// Pulls `message` out of the backend's ApiErrorResponse JSON the download wrote to
// disk in place of a PDF; null if the body isn't readable/parseable.
async function readErrorMessage(uri) {
  try {
    return JSON.parse(await FileSystem.readAsStringAsync(uri))?.message || null;
  } catch {
    return null;
  }
}

/**
 * Downloads GET /api/reports/monthly/pdf?month=YYYY-MM and opens the share sheet.
 * Returns { ok: true } or { ok: false, reason: 'pro'|'empty'|'bad'|'network'|'error', message? }.
 *
 * Bypasses apiClient (axios can't stream binary to a file in RN), so it re-applies the
 * two interceptor behaviours that matter here: the Bearer header, and 402 -> Paywall.
 */
export async function downloadAndShareMonthlyPdf(month) {
  // The month also becomes part of a local file path — never let raw input into it
  if (typeof month !== 'string' || !MONTH_PATTERN.test(month)) {
    return { ok: false, reason: 'bad', message: 'Invalid month' };
  }

  // API_BASE_URL already ends in /api
  const url = `${API_BASE_URL}/reports/monthly/pdf?month=${month}`;
  const target = `${FileSystem.cacheDirectory}dvicheck-${month}.pdf`;

  // Same source and key as apiClient's request interceptor; header only, never the URL
  const token = await SecureStore.getItemAsync('access_token');
  const headers = token ? { Authorization: `Bearer ${token}` } : {};

  let res;
  try {
    res = await FileSystem.downloadAsync(url, target, { headers });
  } catch (err) {
    console.warn('downloadAndShareMonthlyPdf: download failed', err);
    await FileSystem.deleteAsync(target, { idempotent: true }).catch(() => {});
    return { ok: false, reason: 'network', message: "Can't reach the server. Check your connection and try again." };
  }

  if (res.status !== 200) {
    // A non-200 body is error JSON saved under a .pdf name — read it, then always delete it
    const message = await readErrorMessage(res.uri);
    await FileSystem.deleteAsync(res.uri, { idempotent: true }).catch(() => {});

    if (res.status === 402) {
      // Same as apiClient's Day 32 402 branch: toast, then Paywall without stacking
      track(EVENTS.PRO_GATE_HIT, { feature: 'pdf_report' }); // bypasses axios, so apiClient never sees this 402
      useToastStore.getState().showToast(message || 'Upgrade to Pro to continue', 'info');
      if (navigationRef.isReady() && navigationRef.getCurrentRoute()?.name !== 'Paywall') {
        navigate('Paywall', { source: 'pdf_report' });
      }
      return { ok: false, reason: 'pro', message: message || undefined };
    }
    if (res.status === 404) {
      return { ok: false, reason: 'empty', message: message || 'No spending recorded for that month' };
    }
    if (res.status === 400) {
      return { ok: false, reason: 'bad', message: message || undefined };
    }
    return { ok: false, reason: 'error', message: message || undefined };
  }

  if (!(await Sharing.isAvailableAsync())) {
    return { ok: false, reason: 'error', message: 'Sharing is not available on this device' };
  }

  try {
    // Fired before the await: on iOS shareAsync only resolves once the sheet is dismissed
    track(EVENTS.PDF_REPORT_GENERATED);
    await Sharing.shareAsync(res.uri, {
      mimeType: 'application/pdf',
      UTI: 'com.adobe.pdf',
      dialogTitle: `dvicheck report ${month}`,
    });
  } catch (err) {
    // Keeps the { ok, reason } contract — callers never need their own try/catch
    console.warn('downloadAndShareMonthlyPdf: share failed', err);
    return { ok: false, reason: 'error', message: 'Could not open the share sheet' };
  }
  return { ok: true };
}
