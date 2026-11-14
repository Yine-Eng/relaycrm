import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

export type Theme = 'dark' | 'light'

/** Same key as the inline script in index.html, which applies the theme before the first paint. */
export const THEME_STORAGE_KEY = 'relay.theme'

interface ThemeContextValue {
  theme: Theme
  setTheme: (theme: Theme) => void
  toggleTheme: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

/** The remembered theme, or dark (the default). Storage can be unavailable: then it is just dark. */
function readStoredTheme(): Theme {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY) === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}

function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // Not remembered (private window, blocked storage); the theme still applies for this visit.
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    const initial = readStoredTheme()
    document.documentElement.dataset.theme = initial
    return initial
  })

  const setTheme = useCallback((next: Theme) => {
    applyTheme(next)
    setThemeState(next)
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark')
  }, [setTheme, theme])

  const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [theme, setTheme, toggleTheme])
  return <ThemeContext value={value}>{children}</ThemeContext>
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used inside <ThemeProvider>')
  }
  return context
}
