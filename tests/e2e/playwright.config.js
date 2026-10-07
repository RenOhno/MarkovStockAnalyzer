import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.', testMatch: 'integration.spec.js', fullyParallel: false, workers: 1, retries: 0,
  timeout: 180_000, expect: { timeout: 20_000 },
  outputDir: 'artifacts/test-results',
  reporter: [['list'], ['json', { outputFile: 'artifacts/results.json' }], ['html', { outputFolder: 'artifacts/report', open: 'never' }]],
  use: { ...devices['Desktop Chrome'], channel: 'chromium', baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8081',
    viewport: { width: 1280, height: 900 }, locale: 'ja-JP', timezoneId: 'Asia/Tokyo',
    reducedMotion: 'reduce', screenshot: 'only-on-failure', trace: 'retain-on-failure' }
});
