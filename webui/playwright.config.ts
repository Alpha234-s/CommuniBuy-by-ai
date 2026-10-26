import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'

const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 30000,
  reporter: 'list',
  globalSetup: './tests/e2e/setup.ts',
  use: {
    baseURL: 'http://127.0.0.1:4179',
    headless: true,
    launchOptions: existsSync(edgePath) ? { executablePath: edgePath } : {},
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
})
