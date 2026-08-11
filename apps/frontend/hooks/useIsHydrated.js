import { useSyncExternalStore } from 'react';

const subscribeToHydration = () => () => {};
const getClientHydrationSnapshot = () => true;
const getServerHydrationSnapshot = () => false;

const useIsHydrated = () => useSyncExternalStore(
  subscribeToHydration,
  getClientHydrationSnapshot,
  getServerHydrationSnapshot
);

export default useIsHydrated;
