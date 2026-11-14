import { QueryClient } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRouter } from '@tanstack/react-router'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/ui/tooltip'
import { routeTree } from '@/routeTree.gen'

import { jsonResponse, renderWithProviders } from './render'

const SIDEBAR_LINKS = ['Pipeline', 'Deals', 'Leads', 'Contacts', 'Tasks', 'Activity', 'Design system']

/** The real app (routes, shell, providers) at /pipeline, with /api/me answering. */
async function renderApp() {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 3, orgId: 1, role: 'REP' }))
  const router = createRouter({
    routeTree,
    context: { queryClient: new QueryClient() },
    history: createMemoryHistory({ initialEntries: ['/pipeline'] }),
  })
  renderWithProviders(
    <TooltipProvider>
      <RouterProvider router={router} />
    </TooltipProvider>,
  )
  return within(await screen.findByRole('complementary'))
}

describe('Sidebar tooltips', () => {
  it('shows the page name when a link gets keyboard focus, matching its accessible name', async () => {
    const user = userEvent.setup()
    const sidebar = await renderApp()

    for (const label of SIDEBAR_LINKS) {
      const link = sidebar.getByRole('link', { name: label })
      // Tab until this link has focus, as a keyboard user would.
      for (let presses = 0; document.activeElement !== link && presses < 30; presses++) {
        await user.tab()
      }
      expect(link).toHaveFocus()

      const tooltip = await screen.findByRole('tooltip')
      expect(tooltip).toHaveTextContent(label)
      expect(tooltip.textContent).toBe(link.getAttribute('aria-label'))
    }
  })

  it('shows the page name on hover, after a short delay', async () => {
    const user = userEvent.setup()
    const sidebar = await renderApp()

    await user.hover(sidebar.getByRole('link', { name: 'Tasks' }))
    await waitFor(() => {
      expect(screen.getByRole('tooltip')).toHaveTextContent('Tasks')
    })
    // Closing on pointer leave is not checked here: Radix keeps the tooltip open while the pointer
    // may be moving onto it, which needs real pointer coordinates that jsdom does not have.
  })
})
