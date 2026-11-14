import { PUBLIC_DEMO } from '@/auth/demo-users'

/** Shown only on the hosted public demo (VITE_PUBLIC_DEMO): the data is shared with everyone. */
export function PublicDemoBanner() {
  if (!PUBLIC_DEMO) {
    return null
  }
  return (
    <div
      role="note"
      className="flex items-center justify-center gap-2 border-b bg-card px-4 py-1.5 text-center text-xs text-muted-foreground"
    >
      <span aria-hidden className="size-1.5 shrink-0 rounded-full bg-brand" />
      Public demo: seeded data, anyone can change it.
    </div>
  )
}
