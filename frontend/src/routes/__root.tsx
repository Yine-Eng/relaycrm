import type { QueryClient } from '@tanstack/react-query'
import { HeadContent, Link, Outlet, createRootRouteWithContext } from '@tanstack/react-router'
import { CompassIcon } from 'lucide-react'

import { AppShell } from '@/components/app-shell'
import { EmptyState } from '@/components/empty-state'
import { Button } from '@/components/ui/button'
import { Toaster } from '@/components/ui/sonner'

export interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  head: () => ({ meta: [{ title: 'Relay' }] }),
  component: RootLayout,
  notFoundComponent: NotFound,
})

function RootLayout() {
  return (
    <>
      <HeadContent />
      <AppShell>
        <Outlet />
      </AppShell>
      <Toaster position="bottom-right" />
    </>
  )
}

function NotFound() {
  return (
    <EmptyState
      icon={CompassIcon}
      eyebrow="404"
      title="Nothing here"
      description="This page does not exist. The sections are in the sidebar."
    >
      <Button asChild variant="outline">
        <Link to="/pipeline">Go to the pipeline</Link>
      </Button>
    </EmptyState>
  )
}
