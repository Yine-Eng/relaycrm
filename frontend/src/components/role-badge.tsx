import { roleLabel, type Role } from '@/auth/demo-users'
import { cn } from '@/lib/utils'

/** A user's role. Admins get the accent outline: they are the only ones who can delete. */
export function RoleBadge({ role, className }: { role: Role; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex h-5 items-center rounded-md border px-1.5 font-mono text-[10.5px] font-medium tracking-wide uppercase',
        role === 'ADMIN' ? 'border-brand/50 text-brand' : 'border-border text-muted-foreground',
        className,
      )}
    >
      {roleLabel(role)}
    </span>
  )
}
