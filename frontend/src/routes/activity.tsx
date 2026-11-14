import { createFileRoute } from '@tanstack/react-router'
import { ActivityIcon } from 'lucide-react'

import { EmptyState } from '@/components/empty-state'

export const Route = createFileRoute('/activity')({
  head: () => ({ meta: [{ title: 'Activity · Relay' }] }),
  component: () => (
    <EmptyState
      icon={ActivityIcon}
      eyebrow="Coming soon"
      title="Activity"
      description="Who changed what, and when: the audit trail of every record, as one feed."
    />
  ),
})
