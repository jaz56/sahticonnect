import { expect, test } from '@playwright/test';
import { seConnecter } from './outils';

/**
 * Captures avant/après de la refonte visuelle (docs/captures/refonte/), mêmes données et même fenêtre (1440 × 900) :
 *   $env:REFONTE='avant'; $env:REFONTE_WF_CRITIQUE='WF-…'; $env:REFONTE_WF_COMPENSE='WF-…'; npx playwright test refonte
 * Ignoré sans REFONTE (ne réécrit pas les captures à chaque exécution des tests).
 */
const phase = process.env['REFONTE'];
const DOSSIER = '../docs/captures/refonte';

test.describe.configure({ mode: 'serial' });
test.skip(!phase, 'captures de la refonte : définir REFONTE=avant ou REFONTE=apres');

test('fiche patient', async ({ page }) => {
  await seConnecter(page, 'admin');
  await page.goto('/medecin/patients/PAT-0001');
  await expect(page.getByTestId('couverture')).toBeVisible();
  await expect(page.locator('h1')).toContainText('Ben Salah');
  await page.waitForTimeout(2000); // constantes, graphique et historique
  await page.screenshot({ path: `${DOSSIER}/${phase}-fiche-patient.png`, fullPage: true });
});

for (const [nom, variable] of [['workflow-critique', 'REFONTE_WF_CRITIQUE'], ['workflow-compense', 'REFONTE_WF_COMPENSE']]) {
  test(nom, async ({ page }) => {
    const id = process.env[variable];
    test.skip(!id, `${variable} non défini`);
    await seConnecter(page, 'admin');
    await page.goto(`/admin/workflows?id=${id}`);
    await expect(page.getByTestId('detail-workflow')).toBeVisible();
    await expect(page.getByTestId('etat-flux')).toContainText('connecté');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: `${DOSSIER}/${phase}-${nom}.png`, fullPage: true });
  });
}
