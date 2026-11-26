import { Browser, Page, expect } from '@playwright/test';

/** Prochain jour de consultation (pas un dimanche) dans au moins `decalage` jours, au format ISO. */
export function prochainJourOuvre(decalage: number): string {
  const d = new Date();
  d.setDate(d.getDate() + decalage);
  if (d.getDay() === 0) {
    d.setDate(d.getDate() + 1);
  }
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Choisit une option d'un mat-select repéré par son data-testid. */
export async function choisirOption(page: Page, testId: string, libelle: RegExp): Promise<void> {
  const option = page.getByRole('option', { name: libelle }).first();
  // les options peuvent arriver après l'ouverture de la liste (réponse REST en cours) : on rouvre jusqu'à les voir
  await expect(async () => {
    await page.keyboard.press('Escape');
    await page.getByTestId(testId).click();
    await expect(option).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 20_000 });
  await option.click();
}

/** Comptes de démo du realm Keycloak sahticonnect (mot de passe « demo »). */
export type CompteDemo = 'ahmed' | 'leila' | 'karim' | 'sonia' | 'hedi' | 'dr.chaabane' | 'dr.haddad' | 'dr.kallel' | 'admin';

/**
 * Connexion par le vrai parcours OIDC : bouton « Se connecter » → page de connexion Keycloak (localhost:8180) →
 * retour sur le client avec le code d'autorisation, échangé contre les jetons (PKCE).
 */
export async function seConnecter(page: Page, compte: CompteDemo): Promise<void> {
  await page.goto('/');
  await page.getByTestId('connexion').first().click();
  await page.locator('#username').fill(compte);
  await page.locator('#password').fill('demo');
  await page.locator('#kc-login').click();
  await expect(page.getByTestId('utilisateur')).toBeVisible({ timeout: 20_000 });
}

/**
 * Nouvel onglet dans un contexte de navigateur séparé (cookies et stockage propres), connecté avec ce compte :
 * deux utilisateurs différents en même temps (sinon la session SSO de Keycloak serait partagée).
 */
export async function ouvrirSession(browser: Browser, compte: CompteDemo): Promise<Page> {
  const contexte = await browser.newContext();
  const page = await contexte.newPage();
  await seConnecter(page, compte);
  return page;
}

/** Simule une alerte depuis l'accueil ; renvoie l'identifiant du workflow et de l'alerte créés. */
export async function simulerAlerte(page: Page, patient: string, niveau: 'CRITIQUE' | 'ELEVEE'): Promise<{ workflowId: string; alerteId: string }> {
  await page.goto('/');
  await choisirOption(page, 'alerte-patient', new RegExp(patient));
  await page.getByTestId(niveau === 'CRITIQUE' ? 'niveau-critique' : 'niveau-elevee').click();
  await page.getByTestId('simuler-alerte').click();
  const resultat = page.getByTestId('workflow-cree');
  await expect(resultat).toBeVisible();
  const ids = await resultat.locator('.mono').allTextContents();
  return { workflowId: ids[0].trim(), alerteId: ids[1].trim() };
}
