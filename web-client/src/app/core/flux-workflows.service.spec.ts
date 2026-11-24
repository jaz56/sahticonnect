import { fluxSse } from './flux-workflows.service';

/** Faux EventSource : les tests déclenchent eux-mêmes l'ouverture, les événements et les erreurs. */
class FauxEventSource {
  static instances: FauxEventSource[] = [];
  readyState = 0; // CONNECTING
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  ferme = false;
  private ecouteurs = new Map<string, ((e: MessageEvent) => void)[]>();

  constructor(readonly url: string) {
    FauxEventSource.instances.push(this);
  }

  addEventListener(nom: string, ecouteur: (e: MessageEvent) => void): void {
    this.ecouteurs.set(nom, [...(this.ecouteurs.get(nom) ?? []), ecouteur]);
  }

  close(): void {
    this.ferme = true;
    this.readyState = 2;
  }

  // ----- pilotage par le test -----
  ouvrir(): void {
    this.readyState = 1;
    this.onopen?.();
  }

  emettre(nom: string, donnees: string): void {
    (this.ecouteurs.get(nom) ?? []).forEach((e) => e(new MessageEvent(nom, { data: donnees })));
  }

  echouer(readyState: number): void {
    this.readyState = readyState;
    this.onerror?.();
  }
}

describe('fluxSse (EventSource → Observable)', () => {
  const fabrique = (url: string) => new FauxEventSource(url) as unknown as EventSource;
  const options = { evenement: 'workflow', delaiInitial: 1000, delaiMax: 4000 };

  beforeEach(() => {
    FauxEventSource.instances = [];
    vi.useFakeTimers();
    // l'EventSource du navigateur n'existe pas sous jsdom : seules ses constantes servent
    (globalThis as any).EventSource ??= { CONNECTING: 0, OPEN: 1, CLOSED: 2 };
  });

  afterEach(() => vi.useRealTimers());

  it('émet chaque événement nommé, désérialisé ; ignore les autres et les données illisibles', () => {
    const recus: unknown[] = [];
    const etats: boolean[] = [];
    fluxSse<{ id: string }>('http://gw/flux', fabrique, options, (c) => etats.push(c)).subscribe((w) => recus.push(w));
    const source = FauxEventSource.instances[0];

    source.ouvrir();
    source.emettre('workflow', '{"id":"WF-2026-000001","etat":"FACTUREE"}');
    source.emettre('autre', '{"id":"X"}');
    source.emettre('workflow', 'pas du json');

    expect(source.url).toBe('http://gw/flux');
    expect(recus).toEqual([{ id: 'WF-2026-000001', etat: 'FACTUREE' }]);
    expect(etats).toEqual([true]);
  });

  it('coupure réseau (CONNECTING) : on laisse l\'EventSource se reconnecter seul', () => {
    fluxSse('http://gw/flux', fabrique, options).subscribe();
    FauxEventSource.instances[0].echouer(0);

    vi.advanceTimersByTime(10000);
    expect(FauxEventSource.instances).toHaveLength(1);
  });

  it('flux fermé (ex. 503 de la gateway) : reconnexion manuelle avec délai croissant plafonné', () => {
    const etats: boolean[] = [];
    fluxSse('http://gw/flux', fabrique, options, (c) => etats.push(c)).subscribe();

    FauxEventSource.instances[0].echouer(2);
    expect(FauxEventSource.instances[0].ferme).toBe(true);
    vi.advanceTimersByTime(999);
    expect(FauxEventSource.instances).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(FauxEventSource.instances).toHaveLength(2); // après 1 s

    FauxEventSource.instances[1].echouer(2);
    vi.advanceTimersByTime(2000);
    expect(FauxEventSource.instances).toHaveLength(3); // après 2 s

    FauxEventSource.instances[2].echouer(2);
    vi.advanceTimersByTime(4000);
    FauxEventSource.instances[3].echouer(2);
    vi.advanceTimersByTime(3999);
    expect(FauxEventSource.instances).toHaveLength(4); // plafond 4 s : pas encore
    vi.advanceTimersByTime(1);
    expect(FauxEventSource.instances).toHaveLength(5);
    expect(etats.every((e) => e === false)).toBe(true);
  });

  it('une connexion réussie remet le délai au minimum', () => {
    fluxSse('http://gw/flux', fabrique, options).subscribe();
    FauxEventSource.instances[0].echouer(2);
    vi.advanceTimersByTime(1000);
    FauxEventSource.instances[1].echouer(2);
    vi.advanceTimersByTime(2000);
    FauxEventSource.instances[2].ouvrir();
    FauxEventSource.instances[2].echouer(2);

    vi.advanceTimersByTime(1000);
    expect(FauxEventSource.instances).toHaveLength(4);
  });

  it('désabonnement : EventSource fermé, plus de reconnexion', () => {
    const abonnement = fluxSse('http://gw/flux', fabrique, options).subscribe();
    FauxEventSource.instances[0].echouer(2);
    abonnement.unsubscribe();

    vi.advanceTimersByTime(60000);
    expect(FauxEventSource.instances).toHaveLength(1);
    expect(FauxEventSource.instances[0].ferme).toBe(true);
  });
});
