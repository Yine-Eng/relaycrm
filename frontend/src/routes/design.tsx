import { createFileRoute } from '@tanstack/react-router'
import { ArrowRightIcon, PlusIcon, SearchIcon, TrashIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { toast } from 'sonner'

import { RoleBadge } from '@/components/role-badge'
import { StageBadge } from '@/components/stage-badge'
import { STAGES } from '@/lib/stages'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Kbd, KbdGroup } from '@/components/ui/kbd'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import type { Theme } from '@/theme/theme'

export const Route = createFileRoute('/design')({
  head: () => ({ meta: [{ title: 'Design system · Relay' }] }),
  component: DesignPage,
})

/**
 * The living style guide: every token and component, rendered by the real
 * components in both themes side by side. Each panel carries data-theme, and
 * since all theming goes through CSS variables, that is all it takes.
 */
function DesignPage() {
  return (
    <div className="space-y-8">
      <header className="max-w-2xl">
        <p className="mb-3 font-mono text-[11px] tracking-[0.12em] text-subtle-foreground uppercase">
          Foundation
        </p>
        <h1 className="font-serif text-[52px] leading-none tracking-[-0.01em]">Design system</h1>
        <p className="mt-4 text-[15px] text-muted-foreground">
          Tokens and components as the app renders them, in both themes. Dark is the default. One accent, used
          sparingly; 1px borders instead of shadows; an 8px grid and a 10px radius.
        </p>
      </header>

      <div className="grid gap-6 xl:grid-cols-2">
        <ThemePanel theme="dark" />
        <ThemePanel theme="light" />
      </div>

      <p className="text-xs text-subtle-foreground">
        Dialogs and toasts open above the page, so they use the page&apos;s theme: switch it in the top bar to
        see the other one.
      </p>
    </div>
  )
}

