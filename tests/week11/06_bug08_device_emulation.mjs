/**
 * BUG-08 复现/取证脚本：用 puppeteer-core 驱动本机 Edge，
 * 以真实设备模拟（viewport=375x667，布局视口即 375）访问登录页，
 * 输出 scrollWidth 与超宽元素清单，并截图。
 *
 * 用法：node repro.mjs <url> <outPrefix>
 */
import puppeteer from 'puppeteer-core'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const url = process.argv[2] ?? 'http://localhost:5173/login'
const prefix = process.argv[3] ?? 'emu'
const width = Number(process.argv[4] ?? 375)

const browser = await puppeteer.launch({
  executablePath: EDGE,
  headless: 'new',
  args: ['--disable-gpu', '--force-device-scale-factor=1']
})
try {
  const page = await browser.newPage()
  await page.setViewport({ width, height: 667, deviceScaleFactor: 1 })
  await page.goto(url, { waitUntil: 'networkidle0', timeout: 30000 })
  await new Promise((r) => setTimeout(r, 800))

  const report = await page.evaluate(() => {
    const vw = window.innerWidth
    const sw = document.scrollingElement.scrollWidth
    const offenders = []
    document.querySelectorAll('*').forEach((el) => {
      const r = el.getBoundingClientRect()
      if (r.width > vw + 1 || r.right > vw + 1) {
        const cls = typeof el.className === 'string'
          ? el.className.split(' ').filter(Boolean).slice(0, 2).join('.')
          : ''
        offenders.push(`${el.tagName.toLowerCase()}.${cls} w=${Math.round(r.width)} right=${Math.round(r.right)}`)
      }
    })
    return { vw, sw, overflow: sw > vw, offenders: offenders.slice(0, 40) }
  })

  console.log(JSON.stringify({ ...report, offenders: report.offenders }, null, 2))
  await page.screenshot({ path: `out/${prefix}.png` })
  console.log(`screenshot: out/${prefix}.png`)
} finally {
  await browser.close()
}
