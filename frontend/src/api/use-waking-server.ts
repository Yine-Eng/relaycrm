import { useSyncExternalStore } from 'react'

import { isWakingServer, subscribeToWakingServer } from './client'

/** True while the API client is retrying because the server has not answered yet (a cold start). */
export function useWakingServer(): boolean {
  return useSyncExternalStore(subscribeToWakingServer, isWakingServer)
}
