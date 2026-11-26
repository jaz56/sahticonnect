import { expect, test } from '@playwright/test';
import { choisirOption, ouvrirSession, seConnecter, simulerAlerte } from './outils';

/**
 * Captures d'écran de la documentation (docs/captures/), prises contre la stack Docker :
 *   npm run captures
 * Pour des constantes à l'écran, lancer d'abord le capteur (tachycardie de PAT-0001).
 * Connexion Keycloak : admin (accès à tous les écrans), dr.chaabane pour les alertes, ahmed pour l'accès refusé.
 */
const DOSSIER = '../docs/captures';

test.describe.configure({ mode: 'serial' });

test('connexion Keycloak', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('connexion').first().click();
  await page.locator('#username').fill('dr.chaabane');
  await page.screenshot({ path: `${DOSSIER}/11-connexion-keycloak.png` });
});

test('accueil', async ({ page }) => {
  await seConnecter(page, 'admin');
  await expect(page.getByTestId('etat-gateway')).toContainText('UP');
  await page.screenshot({ path: `${DOSSIER}/01-accueil.png`, fullPage: true });
});

test('alertes en direct et workflow', async ({ browser }) => {
  const medecin = await ouvrirSession(browser, 'dr.chaabane');
  const websocket = medecin.waitForEvent('websocket');
  await medecin.goto('/medecin/alertes');
  await websocket;
  await medecin.waitForTimeout(1500);
  const admin = await ouvrirSession(browser, 'admin');
  const critique = await simulerAlerte(admin, 'PAT-0001', 'CRITIQUE');
  await simulerAlerte(admin, 'PAT-0002', 'ELEVEE');
  await expect(medecin.locator(`[data-alerte="${critique.alerteId}"]`)).toBeVisible({ timeout: 20_000 });
  await medecin.waitForTimeout(3000); // interventions créées par les workflows
  await medecin.screenshot({ path: `${DOSSIER}/05-alertes-direct.png` });

  await admin.goto(`/admin/workflows?id=${critique.workflowId}`);
  await expect(admin.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'FACTUREE', { timeout: 30_000 });
  await admin.screenshot({ path: `${DOSSIER}/06-workflows.png` });
});

test('prise de RDV', async ({ page }) => {
  await seConnecter(page, 'ahmed');
  await page.goto('/patient/prendre-rdv');
  await choisirOption(page, 'choix-medecin', /Chaabane/);
  const demain = new Date(Date.now() + 2 * 86400000);
  if (demain.getDay() === 0) demain.setDate(demain.getDate() + 1);
  await page.getByTestId('choix-date').fill(demain.toISOString().slice(0, 10));
  await page.getByTestId('choix-date').press('Tab');
  await expect(page.getByTestId('creneaux')).toBeVisible();
  await page.screenshot({ path: `${DOSSIER}/02-prise-rdv.png` });
});

test('tableau de bord et fiche patient', async ({ page }) => {
  await seConnecter(page, 'dr.chaabane');
  await page.goto('/medecin/tableau-de-bord');
  await expect(page.locator('[data-patient="PAT-0001"]')).toBeVisible();
  await page.screenshot({ path: `${DOSSIER}/03-tableau-de-bord.png` });

  await page.goto('/medecin/patients/PAT-0001');
  await expect(page.getByTestId('couverture')).toBeVisible();
  await page.waitForTimeout(6000); // un rafraîchissement du graphique
  await page.screenshot({ path: `${DOSSIER}/04-fiche-patient.png`, fullPage: true });
});

test('CNAM (SOAP)', async ({ page }) => {
  await seConnecter(page, 'admin');
  await page.goto('/admin/cnam');
  await page.getByTestId('envoyer-verifier').click();
  await expect(page.getByTestId('resultat-verifier')).toBeVisible();
  await page.screenshot({ path: `${DOSSIER}/07-cnam-soap.png`, fullPage: true });

  await page.getByTestId('vignette').fill('VG-2026-000003'); // vignette d'un autre patient → Fault
  await page.getByTestId('envoyer-verifier').click();
  await expect(page.getByTestId('soap-fault')).toContainText('VIGNETTE_INVALIDE');
  await page.getByRole('tab', { name: /Réponse brute/ }).click();
  await page.screenshot({ path: `${DOSSIER}/08-cnam-fault-xml.png`, fullPage: true });
});

test("saga d'escalade : workflow compensé", async ({ page }) => {
  await seConnecter(page, 'admin');
  const elevee = await simulerAlerte(page, 'PAT-0005', 'ELEVEE');
  await page.goto(`/admin/workflows?id=${elevee.workflowId}`);
  await expect(page.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'FACTUREE', { timeout: 30_000 });
  await simulerAlerte(page, 'PAT-0005', 'CRITIQUE');
  await page.goto(`/admin/workflows?id=${elevee.workflowId}`);
  await expect(page.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'COMPENSE', { timeout: 30_000 });
  await page.screenshot({ path: `${DOSSIER}/10-saga-compensation.png`, fullPage: true });
});

test('accès refusé', async ({ page }) => {
  await seConnecter(page, 'ahmed');
  await page.goto('/admin/workflows');
  await expect(page.getByTestId('acces-refuse')).toBeVisible();
  await page.screenshot({ path: `${DOSSIER}/12-acces-refuse.png` });
});
