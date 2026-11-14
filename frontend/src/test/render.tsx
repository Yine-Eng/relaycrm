import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRouter } from '@tanstack/react-router'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'

import { SessionProvider } from '@/auth/session'
import { TooltipProvider } from '@/components/ui/tooltip'
import { routeTree } from '@/routeTree.gen'
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

/**
 * Renders the whole app (routes, shell, providers) at this path. The router loads the page
 * first (pages are split into lazily loaded chunks), so the first render is already complete.
 */
export async function renderAppAt(path: string) {
  const router = createRouter({
    routeTree,
    context: { queryClient: new QueryClient() },
    history: createMemoryHistory({ initialEntries: [path] }),
  })
  await router.load()
  return renderWithProviders(
    <TooltipProvider>
      <RouterProvider router={router} />
    </TooltipProvider>,
  )
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
