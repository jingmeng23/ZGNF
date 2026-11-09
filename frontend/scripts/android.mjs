import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadEnv } from 'vite'

const root = fileURLToPath(new URL('../', import.meta.url))
const release = process.argv.includes('--release')
const buildApk = process.argv.includes('--apk')
const env = { ...loadEnv('android', root, ''), ...process.env }
// Prefer project-local tooling without changing the machine's Java installation.
const localJdkRoot = join(root, '.jdk')
if (existsSync(localJdkRoot)) {
  const localJdk = readdirSync(localJdkRoot).find((name) =>
    existsSync(join(localJdkRoot, name, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')),
  )
  if (localJdk) env.JAVA_HOME = join(localJdkRoot, localJdk)
}
if (existsSync(join(root, '.android-sdk', 'platforms')))
  env.ANDROID_HOME = join(root, '.android-sdk')
let url
try {
  url = new URL(env.VITE_API_BASE_URL)
} catch {
  throw new Error(
    '请在 .env.android.local 中设置完整 VITE_API_BASE_URL，例如 https://shop.example.com/api/v1。Android 无法使用网页开发代理。',
  )
}
if (
  !['http:', 'https:'].includes(url.protocol) ||
  url.username ||
  url.password ||
  url.search ||
  url.hash
)
  throw new Error('API 地址必须为不带账号、查询参数和片段的 HTTP(S) 地址。')
if (
  release &&
  (url.protocol !== 'https:' ||
    ['localhost', '127.0.0.1', '10.0.2.2', '[::1]'].includes(url.hostname))
)
  throw new Error('正式包必须配置手机可访问的 HTTPS 后端地址。')
if (release && (env.VITE_DEMO_MODE === 'true' || env.VITE_LOGIN_DISABLED === 'true'))
  throw new Error('正式包禁止启用演示数据或跳过登录。')
if (url.protocol !== 'https:' && env.ANDROID_ALLOW_HTTP !== 'true')
  throw new Error(
    'HTTP 仅用于调试。如确需局域网联调，请设置 ANDROID_ALLOW_HTTP=true；正式包禁止 HTTP。',
  )

function run(command, args, cwd = root) {
  const result = spawnSync(command, args, {
    cwd,
    env: { ...env, VITE_API_BASE_URL: url.href.replace(/\/$/, '') },
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status || 1)
}
run('npm', ['run', 'build', '--', '--mode', 'android'])
run('npx', ['cap', 'sync', 'android'])
if (buildApk || release) {
  if (release && !existsSync(new URL('../android/keystore.properties', import.meta.url)))
    throw new Error('正式签名需要 android/keystore.properties，请参阅 docs/android-app.md。')
  const localGradle = join(
    root,
    '.gradle-runtime',
    'gradle-8.11.1',
    'bin',
    process.platform === 'win32' ? 'gradle.bat' : 'gradle',
  )
  run(
    existsSync(localGradle)
      ? localGradle
      : process.platform === 'win32'
        ? '.\\gradlew.bat'
        : './gradlew',
    [
      release ? 'bundleRelease' : 'assembleDebug',
      '--gradle-user-home',
      '../.gradle-cache',
      ...(url.protocol === 'http:' ? ['-PallowHttp=true'] : []),
    ],
    fileURLToPath(new URL('../android/', import.meta.url)),
  )
}
