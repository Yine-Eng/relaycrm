import { cn } from '@/lib/utils'

/**
 * The Relay mark: three stations on a path, the signal handed from one to the
 * next. Drawn for this project; also used as the favicon (public/favicon.svg).
 */
export function RelayMark({ className, title }: { className?: string; title?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      className={cn('size-7', className)}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <rect width="32" height="32" rx="9" fill="#C6F432" />
      <path
        d="M8.5 22.5 L15 12 L18.5 18 L23.5 9.5"
        fill="none"
        stroke="#0B0D12"
        strokeWidth="2.25"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="8.5" cy="22.5" r="2.75" fill="#0B0D12" />
      <circle cx="15" cy="12" r="2.25" fill="#0B0D12" />
      <circle cx="23.5" cy="9.5" r="3" fill="#C6F432" stroke="#0B0D12" strokeWidth="2.25" />
    </svg>
  )
}

/** Mark and wordmark, as used at the top of the sidebar. */
export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <RelayMark />
      {compact ? (
        <span className="sr-only">Relay</span>
      ) : (
        <span className="text-[17px] font-semibold tracking-[-0.02em] text-foreground">Relay</span>
      )}
    </span>
  )
}
