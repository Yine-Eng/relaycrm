import { queryOptions, useQuery } from '@tanstack/react-query'

import { apiFetch } from './client'
import type { components } from './schema'

/** Who the server says the caller is: user id, organization and role, all taken from the API key. */
export type Me = components['schemas']['CurrentUser']

export const meQuery = queryOptions({
  queryKey: ['me'],
  queryFn: async ({ signal }) => (await apiFetch<Me>('/api/me', { signal })).data,
  staleTime: 60_000,
})

export function useMe(enabled = true) {
  return useQuery({ ...meQuery, enabled })
}
