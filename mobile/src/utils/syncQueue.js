// NOTE: SDK 54's expo-file-system ships a new File/Directory-class API as its
// default export; the classic imperative API this file uses (documentDirectory,
// getInfoAsync, makeDirectoryAsync, copyAsync, deleteAsync) only exists under
// the '/legacy' subpath. Importing from 'expo-file-system' directly would not
// have these exports at all.
import * as FileSystem from 'expo-file-system/legacy';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { uploadReceiptImage } from '../api/scanApi';
import useToastStore from '../store/toastStore';
import useHomeStore from '../store/homeStore';
import useProfileStore from '../store/profileStore';
import { isProOnDevice } from './purchases';

const QUEUE_KEY = 'dvicheck_pending_scans';
const PENDING_DIR = FileSystem.documentDirectory + 'pending-receipts/';

async function persistQueue(queue) {
  await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
}

async function ensurePendingDir() {
  const info = await FileSystem.getInfoAsync(PENDING_DIR);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(PENDING_DIR, { intermediates: true });
  }
}

export async function getQueue() {
  try {
    const raw = await AsyncStorage.getItem(QUEUE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (err) {
    console.warn('getQueue failed, returning empty queue:', err);
    return [];
  }
}

export async function addToQueue(captureUri, options = {}) {
  await ensurePendingDir();

  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const extMatch = captureUri.match(/\.[a-zA-Z0-9]+$/);
  const ext = extMatch ? extMatch[0] : '.jpg';
  const destPath = PENDING_DIR + id + ext;

  // Copying (not just referencing the original URI) is what makes the image
  // survive an app restart before it syncs — camera/picker temp files aren't
  // guaranteed to persist across launches.
  await FileSystem.copyAsync({ from: captureUri, to: destPath });

  const queue = await getQueue();
  queue.push({
    id,
    imageUri: destPath,
    options,
    queuedAt: Date.now(),
    retryCount: 0,
  });
  await persistQueue(queue);

  return id;
}

export async function removeFromQueue(id) {
  const queue = await getQueue();
  const item = queue.find((q) => q.id === id);
  const nextQueue = queue.filter((q) => q.id !== id);
  await persistQueue(nextQueue);

  if (item?.imageUri) {
    try {
      await FileSystem.deleteAsync(item.imageUri, { idempotent: true });
    } catch (err) {
      // A missing file on cleanup isn't a failure — idempotent:true already
      // covers the common case, this catches anything else (permissions etc.)
      console.warn('removeFromQueue: local file cleanup failed (ignored):', err);
    }
  }
}

// 'YYYY-MM' in device-local time. The backend's month boundary is server-local, so
// near midnight on the 1st they can disagree briefly — harmless: worst case one
// item is retried early, gets the 402 again, and is re-blocked.
function currentPeriod() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

// Clear `blocked` once the free scan limit no longer applies: the user is Pro, or a
// new month has started — the limit is monthly, so a free user who just waits must
// get their receipts synced. Pro is either source: profileStore's backend isPro, or
// RevenueCat on-device (covers the few seconds after a purchase before the webhook
// lands). Trusting the client here is safe — unblocking only allows a retry, and the
// backend still enforces the limit; a wrong guess just re-blocks on the next 402.
async function unblockIfLimitLifted() {
  const queue = await getQueue();
  if (!queue.some((q) => q.blocked)) return;
  const isPro = useProfileStore.getState().profile?.isPro === true || await isProOnDevice();
  const period = currentPeriod();
  const updated = queue.map((q) =>
    q.blocked && (isPro || q.blockedPeriod !== period)
      ? { ...q, blocked: false, blockedPeriod: undefined }
      : q
  );
  await persistQueue(updated);
}

let isProcessing = false;

export async function processQueue() {
  // Guards against a manual 'Sync now' tap and an automatic reconnect
  // trigger running concurrently.
  if (isProcessing) {
    return { synced: 0, failed: 0 };
  }
  isProcessing = true;

  let synced = 0;
  let failed = 0;

  try {
    await unblockIfLimitLifted();
    const queue = await getQueue();

    // Sequential, not parallel — avoids hammering the scan endpoint with
    // simultaneous multipart uploads right after a connection recovers.
    for (const item of queue) {
      // Blocked by the free scan limit — no point re-uploading until it lifts
      if (item.blocked) continue;
      try {
        await uploadReceiptImage(item.imageUri, item.options, { skipPaywall: true });
        await removeFromQueue(item.id);
        synced += 1;
      } catch (err) {
        // 402 PRO_REQUIRED: not a transient failure, so no retryCount bump. Every
        // remaining item would get the same 402, so block them all now rather than
        // uploading each one just to be rejected, and stop.
        if (err?.response?.status === 402) {
          const lastError = err.response.data?.message || 'Upgrade required';
          const remainingIds = new Set(queue.slice(queue.indexOf(item)).map((q) => q.id));
          // Re-read for the same reason as below — never write back a stale snapshot
          const current = await getQueue();
          await persistQueue(current.map((q) =>
            remainingIds.has(q.id) && !q.blocked
              ? { ...q, blocked: true, blockedPeriod: currentPeriod(), lastError }
              : q
          ));
          break;
        }
        failed += 1;
        console.warn('processQueue: item failed, left queued for retry:', item.id, err);
        // Re-read before persisting rather than reusing the loop's `queue`
        // snapshot — addToQueue() could have appended a new item concurrently
        // while this item was uploading, and writing back a stale snapshot
        // would silently drop it.
        const current = await getQueue();
        const updated = current.map((q) =>
          q.id === item.id
            ? {
                ...q,
                retryCount: (q.retryCount ?? 0) + 1,
                lastError: err?.appError?.message || err?.message || 'Unknown error',
              }
            : q
        );
        await persistQueue(updated);
      }
    }

    if (synced > 0) {
      useHomeStore.getState().loadDashboard().catch(() => {});
    }

    // One toast per run, not per item. Combined when both happened, since a
    // second showToast() would just replace the first.
    const blocked = (await getQueue()).filter((q) => q.blocked).length;
    const syncedMsg = `${synced} receipt${synced !== 1 ? 's' : ''} synced`;
    const blockedMsg = `${blocked} receipt${blocked !== 1 ? 's are' : ' is'} waiting — upgrade to Pro to sync them`;
    if (blocked > 0) {
      useToastStore.getState().showToast(
        synced > 0 ? `${syncedMsg} · ${blockedMsg}` : blockedMsg, 'info', 4000);
    } else if (synced > 0) {
      useToastStore.getState().showToast(syncedMsg, 'success');
    }
  } finally {
    isProcessing = false;
  }

  return { synced, failed };
}
