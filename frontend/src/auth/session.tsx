import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

import { setApiKey } from '@/api/client'

import { DEMO_USERS, findDemoUser, type DemoUser } from './demo-users'

/** Remembers which demo user was picked, per browser. */
export const VIEW_AS_STORAGE_KEY = 'relay.viewAs'

interface SessionContextValue {
  /** The demo user requests are made as, or null when demo mode is off (no sign-in yet). */
  user: DemoUser | null
  viewAs: (slug: string) => void
}

const SessionContext = createContext<SessionContextValue | null>(null)

function readStoredUser(): DemoUser | null {
  let slug: string | null = null
  try {
    slug = localStorage.getItem(VIEW_AS_STORAGE_KEY)
  } catch {
    // Storage unavailable: start as the first demo user.
  }
  return findDemoUser(slug) ?? DEMO_USERS[0] ?? null
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<DemoUser | null>(() => {
    const initial = readStoredUser()
    // Set before the first render, so the very first request already carries the key.
    setApiKey(initial?.apiKey ?? null)
    return initial
  })

  const viewAs = useCallback(
    (slug: string) => {
      const next = findDemoUser(slug)
      if (!next) {
        return
      }
      setApiKey(next.apiKey)
      // Nothing fetched as the previous user may be shown to the next one.
      queryClient.clear()
      try {
        localStorage.setItem(VIEW_AS_STORAGE_KEY, next.slug)
      } catch {
        // Not remembered; the switch still applies for this visit.
      }
      setUser(next)
    },
    [queryClient],
  )

  const value = useMemo(() => ({ user, viewAs }), [user, viewAs])
  return <SessionContext value={value}>{children}</SessionContext>
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext)
  if (!context) {
    throw new Error('useSession must be used inside <SessionProvider>')
  }
  return context
}
