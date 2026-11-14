/**
 * Demo mode: pick which seeded user to "be". Each has the API key from the
 * backend's demo data (src/main/resources/db/demo/R__demo_data.sql).
 *
 * Only for local development against the demo profile. VITE_DEMO_MODE is read
 * at build time: when it is not "true" (any production build), the list below
 * is an empty array in the bundle and the keys are not shipped at all.
 */
export const DEMO_MODE: boolean = import.meta.env.VITE_DEMO_MODE === 'true'

export type Role = 'ADMIN' | 'MANAGER' | 'REP'

export interface DemoUser {
  /** Stable id for the switcher and for remembering the choice. */
  slug: string
  /** The user's id in the backend (what GET /api/me returns). */
  userId: number
  name: string
  role: Role
  orgId: number
  orgName: string
  apiKey: string
}

export const DEMO_USERS: readonly DemoUser[] = DEMO_MODE
  ? [
      {
        slug: 'rita',
        userId: 3,
        name: 'Rita',
        role: 'REP',
        orgId: 1,
        orgName: 'Acme Corp',
        apiKey: 'acme-rep-rita-key',
      },
      {
        slug: 'ravi',
        userId: 4,
        name: 'Ravi',
        role: 'REP',
        orgId: 1,
        orgName: 'Acme Corp',
        apiKey: 'acme-rep-ravi-key',
      },
      {
        slug: 'mark',
        userId: 2,
        name: 'Mark',
        role: 'MANAGER',
        orgId: 1,
        orgName: 'Acme Corp',
        apiKey: 'acme-manager-key',
      },
      {
        slug: 'alice',
        userId: 1,
        name: 'Alice',
        role: 'ADMIN',
        orgId: 1,
        orgName: 'Acme Corp',
        apiKey: 'acme-admin-key',
      },
      {
        slug: 'gina',
        userId: 5,
        name: 'Gina',
        role: 'ADMIN',
        orgId: 2,
        orgName: 'Globex Inc',
        apiKey: 'globex-admin-key',
      },
      {
        slug: 'greg',
        userId: 6,
        name: 'Greg',
        role: 'REP',
        orgId: 2,
        orgName: 'Globex Inc',
        apiKey: 'globex-rep-key',
      },
    ]
  : []

const ROLE_LABELS: Record<Role, string> = { ADMIN: 'Admin', MANAGER: 'Manager', REP: 'Rep' }

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role]
}

export function findDemoUser(slug: string | null): DemoUser | undefined {
  return DEMO_USERS.find((user) => user.slug === slug)
}

/** The organizations in switcher order, each with its users. */
export function demoUsersByOrganization(): { orgName: string; users: DemoUser[] }[] {
  const groups = new Map<string, DemoUser[]>()
  for (const user of DEMO_USERS) {
    groups.set(user.orgName, [...(groups.get(user.orgName) ?? []), user])
  }
  return [...groups].map(([orgName, users]) => ({ orgName, users }))
}
