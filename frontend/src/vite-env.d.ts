/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" turns on the demo "View as" switcher and its API keys (see .env.development). */
  readonly VITE_DEMO_MODE?: string
  /** "true" on the hosted public demo (netlify.toml): shows the "Public demo" banner. Needs VITE_DEMO_MODE. */
  readonly VITE_PUBLIC_DEMO?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
