import { expect, test } from '@playwright/test';
import { choisirOption, ouvrirSession, prochainJourOuvre, seConnecter, simulerAlerte } from './outils';

/**
 * Parcours de démonstration, contre la stack Docker complète (client → gateway → services), avec Keycloak :
 * chaque utilisateur se connecte par le vrai parcours Authorization Code + PKCE.
 */

test('ahmed (PATIENT) se connecte, prend un rendez-vous et le retrouve dans « Mes RDV » (REST)', async ({ page }) => {
  await seConnecter(page, 'ahmed');
  // un patient est sur son propre dossier (claim patient_id) : pas de sélecteur de patient ni d'écran médecin
  await expect(page.getByTestId('mon-dossier')).toContainText('PAT-0001');
  await expect(page.getByTestId('choix-patient')).toHaveCount(0);
  await expect(page.getByRole('link', { name: /Tableau de bord/ })).toHaveCount(0);

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

test("ahmed ne peut ouvrir ni l'écran médecin ni le dossier d'un autre patient", async ({ page }) => {
  await seConnecter(page, 'ahmed');

  // côté interface : garde de route → écran « Accès refusé »
  await page.goto('/medecin/tableau-de-bord');
  await expect(page.getByTestId('acces-refuse')).toBeVisible();
  await expect(page.getByTestId('acces-refuse')).toContainText('ahmed');

  // côté serveur (la vraie sécurité) : même avec un jeton valide, la gateway et le portail refusent
  const jeton = await page.evaluate(() => {
    for (let i = 0; i < sessionStorage.length; i++) {
      const trouve = (sessionStorage.getItem(sessionStorage.key(i)!) ?? '').match(/"access_token":"([^"]+)"/);
      if (trouve) return trouve[1];
    }
    return '';
  });
  expect(jeton).not.toBe('');
  const entetes = { Authorization: `Bearer ${jeton}` };
  const gateway = 'http://localhost:8080';
  expect((await page.request.get(`${gateway}/api/portail/patients/PAT-0001`, { headers: entetes })).status()).toBe(200);
  const autre = await page.request.get(`${gateway}/api/portail/patients/PAT-0002`, { headers: entetes });
  expect(autre.status()).toBe(403);
  expect(await autre.json()).toMatchObject({ code: 'ACCES_REFUSE' });
  const graphql = await page.request.post(`${gateway}/api/graphql`, { headers: entetes, data: { query: '{ patients { id } }' } });
  expect(graphql.status()).toBe(403);
  // usurpation : un X-Patient-Id envoyé par le client est supprimé par la gateway, remplacé par le patient_id du jeton
  const usurpe = await page.request.get(`${gateway}/api/portail/patients/PAT-0002`, {
    headers: { ...entetes, 'X-Patient-Id': 'PAT-0002' },
  });
  expect(usurpe.status()).toBe(403);
});

test("dr.chaabane voit en direct l'alerte déclenchée, admin suit le workflow jusqu'à FACTUREE", async ({ browser }) => {
  // côté médecin : la subscription GraphQL nouvelleAlerte est ouverte AVANT l'alerte (WebSocket, jeton dans l'URL)
  const medecin = await ouvrirSession(browser, 'dr.chaabane');
  const websocket = medecin.waitForEvent('websocket', (ws) => ws.url().includes('/api/graphql'));
  await medecin.goto('/medecin/alertes');
  expect((await websocket).url()).toContain('access_token=');
  await medecin.waitForTimeout(1500); // connection_init / subscribe

  // côté admin (autre session) : déclenchement (POST /api/orchestrateur/workflows/declencher)
  const admin = await ouvrirSession(browser, 'admin');
  const { workflowId, alerteId } = await simulerAlerte(admin, 'PAT-0003', 'ELEVEE');
  expect(workflowId).toMatch(/^WF-\d{4}-\d{6}$/);

  // l'alerte enregistrée par l'orchestrateur sur le dashboard est poussée au médecin en direct
  const alerte = medecin.locator(`[data-alerte="${alerteId}"]`);
  await expect(alerte).toBeVisible({ timeout: 20_000 });
  await expect(alerte).toContainText('PAT-0003');

  // l'admin suit le workflow : frise mise à jour par le flux SSE (fetch-event-source, en-tête Authorization)
  await admin.goto(`/admin/workflows?id=${workflowId}`);
  await expect(admin.getByTestId('etat-flux')).toContainText('connecté', { timeout: 10_000 });
  await expect(admin.getByTestId('detail-workflow')).toHaveAttribute('data-etat', 'FACTUREE', { timeout: 30_000 });
  await expect(admin.locator('[data-etape="FACTUREE"]')).toHaveAttribute('data-statut', 'OK');

  // le médecin n'a pas accès à l'administration
  await medecin.goto('/admin/workflows');
  await expect(medecin.getByTestId('acces-refuse')).toBeVisible();
});

test("admin : saga d'escalade, ELEVEE puis CRITIQUE → le workflow ELEVEE est « Compensé »", async ({ page }) => {
  await seConnecter(page, 'admin');
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
