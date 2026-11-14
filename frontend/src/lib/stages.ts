import type { components } from '@/api/schema'

/** A deal's stage, as the API defines it. */
export type Stage = NonNullable<components['schemas']['OpportunityRequest']['stage']>

export const STAGES: readonly Stage[] = ['PROSPECTING', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST']

export const STAGE_LABELS: Record<Stage, string> = {
  PROSPECTING: 'Prospecting',
  PROPOSAL: 'Proposal',
  NEGOTIATION: 'Negotiation',
  WON: 'Won',
  LOST: 'Lost',
}
