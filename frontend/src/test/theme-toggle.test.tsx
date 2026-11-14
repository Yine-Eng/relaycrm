import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { ThemeToggle } from '@/components/theme-toggle'
import { THEME_STORAGE_KEY, ThemeProvider } from '@/theme/theme'

function renderToggle() {
  return render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  )
}

describe('ThemeToggle', () => {
  it('is dark by default and switches to light and back', async () => {
    const user = userEvent.setup()
    renderToggle()

    expect(document.documentElement.dataset.theme).toBe('dark')
    await user.click(screen.getByRole('button', { name: 'Switch to light theme' }))

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
    expect(screen.getByRole('button', { name: 'Switch to dark theme' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Switch to dark theme' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('remembers the choice for the next visit', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light')
    renderToggle()

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(screen.getByRole('button', { name: 'Switch to dark theme' })).toBeInTheDocument()
  })

  it('works from the keyboard', async () => {
    const user = userEvent.setup()
    renderToggle()

    await user.tab()
    expect(screen.getByRole('button', { name: 'Switch to light theme' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(document.documentElement.dataset.theme).toBe('light')
    await user.keyboard(' ')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})
