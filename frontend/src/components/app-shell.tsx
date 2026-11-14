import { Link } from '@tanstack/react-router'
import {
  ActivityIcon,
  CheckSquareIcon,
  ContactIcon,
  KanbanSquareIcon,
  BriefcaseIcon,
  PaletteIcon,
  UserRoundSearchIcon,
  type LucideIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'

import { useMe } from '@/api/me'
import { useWakingServer } from '@/api/use-waking-server'
import { useSession } from '@/auth/session'
import { Brand } from '@/components/brand'
import { PublicDemoBanner } from '@/components/public-demo-banner'
import { ThemeToggle } from '@/components/theme-toggle'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { ViewAsSwitcher } from '@/components/view-as-switcher'
import { cn } from '@/lib/utils'

interface NavItem {
  to: '/pipeline' | '/deals' | '/leads' | '/contacts' | '/tasks' | '/activity'
  label: string
  icon: LucideIcon
}

const NAV: NavItem[] = [
  { to: '/pipeline', label: 'Pipeline', icon: KanbanSquareIcon },
  { to: '/deals', label: 'Deals', icon: BriefcaseIcon },
  { to: '/leads', label: 'Leads', icon: UserRoundSearchIcon },
  { to: '/contacts', label: 'Contacts', icon: ContactIcon },
  { to: '/tasks', label: 'Tasks', icon: CheckSquareIcon },
  { to: '/activity', label: 'Activity', icon: ActivityIcon },
]

const navLinkClass = cn(
  'group flex h-9 items-center gap-3 rounded-lg px-2.5 text-[13.5px] text-muted-foreground',
  'transition-colors duration-150 ease-out hover:bg-accent hover:text-foreground',
  'outline-none focus-visible:ring-2 focus-visible:ring-ring',
  'data-[status=active]:bg-accent data-[status=active]:text-foreground',
)

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh bg-background text-foreground">
      <a
        href="#main"
        className="sr-only z-50 rounded-lg bg-primary px-3 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <PublicDemoBanner />
        <TopBar />
        <main id="main" tabIndex={-1} className="flex-1 px-4 py-6 outline-none md:px-8 md:py-8">
          <div className="mx-auto w-full max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  )
}

function Sidebar() {
  return (
    <aside className="sticky top-0 hidden h-dvh w-16 shrink-0 flex-col border-r bg-card/50 md:flex lg:w-60">
      <div className="flex h-16 items-center px-4">
        <Link to="/pipeline" className="rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <span className="lg:hidden">
            <Brand compact />
          </span>
          <span className="hidden lg:inline">
            <Brand />
          </span>
        </Link>
      </div>
      <nav aria-label="Main" className="flex flex-1 flex-col gap-0.5 px-3 py-2">
        {NAV.map((item) => (
          <SidebarLink key={item.to} {...item} />
        ))}
      </nav>
      <div className="border-t px-3 py-3">
        <SidebarLink to="/design" label="Design system" icon={PaletteIcon} />
      </div>
    </aside>
  )
}

/**
 * One sidebar link. Between the md and lg breakpoints the sidebar shows icons only, so the
 * page name appears in a tooltip on hover and on keyboard focus: the same text as the
 * link's accessible name. With the full sidebar (lg and up) the label is visible and the
 * tooltip stays hidden.
 */
function SidebarLink({
  to,
  label,
  icon: Icon,
}: {
  to: NavItem['to'] | '/design'
  label: string
  icon: LucideIcon
}) {
  return (
    <Tooltip delayDuration={150}>
      <TooltipTrigger asChild>
        <Link to={to} className={navLinkClass} aria-label={label}>
          <Icon className="size-4 shrink-0 group-data-[status=active]:text-brand" aria-hidden />
          <span className="hidden lg:inline">{label}</span>
        </Link>
      </TooltipTrigger>
      <TooltipContent side="right" sideOffset={8} className="lg:hidden">
        {label}
      </TooltipContent>
    </Tooltip>
  )
}

