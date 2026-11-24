import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { erreurInterceptor } from './erreur.interceptor';
import { NotificationService } from './notification.service';
import { ErreurApi, SANS_NOTIFICATION, erreursParChamp, lireErreur } from './erreurs';

describe('erreurInterceptor', () => {
  let http: HttpClient;
  let controleur: HttpTestingController;
  let erreursNotifiees: ErreurApi[];

  beforeEach(() => {
    erreursNotifiees = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([erreurInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { erreur: (e: ErreurApi) => erreursNotifiees.push(e) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controleur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controleur.verify());

  it('lit le schéma Erreur (fallback 503 de la gateway) et notifie avec le X-Correlation-Id, puis propage l\'erreur', async () => {
    const appel = firstValueFrom(http.get('/api/portail/patients/PAT-0001'));
    controleur.expectOne('/api/portail/patients/PAT-0001').flush(
      { horodatage: '2026-11-24T19:54:07+01:00', statut: 503, code: 'SERVICE_INDISPONIBLE', service: 'portail-patient',
        message: 'Service portail-patient indisponible', chemin: '/api/portail/patients/PAT-0001' },
      { status: 503, statusText: 'Service Unavailable', headers: { 'X-Correlation-Id': '3f2c-demo' } },
    );

    await expect(appel).rejects.toMatchObject({ status: 503 });
    expect(erreursNotifiees).toEqual([expect.objectContaining({
      statut: 503, code: 'SERVICE_INDISPONIBLE', service: 'portail-patient', correlationId: '3f2c-demo',
      message: 'Service portail-patient indisponible',
    })]);
  });

  it('400 avec details : erreurs indexées par champ pour le formulaire', async () => {
    const appel = firstValueFrom(http.put('/api/portail/patients/PAT-0001', {}));
    controleur.expectOne('/api/portail/patients/PAT-0001').flush(
      { statut: 400, code: 'REQUETE_INVALIDE', message: 'La requête contient des champs invalides',
        details: [{ champ: 'email', message: 'doit être une adresse email valide' }] },
      { status: 400, statusText: 'Bad Request' },
    );

    const erreur: ErreurApi = await appel.then(() => { throw new Error("400 attendu"); }, (e) => lireErreur(e));
    expect(erreursParChamp(erreur)).toEqual({ email: 'doit être une adresse email valide' });
    expect(erreursNotifiees[0].code).toBe('REQUETE_INVALIDE');
  });

  it('gateway injoignable (statut 0) : erreur GATEWAY_INJOIGNABLE', async () => {
    const appel = firstValueFrom(http.get('/api/orchestrateur/workflows'));
    controleur.expectOne('/api/orchestrateur/workflows').error(new ProgressEvent('error'), { status: 0, statusText: '' });

    await expect(appel).rejects.toBeTruthy();
    expect(erreursNotifiees[0]).toEqual(expect.objectContaining({ statut: 0, code: 'GATEWAY_INJOIGNABLE' }));
  });

  it('requête marquée SANS_NOTIFICATION : pas de snackbar (le composant affiche l\'erreur lui-même)', async () => {
    const appel = firstValueFrom(http.get('/actuator/health', { context: new HttpContext().set(SANS_NOTIFICATION, true) }));
    controleur.expectOne('/actuator/health').flush({ status: 'DOWN' }, { status: 503, statusText: 'Service Unavailable' });

    await expect(appel).rejects.toBeTruthy();
    expect(erreursNotifiees).toHaveLength(0);
  });
});
