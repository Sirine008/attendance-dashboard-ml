import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contre l'application déployée (par défaut la stack Docker : http://localhost:4200).
 * Lancement : `npx playwright test` (stack démarrée au préalable avec `docker compose up --build`).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
});
