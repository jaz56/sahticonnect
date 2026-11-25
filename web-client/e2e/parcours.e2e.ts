import { expect, test } from '@playwright/test';
import { choisirOption, choisirRole, prochainJourOuvre, simulerAlerte } from './outils';

/** Parcours de démonstration, contre la stack Docker complète (client → gateway → services). */

test('un patient prend un rendez-vous (REST)', async ({ page }) => {
  await page.goto('/');
  await choisirRole(page, 'patient');
  await page.getByRole('link', { name: /Prendre RDV/ }).click();

  await choisirOption(page, 'choix-medecin', /Dr /);
  await page.getByTestId('choix-date').fill(prochainJourOuvre(3));
  await page.getByTestId('choix-date').press('Tab'); // dateChange → GET /creneaux
  const creneaux = page.getByTestId('creneaux').getByRole('option');
  await expect(creneaux.first()).toBeVisible();
  await creneaux.first().click();
  await page.getByTestId('reserver').click();

  const confirmation = page.getByTestId('rdv-confirme');
  await expect(confirmation).toBeVisible();
  const rdv = (await confirmation.locator('mat-card-title').textContent())!.match(/RDV-\d{4}-\d{6}/)![0];

  // le RDV apparaît dans « Mes RDV », puis on l'annule pour laisser le créneau libre
  await confirmation.getByRole('link', { name: 'Voir mes RDV' }).click();
  const ligne = page.locator(`tr[data-rdv="${rdv}"]`);
  await expect(ligne).toContainText('CONFIRME');
  page.once('dialog', (d) => d.accept());
  await ligne.getByTestId('annuler-rdv').click();
  await expect(ligne).toContainText('ANNULE');
});

test('« Simuler une alerte » : l\'alerte apparaît en direct côté médecin, le workflow passe à FACTUREE côté admin', async ({ browser }) => {
  // côté médecin : la subscription GraphQL nouvelleAlerte est ouverte AVANT l'alerte (WebSocket via la gateway)
  const medecin = await browser.newPage();
  const websocket = medecin.waitForEvent('websocket', (ws) => ws.url().includes('/api/graphql'));
  await medecin.goto('/medecin/alertes');
  await websocket;
  await medecin.waitForTimeout(1500); // connection_init / subscribe

  // côté admin : déclenchement (POST /api/orchestrateur/workflows/declencher)
  const admin = await browser.newPage();
  const { workflowId, alerteId } = await simulerAlerte(admin, 'PAT-0003', 'ELEVEE');
  expect(workflowId).toMatch(/^WF-\d{4}-\d{6}$/);

  // l'alerte enregistrée par l'orchestrateur sur le dashboard est poussée au médecin en direct
  const alerte = medecin.locator(`[data-alerte="${alerteId}"]`);
  await expect(alerte).toBeVisible({ timeout: 20_000 });
  await expect(alerte).toContainText('PAT-0003');

  // l'admin suit le workflow : frise mise à jour par le flux SSE jusqu'à FACTUREE
  await admin.goto(`/admin/workflows?id=${workflowId}`);
  await expect(admin.getByTestId('etat-flux')).toContainText('connecté', { timeout: 10_000 });
  await expect(admin.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'FACTUREE', { timeout: 30_000 });
  await expect(admin.locator('[data-etape="FACTUREE"]')).toHaveAttribute('data-statut', 'OK');
});

test('saga d\'escalade : ELEVEE puis CRITIQUE → le workflow ELEVEE est « Compensé », la CRITIQUE passe par ESCALADE', async ({ page }) => {
  // 1. alerte ELEVEE : RDV d'urgence, fiche, feuille CS 1 → FACTUREE
  const elevee = await simulerAlerte(page, 'PAT-0004', 'ELEVEE');
  await page.goto(`/admin/workflows?id=${elevee.workflowId}`);
  await expect(page.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'FACTUREE', { timeout: 30_000 });

  // 2. alerte CRITIQUE du même patient, quelques minutes plus tard : l'ambulance remplace le RDV
  const critique = await simulerAlerte(page, 'PAT-0004', 'CRITIQUE');
  await page.goto(`/admin/workflows?id=${critique.workflowId}`);
  await expect(page.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'FACTUREE', { timeout: 30_000 });
  await expect(page.locator('[data-etape="ESCALADE"]')).toHaveAttribute('data-statut', 'OK');
  await expect(page.locator('[data-etape="ESCALADE"]')).toContainText(`${elevee.workflowId} compensé`);

  // 3. le workflow ELEVEE est compensé : badge, et les 3 compensations dans l'ordre inverse
  await page.goto(`/admin/workflows?id=${elevee.workflowId}`);
  const detail = page.getByTestId('detail-workflow');
  await expect(detail).toHaveAttribute('data-etat', 'COMPENSE', { timeout: 15_000 });
  await expect(detail.getByTestId('badge-etat')).toHaveText('Compensé');
  await expect(detail.locator('[data-compensation]')).toHaveCount(3);
  const ordre = await detail.locator('[data-compensation]').evaluateAll((cases) => cases.map((c) => c.getAttribute('data-compensation')));
  expect(ordre).toEqual(['FACTUREE', 'FICHE_PUBLIEE', 'INTERVENTION_DECLENCHEE']);
  await expect(detail).toContainText(critique.workflowId);
});