function ThemePanel({ theme }: { theme: Theme }) {
  return (
    <section
      data-theme={theme}
      aria-label={`${theme === 'dark' ? 'Dark' : 'Light'} theme`}
      className="space-y-10 rounded-xl border bg-background p-6 text-foreground md:p-8"
    >
      <div className="flex items-center justify-between">
        <h2 className="font-serif text-[32px] leading-none">{theme === 'dark' ? 'Dark' : 'Light'}</h2>
        <span className="font-mono text-[11px] text-subtle-foreground">data-theme=&quot;{theme}&quot;</span>
      </div>

      <Section title="Color">
        <SwatchGroup
          label="Surfaces"
          tokens={['background', 'card', 'popover', 'highlight', 'border', 'input']}
        />
        <SwatchGroup
          label="Text"
          tokens={['foreground', 'muted-foreground', 'subtle-foreground', 'destructive']}
        />
        <SwatchGroup label="Accent" tokens={['primary', 'primary-foreground', 'brand', 'ring']} />
        <SwatchGroup
          label="Stages"
          tokens={['stage-prospecting', 'stage-proposal', 'stage-negotiation', 'stage-won', 'stage-lost']}
        />
      </Section>

      <Section title="Type">
        <div className="space-y-3">
          <TypeRow spec="Instrument Serif 44/1.05">
            <span className="font-serif text-[44px] leading-[1.05]">Close the quarter</span>
          </TypeRow>
          <TypeRow spec="Geist 24 semibold">
            <span className="text-2xl font-semibold tracking-[-0.015em]">Acme Rockets, fleet deal</span>
          </TypeRow>
          <TypeRow spec="Geist 16 medium">
            <span className="text-base font-medium">Follow up on proposal</span>
          </TypeRow>
          <TypeRow spec="Geist 14 (body)">
            <span className="text-sm">Moved from Proposal to Negotiation by Mark.</span>
          </TypeRow>
          <TypeRow spec="Geist 13 muted">
            <span className="text-[13px] text-muted-foreground">Due in 3 days, owned by Rita</span>
          </TypeRow>
          <TypeRow spec="Geist Mono 13">
            <span className="font-mono text-[13px] tabular">#112 · $20,000.00 · v7</span>
          </TypeRow>
        </div>
      </Section>

      <Section title="Badges">
        <div className="flex flex-wrap gap-2">
          {STAGES.map((stage) => (
            <StageBadge key={stage} stage={stage} />
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <RoleBadge role="ADMIN" />
          <RoleBadge role="MANAGER" />
          <RoleBadge role="REP" />
          <Badge>New</Badge>
          <Badge variant="secondary">Auto</Badge>
          <Badge variant="outline">Manual</Badge>
          <Badge variant="destructive">Overdue</Badge>
        </div>
      </Section>

      <Section title="Buttons">
        <div className="flex flex-wrap items-center gap-2">
          <Button>
            <PlusIcon aria-hidden /> New deal
          </Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="outline">Outline</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="destructive">
            <TrashIcon aria-hidden /> Delete
          </Button>
          <Button variant="link">
            Open history <ArrowRightIcon aria-hidden />
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm">Small</Button>
          <Button size="lg">Large</Button>
          <Button size="icon" variant="outline" aria-label="Search">
            <SearchIcon aria-hidden />
          </Button>
          <Button disabled>Disabled</Button>
        </div>
      </Section>

      <Section title="Inputs">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`${theme}-deal-name`}>Deal name</Label>
            <Input id={`${theme}-deal-name`} placeholder="Acme Rockets, fleet deal" />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${theme}-amount`}>Amount</Label>
            <Input
              id={`${theme}-amount`}
              defaultValue="-5"
              aria-invalid
              aria-describedby={`${theme}-amount-error`}
              className="font-mono"
            />
            <p id={`${theme}-amount-error`} className="text-xs text-destructive">
              must be greater than or equal to 0
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${theme}-disabled`}>Organization</Label>
            <Input id={`${theme}-disabled`} defaultValue="Acme Corp" disabled />
          </div>
        </div>
      </Section>

      <Section title="Card, avatar, keys, loading">
        <Card className="gap-4 py-5">
          <CardHeader className="px-5">
            <CardTitle className="flex items-center justify-between gap-3">
              Acme Rockets, fleet deal
              <StageBadge stage="PROPOSAL" />
            </CardTitle>
            <CardDescription className="font-mono text-xs tabular">#112 · $20,000.00 · v3</CardDescription>
          </CardHeader>
          <CardContent className="flex items-center justify-between px-5">
            <span className="flex items-center gap-2 text-[13px] text-muted-foreground">
              <Avatar className="size-6">
                <AvatarFallback className="border bg-secondary text-[10px]">RI</AvatarFallback>
              </Avatar>
              Rita
            </span>
            <KbdGroup>
              <Kbd>Ctrl</Kbd>
              <Kbd>K</Kbd>
            </KbdGroup>
          </CardContent>
        </Card>
        <div className="space-y-2" aria-label="Loading placeholder">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-8 w-full" />
        </div>
      </Section>

      <Section title="Overlays">
        <div className="flex flex-wrap gap-2">
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="outline">Open dialog</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Delete this deal?</DialogTitle>
                <DialogDescription>
                  Its open follow-up tasks are cancelled. The history stays readable for managers and admins.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose asChild>
                  <Button variant="ghost">Cancel</Button>
                </DialogClose>
                <DialogClose asChild>
                  <Button variant="destructive">Delete</Button>
                </DialogClose>
              </DialogFooter>
            </DialogContent>
          </Dialog>
          <Button
            variant="outline"
            onClick={() =>
              toast.success('Deal moved to Negotiation', { description: 'Version 4 · just now' })
            }
          >
            Show toast
          </Button>
        </div>
      </Section>
    </section>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="space-y-4">
      <h3 className="font-mono text-[11px] tracking-[0.12em] text-subtle-foreground uppercase">{title}</h3>
      {children}
    </div>
  )
}

function SwatchGroup({ label, tokens }: { label: string; tokens: string[] }) {
  return (
    <div>
      <p className="mb-2 text-xs text-muted-foreground">{label}</p>
      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {tokens.map((token) => (
          <li key={token} className="flex items-center gap-2.5 rounded-lg border p-1.5 pr-2">
            <span
              aria-hidden
              className="size-8 shrink-0 rounded-md border"
              style={{ backgroundColor: `var(--${token})` }}
            />
            <span className="min-w-0 leading-tight">
              <span className="block truncate text-xs">{token}</span>
              <TokenValue token={token} />
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Shows the value the token really has in this panel, read from the computed style. */
function TokenValue({ token }: { token: string }) {
  return (
    <span
      className="block font-mono text-[11px] text-subtle-foreground uppercase"
      ref={(element) => {
        if (element) {
          element.textContent = getComputedStyle(element).getPropertyValue(`--${token}`).trim()
        }
      }}
    />
  )
}

function TypeRow({ spec, children }: { spec: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 border-b pb-3 last:border-b-0 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
      <div className="min-w-0 truncate">{children}</div>
      <span className="shrink-0 font-mono text-[11px] text-subtle-foreground">{spec}</span>
    </div>
  )
}
