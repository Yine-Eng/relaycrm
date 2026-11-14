import './styles/index.css'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { isApiError } from '@/api/errors'
import { SessionProvider } from '@/auth/session'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ThemeProvider } from '@/theme/theme'

import { routeTree } from './routeTree.gen'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 4xx will not change by asking again; a network error or 5xx is retried once.
      retry: (failures, error) =>
        failures < 1 && !(isApiError(error) && error.status >= 400 && error.status < 500),
      refetchOnWindowFocus: false,
    },
  },
})

const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: 'intent',
  scrollRestoration: true,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

const root = document.getElementById('root')
if (!root) {
  throw new Error('index.html has no #root element')
}

createRoot(root).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <TooltipProvider delayDuration={300}>
            <RouterProvider router={router} />
          </TooltipProvider>
        </SessionProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
