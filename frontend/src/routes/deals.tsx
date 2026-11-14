import { createFileRoute } from '@tanstack/react-router'
import { BriefcaseIcon } from 'lucide-react'

import { EmptyState } from '@/components/empty-state'

export const Route = createFileRoute('/deals')({
  head: () => ({ meta: [{ title: 'Deals · Relay' }] }),
  component: () => (
    <EmptyState
      icon={BriefcaseIcon}
      eyebrow="Coming soon"
      title="Deals"
      description="Every opportunity in one list, with its amount, stage, owner and close date."
    />
  ),
})
