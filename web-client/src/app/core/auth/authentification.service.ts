import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom, map, of, switchMap } from 'rxjs';

export type RoleSahti = 'PATIENT' | 'MEDECIN' | 'ADMIN';

export interface Utilisateur {
  login: string;
  nom: string;
  roles: RoleSahti[];
  /** Claim patient_id du jeton (utilisateurs PATIENT seulement). */
  patientId: string | null;
}

/** Lit l'identité dans le jeton d'accès : rôles du realm (realm_access.roles), patient_id, nom. */
export function utilisateurDuJeton(payload: Record<string, any> | null | undefined): Utilisateur | null {
  if (!payload) {
    return null;
  }
  const roles = ((payload['realm_access']?.roles ?? []) as string[])
    .filter((r): r is RoleSahti => r === 'PATIENT' || r === 'MEDECIN' || r === 'ADMIN');
  const nom = [payload['given_name'], payload['family_name']].filter(Boolean).join(' ') || payload['preferred_username'];
  return { login: payload['preferred_username'] ?? '', nom, roles, patientId: payload['patient_id'] ?? null };
}

/**
 * Utilisateur connecté (Keycloak), sous forme de signals pour les composants. Les écrans ne choisissent plus leur
 * rôle : le menu et les accès découlent des rôles du jeton.
 */
@Injectable({ providedIn: 'root' })
export class Authentification {
  private readonly oidc = inject(OidcSecurityService);

  /** null : contrôle en cours au démarrage ; ensuite true / false. */
  readonly connecte = signal<boolean | null>(null);
  readonly utilisateur = signal<Utilisateur | null>(null);
  readonly roles = computed(() => this.utilisateur()?.roles ?? []);
  /** Patient « pur » (sans rôle MEDECIN ni ADMIN) : limité à ses données, pas de sélecteur de patient. */
  readonly estPatient = computed(() => {
    const r = this.roles();
    return r.includes('PATIENT') && !r.includes('MEDECIN') && !r.includes('ADMIN');
  });

  constructor() {
    this.oidc.isAuthenticated$
      .pipe(
        switchMap(({ isAuthenticated }) =>
          isAuthenticated ? this.oidc.getPayloadFromAccessToken().pipe(map((p) => utilisateurDuJeton(p))) : of(null),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((utilisateur) => {
        this.utilisateur.set(utilisateur);
        this.connecte.set(utilisateur !== null);
      });
  }

  aUnRole(...roles: RoleSahti[]): boolean {
    return roles.some((r) => this.roles().includes(r));
  }

  /** Jeton d'accès courant (rafraîchi automatiquement), ou chaîne vide si l'utilisateur n'est pas connecté. */
  jeton(): Promise<string> {
    return firstValueFrom(this.oidc.getAccessToken()).catch(() => '');
  }

  /** Redirection vers la page de connexion de Keycloak (Authorization Code + PKCE). */
  connecter(): void {
    this.oidc.authorize();
  }

  /** Déconnexion : révocation du refresh token, fin de la session Keycloak, retour à l'accueil. */
  deconnecter(): void {
    this.oidc.logoffAndRevokeTokens().subscribe();
  }
}
