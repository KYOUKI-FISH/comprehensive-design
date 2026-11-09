import { spawn } from 'node:child_process'

const isWindows = process.platform === 'win32'
const npmCommand = isWindows ? 'npm.cmd' : 'npm'

const children = [
  {
    name: 'mock-server',
    child: spawn(process.execPath, ['mock-server/server.mjs'], { stdio: 'inherit' })
  },
  {
    name: 'vite',
    child: spawn(npmCommand, ['--prefix', 'frontend', 'run', 'dev'], {
      stdio: 'inherit',
      shell: isWindows
    })
  }
]

let shuttingDown = false

function shutdown(code = 0) {
  if (shuttingDown) return
  shuttingDown = true
  for (const { child } of children) {
    if (!child.killed) child.kill()
  }
  setTimeout(() => process.exit(code), 200)
}

process.on('SIGINT', () => shutdown(0))
process.on('SIGTERM', () => shutdown(0))

for (const { name, child } of children) {
  child.on('exit', (code, signal) => {
    if (!shuttingDown && code !== 0) {
      console.error(`[dev] ${name} 已退出，code=${code} signal=${signal ?? '-'}`)
      shutdown(code ?? 1)
    }
  })
}
