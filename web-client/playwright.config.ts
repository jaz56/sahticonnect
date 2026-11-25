import { defineConfig, devices } from '@playwright/test';

/**
 * Tests de bout en bout (bonus) contre la stack Docker complète (docker compose up -d) :
 * client sur http://localhost:4200, qui appelle la gateway (8080), qui relaie vers les services.
 *   npm run e2e            → parcours (e2e/parcours.e2e.ts)
 *   npm run captures       → captures d'écran de la documentation (docs/captures/)
 */
export default defineConfig({
  testDir: 'e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_URL'] ?? 'http://localhost:4200',
    locale: 'fr-FR',
    timezoneId: 'Africa/Tunis',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
});
