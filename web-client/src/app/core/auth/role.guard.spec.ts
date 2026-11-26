import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Observable, firstValueFrom, of } from 'rxjs';
import { roleGuard } from './role.guard';
import { Authentification, RoleSahti, utilisateurDuJeton } from './authentification.service';

describe('roleGuard', () => {
  let connexions: number;

  /** Configure un faux OidcSecurityService : null = non connecté, sinon payload du jeton d'accès. */
  function configurer(payload: Record<string, unknown> | null): void {
    connexions = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: OidcSecurityService,
          useValue: {
            isAuthenticated$: of({ isAuthenticated: payload !== null, allConfigsAuthenticated: [] }),
            getPayloadFromAccessToken: () => of(payload),
          },
        },
        { provide: Authentification, useValue: { connecter: () => connexions++ } },
      ],
    });
  }

  const jeton = (roles: RoleSahti[], patientId?: string) => ({
    preferred_username: 'demo', realm_access: { roles: [...roles, 'offline_access'] }, patient_id: patientId,
  });

  function executer(...roles: RoleSahti[]): Promise<boolean | UrlTree> {
    const resultat = TestBed.runInInjectionContext(() =>
      roleGuard(...roles)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    return firstValueFrom(resultat);
  }

  it('non connecté : redirection vers Keycloak, navigation annulée', async () => {
    configurer(null);
    expect(await executer('PATIENT')).toBe(false);
    expect(connexions).toBe(1);
  });

  it('rôle présent : accès accordé', async () => {
    configurer(jeton(['MEDECIN']));
    expect(await executer('MEDECIN', 'ADMIN')).toBe(true);
    expect(connexions).toBe(0);
  });

  it('rôle absent (patient sur un écran médecin) : écran « Accès refusé »', async () => {
    configurer(jeton(['PATIENT'], 'PAT-0001'));
    const resultat = await executer('MEDECIN', 'ADMIN');
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/acces-refuse');
  });

  it('médecin sur un écran admin : refusé ; admin : accordé', async () => {
    configurer(jeton(['MEDECIN']));
    expect(await executer('ADMIN')).toBeInstanceOf(UrlTree);
    TestBed.resetTestingModule();
    configurer(jeton(['ADMIN']));
    expect(await executer('ADMIN')).toBe(true);
  });
});

describe('utilisateurDuJeton', () => {
  it('lit les rôles du realm (filtrés), le patient_id et le nom complet', () => {
    expect(
      utilisateurDuJeton({
        preferred_username: 'ahmed', given_name: 'Ahmed', family_name: 'Ben Salah', patient_id: 'PAT-0001',
        realm_access: { roles: ['PATIENT', 'offline_access', 'default-roles-sahticonnect'] },
      }),
    ).toEqual({ login: 'ahmed', nom: 'Ahmed Ben Salah', roles: ['PATIENT'], patientId: 'PAT-0001' });
  });

  it('sans nom ni rôle : login comme nom, aucun rôle ; payload absent : null', () => {
    expect(utilisateurDuJeton({ preferred_username: 'x' })).toEqual({ login: 'x', nom: 'x', roles: [], patientId: null });
    expect(utilisateurDuJeton(null)).toBeNull();
  });
});
