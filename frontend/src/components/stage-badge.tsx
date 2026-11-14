import { cn } from '@/lib/utils'
import { STAGE_LABELS, type Stage } from '@/lib/stages'

/** A deal's stage. The color comes from the --stage-* tokens (see styles/index.css). */
export function StageBadge({ stage, className }: { stage: Stage; className?: string }) {
  return (
    <span
      data-stage={stage}
      className={cn(
        'stage-badge inline-flex h-6 items-center gap-1.5 rounded-md px-2 text-xs font-medium',
        className,
      )}
    >
      <span aria-hidden className="size-1.5 rounded-full bg-current" />
      {STAGE_LABELS[stage]}
    </span>
  )
}
