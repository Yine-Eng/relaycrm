import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  // Generated files: the API types (npm run gen:api) and the route tree (router plugin).
  globalIgnores(['dist', 'src/api/schema.d.ts', 'src/routeTree.gen.ts']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      // Providers export their hook next to them (useTheme, useSession), and storage keys as constants.
      'react-refresh/only-export-components': [
        'error',
        { allowConstantExport: true, allowExportNames: ['useTheme', 'useSession'] },
      ],
    },
  },
  {
    // Test setup fills in browser APIs that jsdom lacks with no-op stand-ins.
    files: ['src/test/**'],
    rules: {
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/unbound-method': 'off',
      '@typescript-eslint/no-unnecessary-condition': 'off',
    },
  },
  {
    // shadcn/ui components and route files export helpers next to components by design.
    files: ['src/components/ui/**', 'src/routes/**'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
  {
    files: ['eslint.config.js'],
    extends: [tseslint.configs.disableTypeChecked],
  },
])
