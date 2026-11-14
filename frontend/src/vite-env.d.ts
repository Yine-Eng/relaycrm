/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" turns on the demo "View as" switcher and its API keys (see .env.development). */
  readonly VITE_DEMO_MODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
