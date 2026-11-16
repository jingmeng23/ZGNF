import { execFileSync } from 'node:child_process'
import { lstatSync, readdirSync, realpathSync, rmSync } from 'node:fs'
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

// Only known, reproducible output belongs here. Keep toolchains, dependency
// installations, database backups, local configuration and delivery packages.
const root = realpathSync(fileURLToPath(new URL('../', import.meta.url)))
const args = process.argv.slice(2)
if (args.some((arg) => arg !== '--apply')) {
  throw new Error('Usage: node scripts/clean.mjs [--apply] (default: preview)')
}
const apply = args.includes('--apply')
const candidates = [
  'target',
  'frontend/dist',
  'frontend/.npm-cache',
  'frontend/.screenshots',
  'frontend/android/.gradle',
  'frontend/android/build',
  'frontend/android/app/build',
  'frontend/android/capacitor-cordova-android-plugins/build',
  'frontend/android-commandlinetools.zip',
  'frontend/android-repository.xml',
  'frontend/jdk21.zip',
  'frontend/jdk21-assets.json',
  'frontend/gradle-8.11.1-bin.zip',
  'frontend/gradle-8.11.1-bin.zip.sha256',
]
for (const dir of ['artifacts', 'frontend']) {
  try {
    for (const entry of readdirSync(join(root, dir), { withFileTypes: true })) {
      if (entry.isFile() && entry.name.endsWith('.log')) candidates.push(`${dir}/${entry.name}`)
    }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }
}

function insideRoot(path) {
  const part = relative(root, path)
  return part !== '' && part !== '..' && !part.startsWith(`..${sep}`) && !isAbsolute(part)
}

const targets = candidates.filter((candidate) => {
  const path = resolve(root, candidate)
  if (!insideRoot(path)) throw new Error(`Outside workspace: ${candidate}`)
  try {
    // Check ancestors too: never clean through a junction or symbolic link.
    for (let current = path; current !== root; current = dirname(current)) {
      if (lstatSync(current).isSymbolicLink()) throw new Error(`Linked path: ${candidate}`)
    }
    if (!insideRoot(realpathSync(path))) throw new Error(`Outside workspace: ${candidate}`)
    return true
  } catch (error) {
    if (error.code === 'ENOENT') return false
    throw error
  }
})

if (apply && targets.length) {
  // Fail closed if Git cannot check for existing staged/unstaged work.
  for (const flags of [[], ['--cached']]) {
    const changed = execFileSync('git', ['diff', ...flags, '--name-only', '-z', '--', ...targets], {
      cwd: root,
      encoding: 'utf8',
    }).split('\0').filter(Boolean)
    // Missing files from an earlier cleanup do not prevent subsequent runs.
    const existing = changed.filter((path) => {
      try { lstatSync(join(root, path)); return true } catch (error) {
        if (error.code === 'ENOENT') return false
        throw error
      }
    })
    if (existing.length) throw new Error(`Preserve changed tracked files:\n${existing.join('\n')}`)
  }
}

for (const candidate of targets) {
  console.log(`${apply ? 'REMOVE' : 'PREVIEW'} ${candidate}`)
  if (apply) rmSync(join(root, candidate), { recursive: true, force: true })
}
console.log(`${targets.length} paths. ${apply ? 'Cleanup complete.' : 'Use --apply to remove them.'}`)
