// Strumento di sviluppo (non fa parte dell'app): screenshot reali con Edge/Chrome via DevTools Protocol.
// Emula un dispositivo (larghezza, densità, touch), imposta il tema e, se indicato, accede con un utente demo.
// Uso: node shot.mjs <baseUrl> <out-dir> <spec.json>
// spec: [{ "name": "home-mobile-dark", "path": "/", "width": 390, "height": 844, "mobile": true,
//          "theme": "dark", "login": { "email": "...", "password": "..." }, "fullPage": false }]
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const [baseUrl, outDir, specPath] = process.argv.slice(2)
const specs = JSON.parse(readFileSync(specPath, 'utf8'))
const browser = process.env.BROWSER_PATH || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const port = 9333
const profile = join(tmpdir(), `shot-profile-${Date.now()}`)
mkdirSync(outDir, { recursive: true })

const proc = spawn(browser, ['--headless=new', '--disable-gpu', '--no-first-run', '--hide-scrollbars',
  `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function pageSocket() {
  for (let i = 0; i < 50; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
      const page = list.find((t) => t.type === 'page')
      if (page) return page.webSocketDebuggerUrl
    } catch { /* il browser sta partendo */ }
    await sleep(200)
  }
  throw new Error('browser non raggiungibile')
}

const ws = new WebSocket(await pageSocket())
await new Promise((r) => ws.addEventListener('open', r, { once: true }))
let nextId = 1
const pending = new Map()
ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data)
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg)
    pending.delete(msg.id)
  }
})
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = nextId++
  pending.set(id, (msg) => (msg.error ? reject(new Error(`${method}: ${msg.error.message}`)) : resolve(msg.result)))
  ws.send(JSON.stringify({ id, method, params }))
})
const evaluate = async (expression) =>
  (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result.value

await send('Page.enable')
await send('Runtime.enable')
const report = []
for (const s of specs) {
  await send('Emulation.setDeviceMetricsOverride', {
    width: s.width, height: s.height, deviceScaleFactor: s.mobile ? 2 : 1, mobile: Boolean(s.mobile),
  })
  await send('Emulation.setTouchEmulationEnabled', { enabled: Boolean(s.mobile) })
  await send('Emulation.setEmulatedMedia', { features: [
    { name: 'prefers-color-scheme', value: s.theme === 'dark' ? 'dark' : 'light' },
    { name: 'prefers-reduced-motion', value: 'reduce' },
  ] })
  await send('Page.navigate', { url: `${baseUrl}/login` })
  await sleep(1500)
  await evaluate(`localStorage.setItem('valhalla-theme', ${JSON.stringify(s.theme || 'light')}); true`)
  let loginStatus
  if (s.login) {
    loginStatus = await evaluate(`fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin', body: JSON.stringify(${JSON.stringify(s.login)}) })
      .then(async (r) => r.status + ' cookie=' + (r.headers.get('set-cookie') ? 'si' : 'nascosto') + ' ' + (await r.text()).slice(0, 80))`)
  }
  await send('Page.navigate', { url: `${baseUrl}${s.path}` })
  await sleep(s.wait || 3500)
  const metrics = await evaluate(`({ scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth, height: document.documentElement.scrollHeight,
    theme: document.documentElement.getAttribute('data-bs-theme') })`)
  const clip = s.fullPage ? { x: 0, y: 0, width: s.width, height: Math.min(metrics.height, 6000), scale: 1 } : undefined
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: Boolean(s.fullPage), clip })
  writeFileSync(join(outDir, `${s.name}.png`), Buffer.from(shot.data, 'base64'))
  report.push({ name: s.name, ...metrics, overflowX: metrics.scrollWidth > metrics.clientWidth, loginStatus })
}
console.log(JSON.stringify(report, null, 2))
ws.close()
proc.kill()
await sleep(500)
try { rmSync(profile, { recursive: true, force: true }) } catch { /* profilo ancora in uso */ }
