import { EventSourceMessage, FetchEventSourceInit } from '@microsoft/fetch-event-source';
import { fluxSse } from './flux-workflows.service';

/** Fausse connexion fetch-event-source : le test déclenche l'ouverture, les messages, les erreurs et la fin. */
interface FausseConnexion {
  url: string;
  init: FetchEventSourceInit;
  ouvrir(statut?: number): Promise<void>;
  emettre(event: string, data: string): void;
  echouer(): void;
  terminer(): void;
}

describe('fluxSse (fetch-event-source → Observable)', () => {
  let connexions: FausseConnexion[];
  let numero = 0;

  /** Reproduit fetchEventSource : une erreur relancée par onerror rejette la promesse ; abort la résout. */
  const ouvreur = (url: string, init: FetchEventSourceInit) =>
    new Promise<void>((resolve, reject) => {
      const signalerErreur = (erreur: unknown) => {
        try {
          init.onerror!(erreur);
        } catch (relance) {
          reject(relance);
        }
      };
      connexions.push({
        url,
        init,
        ouvrir: async (statut = 200) => {
          try {
            await init.onopen!(new Response(null, { status: statut, headers: { 'Content-Type': 'text/event-stream' } }));
          } catch (e) {
            signalerErreur(e);
          }
        },
        emettre: (event, data) => init.onmessage!({ id: '', event, data } as EventSourceMessage),
        echouer: () => signalerErreur(new Error('réseau')),
        terminer: () => resolve(),
      });
      init.signal?.addEventListener('abort', () => resolve());
    });
  const jeton = async () => `jeton-${++numero}`;
  const options = { evenement: 'workflow', delaiInitial: 1000, delaiMax: 4000 };
  /** Laisse passer les promesses en attente (jeton, rejet de la connexion). */
  const attendre = () => vi.advanceTimersByTimeAsync(0);

  beforeEach(() => {
    connexions = [];
    numero = 0;
    vi.useFakeTimers();
  });

  afterEach(() => vi.useRealTimers());

  it("envoie le jeton dans l'en-tête Authorization, jamais dans l'URL", async () => {
    fluxSse('http://gw/flux', ouvreur, jeton, options).subscribe();
    await attendre();

    expect(connexions[0].url).toBe('http://gw/flux');
    expect(connexions[0].init.headers).toEqual({ Accept: 'text/event-stream', Authorization: 'Bearer jeton-1' });
  });

  it('émet chaque événement nommé, désérialisé ; ignore les autres et les données illisibles', async () => {
    const recus: unknown[] = [];
    const etats: boolean[] = [];
    fluxSse<{ id: string }>('http://gw/flux', ouvreur, jeton, options, (c) => etats.push(c)).subscribe((w) => recus.push(w));
    await attendre();
    const connexion = connexions[0];

    await connexion.ouvrir();
    connexion.emettre('workflow', '{"id":"WF-2026-000001","etat":"FACTUREE"}');
    connexion.emettre('autre', '{"id":"X"}');
    connexion.emettre('workflow', 'pas du json');

    expect(recus).toEqual([{ id: 'WF-2026-000001', etat: 'FACTUREE' }]);
    expect(etats).toEqual([true]);
  });

  it('réponse refusée (503, 401) ou réseau : reconnexion avec délai croissant plafonné et jeton frais', async () => {
    const etats: boolean[] = [];
    fluxSse('http://gw/flux', ouvreur, jeton, options, (c) => etats.push(c)).subscribe();
    await attendre();

    await connexions[0].ouvrir(503);
    await attendre();
    await vi.advanceTimersByTimeAsync(999);
    expect(connexions).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(connexions).toHaveLength(2); // après 1 s
    expect(connexions[1].init.headers).toMatchObject({ Authorization: 'Bearer jeton-2' });

    await connexions[1].ouvrir(401);
    await vi.advanceTimersByTimeAsync(2000);
    expect(connexions).toHaveLength(3); // après 2 s

    connexions[2].echouer();
    await vi.advanceTimersByTimeAsync(4000);
    connexions[3].echouer();
    await vi.advanceTimersByTimeAsync(3999);
    expect(connexions).toHaveLength(4); // plafond 4 s : pas encore
    await vi.advanceTimersByTimeAsync(1);
    expect(connexions).toHaveLength(5);
    expect(etats.every((e) => e === false)).toBe(true);
  });

  it('une connexion réussie remet le délai au minimum ; fin du flux côté serveur : reconnexion', async () => {
    fluxSse('http://gw/flux', ouvreur, jeton, options).subscribe();
    await attendre();
    connexions[0].echouer();
    await vi.advanceTimersByTimeAsync(1000);
    connexions[1].echouer();
    await vi.advanceTimersByTimeAsync(2000);
    await connexions[2].ouvrir();
    connexions[2].terminer();

    await vi.advanceTimersByTimeAsync(1000);
    expect(connexions).toHaveLength(4);
  });

  it('désabonnement : requête annulée, plus de reconnexion', async () => {
    const abonnement = fluxSse('http://gw/flux', ouvreur, jeton, options).subscribe();
    await attendre();
    const signal = connexions[0].init.signal!;
    abonnement.unsubscribe();

    await vi.advanceTimersByTimeAsync(60000);
    expect(signal.aborted).toBe(true);
    expect(connexions).toHaveLength(1);
  });
});
