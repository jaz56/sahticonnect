import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { jetonInterceptor } from './jeton.interceptor';
import { Authentification } from './authentification.service';

describe('jetonInterceptor', () => {
  let http: HttpClient;
  let controleur: HttpTestingController;
  let jeton: string;
  const gw = environment.gatewayUrl;

  beforeEach(() => {
    jeton = 'eyJ.jeton.demo';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jetonInterceptor])),
        provideHttpClientTesting(),
        { provide: Authentification, useValue: { jeton: async () => jeton } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controleur = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controleur.verify());

  /** Laisse l'intercepteur obtenir le jeton (promesse) avant d'émettre la requête. */
  const attendre = () => new Promise((ok) => setTimeout(ok));

  it('ajoute Authorization: Bearer aux requêtes vers la gateway', async () => {
    const appel = firstValueFrom(http.get(`${gw}/api/portail/patients/PAT-0001`));
    await attendre();
    const requete = controleur.expectOne(`${gw}/api/portail/patients/PAT-0001`);
    expect(requete.request.headers.get('Authorization')).toBe('Bearer eyJ.jeton.demo');
    requete.flush({});
    await appel;
  });

  it("n'envoie jamais le jeton à une autre origine (Keycloak, CDN…)", async () => {
    const appel = firstValueFrom(http.get('http://localhost:8180/realms/sahticonnect/.well-known/openid-configuration'));
    await attendre();
    const requete = controleur.expectOne((r) => r.url.startsWith('http://localhost:8180'));
    expect(requete.request.headers.has('Authorization')).toBe(false);
    requete.flush({});
    await appel;
  });

  it('route publique de santé : pas de jeton', async () => {
    const appel = firstValueFrom(http.get(`${gw}/actuator/health`));
    await attendre();
    const requete = controleur.expectOne(`${gw}/actuator/health`);
    expect(requete.request.headers.has('Authorization')).toBe(false);
    requete.flush({ status: 'UP' });
    await appel;
  });

  it("utilisateur non connecté : requête envoyée sans en-tête (la gateway répondra 401)", async () => {
    jeton = '';
    const appel = firstValueFrom(http.get(`${gw}/api/graphql`));
    await attendre();
    const requete = controleur.expectOne(`${gw}/api/graphql`);
    expect(requete.request.headers.has('Authorization')).toBe(false);
    requete.flush({});
    await appel;
  });
});
