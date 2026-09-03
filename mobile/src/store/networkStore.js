import { create } from 'zustand';
import NetInfo from '@react-native-community/netinfo';

import { processQueue } from '../utils/syncQueue';

const useNetworkStore = create(() => ({
  isConnected: true, // optimistic until the first real NetInfo event arrives
  initialized: false, // true once the first NetInfo event has been received
}));

let unsubscribe = null;

// Idempotent — calling this more than once (e.g. from multiple screens) is a
// no-op after the first call, since only one listener/subscription is needed.
export function initNetworkListener() {
  if (unsubscribe) return;

  unsubscribe = NetInfo.addEventListener((state) => {
    // isInternetReachable is null while NetInfo is still determining
    // reachability (common right after the listener starts) — treat that as
    // reachable so we don't flash 'offline' before a real reading comes in.
    // Only an explicit false counts as offline.
    const reachable = state.isInternetReachable !== false;
    const isConnected = !!state.isConnected && reachable;

    const wasConnected = useNetworkStore.getState().isConnected;

    useNetworkStore.setState({ isConnected, initialized: true });

    if (wasConnected === false && isConnected === true) {
      processQueue().catch((err) => {
        console.error('processQueue failed:', err);
      });
    }
  });
}

export default useNetworkStore;