function TopBar() {
  return (
    <div className="sticky top-0 z-30">
      <TopBarRow />
      <WakingNoticeForSmallScreens />
    </div>
  )
}

function TopBarRow() {
  return (
    <header className="flex h-16 items-center gap-3 border-b bg-background/85 px-4 backdrop-blur md:px-8">
      <span className="md:hidden">
        <Brand compact />
      </span>
      <MobileNav />
      <div className="flex-1" />
      <ApiStatus />
      <ViewAsSwitcher />
      <ThemeToggle />
    </header>
  )
}

/** Small screens: the sidebar is hidden, so the sections sit in a scrollable row. */
function MobileNav() {
  return (
    <nav aria-label="Main" className="-mx-1 flex min-w-0 gap-1 overflow-x-auto px-1 md:hidden">
      {NAV.map(({ to, label }) => (
        <Link key={to} to={to} className={cn(navLinkClass, 'h-8 shrink-0 px-2 text-[13px]')}>
          {label}
        </Link>
      ))}
    </nav>
  )
}

/** While the hosted demo's server wakes up (see api/client.ts): what is happening, and for how long. */
export const WAKING_FULL = 'Waking up the demo server… this can take up to 2 minutes on the free plan.'
export const WAKING_SHORT = 'Waking the server (up to 2 min)…'

/** The API status as the top bar shows it: connected, unreachable, waking up, or still checking. */
function useApiState() {
  const { user } = useSession()
  const me = useMe(user !== null)
  const waking = useWakingServer()
  if (!user) {
    return null
  }
  return me.isSuccess ? 'connected' : me.isError ? 'error' : waking ? 'waking' : 'checking'
}

/**
 * On a phone the top bar has no room for the waking message next to the user switcher, so it
 * gets its own full-width line under the bar instead, with the whole sentence.
 */
function WakingNoticeForSmallScreens() {
  if (useApiState() !== 'waking') {
    return null
  }
  return (
    <p
      role="status"
      className="flex items-center gap-2 border-b bg-background/95 px-4 py-2 text-xs text-muted-foreground sm:hidden"
    >
      <span
        aria-hidden
        className="size-2 shrink-0 animate-pulse rounded-full bg-stage-negotiation motion-reduce:animate-none"
      />
      {WAKING_FULL}
    </p>
  )
}

/**
 * A live dot that turns lime once GET /api/me has answered for the current user. While the
 * client is retrying because the server is still starting (the hosted demo sleeps when idle),
 * it says so instead of looking broken.
 */
function ApiStatus() {
  const state = useApiState()
  if (!state) {
    return null
  }

  const label = {
    connected: 'API connected',
    error: 'API unreachable',
    waking: (
      <>
        {/* The full sentence where the top bar has room for it, shorter ones where it has less. */}
        <span className="hidden xl:inline">{WAKING_FULL}</span>
        <span className="xl:hidden">{WAKING_SHORT}</span>
      </>
    ),
    checking: 'Connecting…',
  }[state]

  return (
    <span
      role="status"
      className={cn(
        'shrink-0 items-center gap-2 text-xs whitespace-nowrap text-muted-foreground',
        // Normally only on wide screens. While waking, from small tablets up, so nobody thinks the app is
        // broken; phones get the notice under the top bar instead (WakingNoticeForSmallScreens).
        state === 'waking' ? 'hidden sm:flex' : 'hidden lg:flex',
      )}
    >
      <span className="relative flex size-2">
        {state === 'connected' && (
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand opacity-50 motion-reduce:hidden" />
        )}
        <span
          className={cn(
            'relative inline-flex size-2 rounded-full',
            state === 'connected' && 'bg-brand',
            state === 'error' && 'bg-destructive',
            state === 'checking' && 'bg-subtle-foreground',
            state === 'waking' && 'animate-pulse bg-stage-negotiation motion-reduce:animate-none',
          )}
        />
      </span>
      {label}
    </span>
  )
}
