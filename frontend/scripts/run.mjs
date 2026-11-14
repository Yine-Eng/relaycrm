// Runs one of the project's tools: node scripts/run.mjs <vite|vitest> [arguments]
// Used by the npm scripts (dev, build, preview, test).
//
// Why not call the tools directly: on Windows, a terminal can start in "c:\..." while Node
// resolves files as "C:\...". The tools then load some modules twice, under both spellings:
//   - Vitest (4 and 5) fails every test file with "failed to find the current suite";
//   - Vite bundles React twice, and the built app stops at "Cannot read properties of null
//     (reading 'useState')".
// Starting the tool from the upper-case spelling of the same folder avoids both. On macOS and
// Linux (CI, Netlify) this changes nothing.
import { spawnSync } from 'node:child_process'
import path from 'node:path'

const TOOLS = {
  vite: ['vite', 'bin', 'vite.js'],
  vitest: ['vitest', 'vitest.mjs'],
}

const [tool, ...args] = process.argv.slice(2)
const entry = tool === undefined ? undefined : TOOLS[tool]
if (!entry) {
  console.error(`Usage: node scripts/run.mjs <${Object.keys(TOOLS).join('|')}> [arguments]`)
  process.exit(2)
}

const cwd = process.cwd().replace(/^[a-z]:/, (drive) => drive.toUpperCase())
const result = spawnSync(process.execPath, [path.join(cwd, 'node_modules', ...entry), ...args], {
  cwd,
  stdio: 'inherit',
})
process.exit(result.status ?? 1)
