import { createFileRoute } from '@tanstack/react-router'
import { KanbanSquareIcon } from 'lucide-react'

import { EmptyState } from '@/components/empty-state'
import { StageBadge } from '@/components/stage-badge'
import { STAGES } from '@/lib/stages'

export const Route = createFileRoute('/pipeline')({
  head: () => ({ meta: [{ title: 'Pipeline · Relay' }] }),
  component: PipelinePage,
})

function PipelinePage() {
  return (
    <EmptyState
      icon={KanbanSquareIcon}
      eyebrow="Coming soon"
      title="Pipeline"
      description="A board of every open deal by stage, moved only along the steps the rules allow."
    >
      <ul aria-label="Stages" className="flex flex-wrap justify-center gap-2">
        {STAGES.map((stage) => (
          <li key={stage}>
            <StageBadge stage={stage} />
          </li>
        ))}
      </ul>
    </EmptyState>
  )
}
