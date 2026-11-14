import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { VIEW_AS_STORAGE_KEY } from '@/auth/session'
import { ViewAsSwitcher } from '@/components/view-as-switcher'

import { jsonResponse, renderWithProviders } from './render'

const ME: Record<string, { id: number; orgId: number; role: string }> = {
  'acme-rep-rita-key': { id: 3, orgId: 1, role: 'REP' },
  'acme-rep-ravi-key': { id: 4, orgId: 1, role: 'REP' },
  'globex-admin-key': { id: 5, orgId: 2, role: 'ADMIN' },
}

/** A fake /api/me that answers according to the X-Api-Key it receives, and records every key. */
function mockMe() {
  const keys: (string | null)[] = []
  vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => {
    const key = new Headers(init?.headers).get('X-Api-Key')
    keys.push(key)
    const me = key ? ME[key] : undefined
    return Promise.resolve(
      me ? jsonResponse(me) : jsonResponse({ title: 'Unauthorized', status: 401 }, { status: 401 }),
    )
  })
  return keys
}

describe('ViewAsSwitcher', () => {
  it('starts as Rita and calls /api/me with her key', async () => {
    const keys = mockMe()
    renderWithProviders(<ViewAsSwitcher />)

    expect(screen.getByRole('button', { name: /view as rita, rep at acme corp/i })).toBeInTheDocument()
    await waitFor(() => {
      expect(keys).toEqual(['acme-rep-rita-key'])
    })
  })

  it('switching user sends the new X-Api-Key, clears the cache and is remembered', async () => {
    const keys = mockMe()
    const user = userEvent.setup()
    const { queryClient } = renderWithProviders(<ViewAsSwitcher />)
    await waitFor(() => {
      expect(queryClient.getQueryData(['me'])).toEqual(ME['acme-rep-rita-key'])
    })

    await user.click(screen.getByRole('button', { name: /view as rita/i }))
    await user.click(await screen.findByRole('menuitemradio', { name: /gina, admin at globex inc/i }))

    expect(screen.getByRole('button', { name: /view as gina, admin at globex inc/i })).toBeInTheDocument()
    await waitFor(() => {
      expect(keys.at(-1)).toBe('globex-admin-key')
    })
    // Rita's answer was dropped, not reused: what is cached now is Gina's.
    await waitFor(() => {
      expect(queryClient.getQueryData(['me'])).toEqual(ME['globex-admin-key'])
    })
    expect(localStorage.getItem(VIEW_AS_STORAGE_KEY)).toBe('gina')
  })

  it('can be operated with the keyboard alone', async () => {
    const keys = mockMe()
    const user = userEvent.setup()
    renderWithProviders(<ViewAsSwitcher />)

    // Tab to the button and open the menu with Enter.
    await user.tab()
    const trigger = screen.getByRole('button', { name: /view as rita/i })
    expect(trigger).toHaveFocus()
    await user.keyboard('{Enter}')
    const menu = await screen.findByRole('menu')
    expect(menu).toBeInTheDocument()

    // Arrow down from the first item (Rita) to the second (Ravi) and choose it with Enter.
    await waitFor(() => {
      expect(screen.getByRole('menuitemradio', { name: /rita/i })).toHaveFocus()
    })
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitemradio', { name: /ravi/i })).toHaveFocus()
    await user.keyboard('{Enter}')

    // The menu closes, focus goes back to the button, and requests now carry Ravi's key.
    await waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
    const updated = screen.getByRole('button', { name: /view as ravi, rep at acme corp/i })
    await waitFor(() => {
      expect(updated).toHaveFocus()
    })
    await waitFor(() => {
      expect(keys.at(-1)).toBe('acme-rep-ravi-key')
    })

    // Escape closes the menu without changing anything.
    await user.keyboard('{Enter}')
    await screen.findByRole('menu')
    await user.keyboard('{Escape}')
    await waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: /view as ravi/i })).toBeInTheDocument()
  })

  it('shows "Sign-in not configured" and sends nothing when demo mode is off', async () => {
    vi.resetModules()
    vi.stubEnv('VITE_DEMO_MODE', 'false')
    const keys = mockMe()
    const { ViewAsSwitcher: Switcher } = await import('@/components/view-as-switcher')
    const { DEMO_USERS } = await import('@/auth/demo-users')
    const { renderWithProviders: renderFresh } = await import('./render')

    renderFresh(<Switcher />)

    expect(screen.getByRole('status')).toHaveTextContent('Sign-in not configured')
    expect(screen.queryByRole('button', { name: /view as/i })).not.toBeInTheDocument()
    expect(DEMO_USERS).toHaveLength(0)
    expect(keys).toEqual([])
    vi.unstubAllEnvs()
  })
})
