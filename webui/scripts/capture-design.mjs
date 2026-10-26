// Capture the real frontend in an isolated browser. Sample responses exist only
// in this script; no backend accounts, product records or payments are created.
import { chromium } from '@playwright/test'
import { createServer } from 'vite'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const output = resolve('test-results/design-restored')
await mkdir(output, { recursive: true })
const server = await createServer({
  server: { host: '127.0.0.1', port: 4180, strictPort: true, open: false },
  logLevel: 'error',
})
let browser
try {
  await server.listen()
  const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  browser = await chromium.launch({
    headless: true,
    ...(existsSync(edge) ? { executablePath: edge } : {}),
  })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    reducedMotion: 'reduce',
  })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  const now = Math.floor(Date.now() / 1000)
  const categories = [
    '食品饮品',
    '学习文具',
    '宿舍日用',
    '运动户外',
    '数码配件',
    '个护清洁',
    '图书阅读',
  ].map((name, index) => ({ id: index + 1, name }))
  const products = [
    ['早餐全麦面包组合', 990, 1],
    ['课堂笔记本 · 轻便随身装', 1280, 2],
    ['宿舍桌面可折叠收纳盒 · 整理书桌与日常小物', 1590, 3],
    ['社区运动水杯', 1990, 4],
    ['自习文具套装', 890, 2],
    ['课间零食分享包', 1680, 1],
    ['宿舍洗护组合', 2490, 6],
    ['轻便运动跳绳', 1290, 4],
    ['便携充电线收纳包', 790, 5],
    ['读书笔记与书签组合', 1150, 7],
    ['宿舍清洁小工具', 690, 6],
    ['午后茶饮组合', 1490, 1],
  ].map(([name, price, categoryId], index) => ({
    id: index + 1,
    name: `示例 · ${name}`,
    description: '界面预览示例，非实际上架商品。规格与交易信息以正式团购说明为准。',
    price,
    categoryId,
    stock: index === 10 ? 0 : 20 + index * 3,
    status: 0,
    owner: `community_${String.fromCharCode(65 + (index % 4))}`,
    startTime: index === 11 ? now + 86400 : now - 3600,
    endTime: now + 86400 * (index === 11 ? 3 : 2),
    shipmentCount: 0,
    version: 1,
  }))
  const paged = (content, count = content.length) => ({ content, count, page: 0, size: 12 })
  await context.addInitScript(() =>
    sessionStorage.setItem('communibuy.token', 'isolated-design-preview')
  )
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    let data
    if (request.method() !== 'GET') {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          code: 'VISUAL_PREVIEW_ONLY',
          message: 'This is a visual preview, not a backend.',
          data: null,
        }),
      })
    }
    if (url.pathname === '/api/v1/auth')
      data = { id: 'preview_student', nickname: '预览邻居', description: '', address: '', role: 0 }
    else if (url.pathname === '/api/v1/categories') data = paged(categories)
    else if (url.pathname === '/api/v1/item') {
      const filtered = products.filter(
        (item) =>
          (!url.searchParams.has('category') ||
            String(item.categoryId) === url.searchParams.get('category')) &&
          (!url.searchParams.has('keyword') || item.name.includes(url.searchParams.get('keyword')))
      )
      const size = Number(url.searchParams.get('size') || 8)
      const pageNumber = Number(url.searchParams.get('page') || 0)
      data = {
        content: filtered.slice(pageNumber * size, (pageNumber + 1) * size),
        count: filtered.length,
        page: pageNumber,
        size,
      }
    } else if (url.pathname.match(/^\/api\/v1\/item\/\d+\/reviews$/)) data = paged([])
    else if (url.pathname.match(/^\/api\/v1\/item\/\d+$/))
      data = products.find((item) => String(item.id) === url.pathname.split('/').at(-1))
    else return route.abort()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        code: 'OK',
        message: '',
        data,
        traceId: '00000000000000000000000000000000',
      }),
    })
  })
  async function capture(name, width, height, pathname, selector) {
    await page.setViewportSize({ width, height })
    await page.goto(`http://127.0.0.1:4180${pathname}`)
    await page.locator(selector).first().waitFor()
    await page.evaluate(() => document.fonts.ready)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)
    if (overflow) throw new Error(`Horizontal overflow in ${name}`)
    await page.screenshot({ path: resolve(output, `${name}.png`), fullPage: true })
    console.log(`Captured ${name}`)
  }
  await capture('home-desktop', 1440, 1000, '/', '.item-card')
  await capture('home-tablet', 1024, 900, '/', '.item-card')
  await capture('home-mobile', 390, 844, '/', '.item-card')
  await capture('item-desktop', 1440, 1000, '/items/1', '.purchase-panel')
  await capture('item-mobile', 390, 844, '/items/1', '.purchase-panel')
  await capture('login-desktop', 1440, 960, '/login', '.auth-card')
  await capture('login-mobile', 390, 844, '/login', '.auth-card')
  if (errors.length) throw new Error(errors.join('\n'))
  console.log(`Design previews saved to ${output}; data is illustrative only.`)
} finally {
  await browser?.close()
  await server.close()
}
