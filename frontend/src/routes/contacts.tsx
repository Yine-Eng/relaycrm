import { createFileRoute } from '@tanstack/react-router'
import { ContactIcon } from 'lucide-react'

import { EmptyState } from '@/components/empty-state'

export const Route = createFileRoute('/contacts')({
  head: () => ({ meta: [{ title: 'Contacts · Relay' }] }),
  component: () => (
    <EmptyState
      icon={ContactIcon}
      eyebrow="Coming soon"
      title="Contacts"
      description="The people behind your deals, each with the deals they are linked to."
    />
  ),
})
