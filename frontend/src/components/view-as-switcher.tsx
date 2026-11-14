import { CheckIcon, ChevronsUpDownIcon, LockIcon } from 'lucide-react'
import { toast } from 'sonner'

import { useMe } from '@/api/me'
import { DEMO_MODE, demoUsersByOrganization, roleLabel, type DemoUser } from '@/auth/demo-users'
import { useSession } from '@/auth/session'
import { RoleBadge } from '@/components/role-badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'

/**
 * "View as": which demo user the app acts as. Picking one changes the
 * X-Api-Key sent with every request and empties the query cache. The menu
 * also shows what GET /api/me answered, so the wiring is visible.
 *
 * Fully keyboard-operable (Radix menu): Enter, Space or ArrowDown opens it,
 * arrows move, Enter selects, Escape closes and returns focus to the button.
 */
export function ViewAsSwitcher() {
  if (!DEMO_MODE) {
    return <SignInNotConfigured />
  }
  return <DemoSwitcher />
}

function DemoSwitcher() {
  const { user, viewAs } = useSession()
  const me = useMe(user !== null)

  if (!user) {
    return <SignInNotConfigured />
  }

  const select = (slug: string) => {
    if (slug === user.slug) {
      return
    }
    viewAs(slug)
    const next = demoUsersByOrganization()
      .flatMap((group) => group.users)
      .find((candidate) => candidate.slug === slug)
    if (next) {
      toast(`Now viewing as ${next.name}`, { description: `${roleLabel(next.role)} at ${next.orgName}` })
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`View as ${user.name}, ${roleLabel(user.role)} at ${user.orgName} (demo). Change user`}
        className={cn(
          'group flex h-10 items-center gap-2.5 rounded-lg border bg-card pr-2 pl-1.5 text-left',
          'transition-colors duration-150 ease-out hover:bg-accent',
          'outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        )}
      >
        <UserAvatar user={user} />
        <span className="hidden min-w-0 flex-col leading-tight sm:flex">
          <span className="flex items-center gap-1.5 text-[13px] font-medium text-foreground">
            {user.name}
            <RoleBadge role={user.role} />
          </span>
          <span className="truncate text-xs text-muted-foreground">{user.orgName}</span>
        </span>
        <span className="ml-1 rounded-sm bg-primary px-1 py-px font-mono text-[10px] font-semibold tracking-wide text-primary-foreground uppercase">
          Demo
        </span>
        <ChevronsUpDownIcon className="size-3.5 text-subtle-foreground" aria-hidden />
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-72">
        <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">
          View as (demo users from the seed data)
        </DropdownMenuLabel>
        <DropdownMenuRadioGroup value={user.slug} onValueChange={select}>
          {demoUsersByOrganization().map((group, index) => (
            <DropdownMenuGroup key={group.orgName}>
              {index > 0 && <DropdownMenuSeparator />}
              <DropdownMenuLabel className="pt-2 font-mono text-[10.5px] tracking-[0.1em] text-subtle-foreground uppercase">
                {group.orgName}
              </DropdownMenuLabel>
              {group.users.map((candidate) => (
                <DropdownMenuRadioItem
                  key={candidate.slug}
                  value={candidate.slug}
                  className="gap-2.5 py-1.5"
                  aria-label={`${candidate.name}, ${roleLabel(candidate.role)} at ${candidate.orgName}`}
                >
                  <UserAvatar user={candidate} small />
                  <span className="flex-1">{candidate.name}</span>
                  <RoleBadge role={candidate.role} />
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuGroup>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <div className="px-2 py-2 text-xs" aria-live="polite">
          <p className="mb-1 font-mono text-[10.5px] tracking-[0.1em] text-subtle-foreground uppercase">
            GET /api/me
          </p>
          {me.isPending && <p className="text-muted-foreground">Asking the server…</p>}
          {me.isError && <p className="text-destructive">{me.error.message}</p>}
          {me.isSuccess && (
            <p className="flex items-center gap-1.5 font-mono text-muted-foreground tabular">
              <CheckIcon className="size-3.5 text-brand" aria-hidden />
              user {me.data.id} · org {me.data.orgId} · {me.data.role}
            </p>
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function UserAvatar({ user, small = false }: { user: DemoUser; small?: boolean }) {
  return (
    <Avatar className={small ? 'size-6' : 'size-7'}>
      <AvatarFallback
        className={cn(
          'border bg-secondary font-medium text-secondary-foreground',
          small ? 'text-[10px]' : 'text-[11px]',
        )}
      >
        {user.name.slice(0, 2).toUpperCase()}
      </AvatarFallback>
    </Avatar>
  )
}

/** Shown instead of the switcher when demo mode is off: real sign-in comes later. */
function SignInNotConfigured() {
  return (
    <p
      role="status"
      className="flex h-10 items-center gap-2 rounded-lg border border-dashed px-3 text-xs text-muted-foreground"
    >
      <LockIcon className="size-3.5" aria-hidden />
      Sign-in not configured
    </p>
  )
}
