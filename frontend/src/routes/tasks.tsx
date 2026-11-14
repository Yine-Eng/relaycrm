import { createFileRoute } from '@tanstack/react-router'
import { CheckSquareIcon } from 'lucide-react'

import { EmptyState } from '@/components/empty-state'

export const Route = createFileRoute('/tasks')({
  head: () => ({ meta: [{ title: 'Tasks · Relay' }] }),
  component: () => (
    <EmptyState
      icon={CheckSquareIcon}
      eyebrow="Coming soon"
      title="Tasks"
      description="Your follow-ups, soonest first. The automatic ones appear as deals and leads move."
    />
  ),
})
