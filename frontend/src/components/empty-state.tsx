import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/** A page with nothing in it yet: what it will hold, and (optionally) what to do. */
export function EmptyState({
  icon: Icon,
  title,
  description,
  eyebrow,
  children,
  className,
}: {
  icon: LucideIcon
  title: string
  description: string
  eyebrow?: string
  children?: ReactNode
  className?: string
}) {
  return (
    <section
      className={cn(
        'relative flex flex-col items-center justify-center overflow-hidden rounded-xl border border-dashed bg-card/40 px-6 py-24 text-center',
        className,
      )}
    >
      {/* A faint 8px grid behind the content, fading out from the center. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 [background-image:linear-gradient(var(--border)_1px,transparent_1px),linear-gradient(90deg,var(--border)_1px,transparent_1px)] [mask-image:radial-gradient(ellipse_at_center,black_10%,transparent_65%)] [background-size:32px_32px] opacity-60"
      />
      <div className="relative flex flex-col items-center">
        <div className="mb-6 flex size-12 items-center justify-center rounded-xl border bg-card text-muted-foreground">
          <Icon className="size-5" aria-hidden />
        </div>
        {eyebrow && (
          <p className="mb-3 font-mono text-[11px] tracking-[0.12em] text-subtle-foreground uppercase">
            {eyebrow}
          </p>
        )}
        <h1 className="font-serif text-[44px] leading-[1.05] tracking-[-0.01em] text-foreground">{title}</h1>
        <p className="mt-3 max-w-md text-[15px] text-muted-foreground">{description}</p>
        {children && <div className="mt-8">{children}</div>}
      </div>
    </section>
  )
}
