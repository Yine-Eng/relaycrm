import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'

import { SessionProvider } from '@/auth/session'
import { ThemeProvider } from '@/theme/theme'

/** Renders with the app's providers (theme, queries, session) and a fresh query cache. */
export function renderWithProviders(ui: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const result = render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>{ui}</SessionProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  )
  return { ...result, queryClient }
}

/** A JSON response like the API's. */
export function jsonResponse(
  body: unknown,
  init: { status?: number; headers?: Record<string, string> } = {},
) {
  const status = init.status ?? 200
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json',
      ...init.headers,
    },
  })
}
