// Starts Vitest with the arguments given (npm test, npm run test:watch).
//
// Why not call vitest directly: on Windows, a terminal can start in "c:\..." while Node
// resolves files as "C:\...". Vitest then loads its own runner twice, under both spellings,
// and every test file fails with "Vitest failed to find the current suite" (seen with
// Vitest 4 and 5). Starting it from the upper-case spelling of the same folder avoids
// that. On macOS and Linux this changes nothing.
import { spawnSync } from 'node:child_process'
import path from 'node:path'

const cwd = process.cwd().replace(/^[a-z]:/, (drive) => drive.toUpperCase())
const vitest = path.join(cwd, 'node_modules', 'vitest', 'vitest.mjs')

const result = spawnSync(process.execPath, [vitest, ...process.argv.slice(2)], { cwd, stdio: 'inherit' })
process.exit(result.status ?? 1)
