/// <reference types="vitest/config" />
import path from 'node:path'

import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [
    // Must come before the React plugin: it generates src/routeTree.gen.ts from src/routes/.
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, 'src') },
  },
  // Pre-bundle these at startup. Otherwise Vite discovers them while the first page is loading,
  // bundles them a second time, and that page can end up with two copies of React ("Invalid hook call").
  optimizeDeps: {
    include: [
      'react',
      'react-dom/client',
      'radix-ui',
      'sonner',
      'lucide-react',
      'class-variance-authority',
      'clsx',
      'tailwind-merge',
      '@tanstack/react-query',
      '@tanstack/react-router',
    ],
  },
  server: {
    port: 5173,
    // The browser only ever talks to the Vite server, so the backend needs no CORS setup.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    // Tests run in "test" mode, which does not read .env.development; demo mode is on unless a test turns it off.
    env: { VITE_DEMO_MODE: 'true' },
  },
})
