import { createFileRoute } from '@tanstack/react-router'
import { UserRoundSearchIcon } from 'lucide-react'

import { EmptyState } from '@/components/empty-state'

export const Route = createFileRoute('/leads')({
  head: () => ({ meta: [{ title: 'Leads · Relay' }] }),
  component: () => (
    <EmptyState
      icon={UserRoundSearchIcon}
      eyebrow="Coming soon"
      title="Leads"
      description="New, contacted and qualified leads, and the follow-ups that qualifying one creates."
    />
  ),
})
