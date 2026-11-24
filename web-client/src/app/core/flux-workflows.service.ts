import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Observable, share } from 'rxjs';
import { environment } from '../../environments/environment';
import { Workflow } from '../api/orchestrateur';

/** Fabrique d'EventSource (remplaçable dans les tests). */
export const FABRIQUE_EVENT_SOURCE = new InjectionToken<(url: string) => EventSource>('FABRIQUE_EVENT_SOURCE', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

export interface OptionsFlux {
  /** Nom de l'événement SSE (champ « event: »). */
  evenement: string;
  /** Délai avant la 1re reconnexion manuelle, doublé à chaque échec, plafonné à delaiMax (ms). */
  delaiInitial: number;
  delaiMax: number;
}

/**
 * Encapsule un flux SSE (EventSource) dans un Observable.
 * <ul>
 *   <li>chaque événement nommé est désérialisé (JSON) et émis ;</li>
 *   <li>coupure réseau : l'EventSource se reconnecte seul (readyState CONNECTING) ;</li>
 *   <li>flux fermé (ex. 503 de la gateway quand l'orchestrateur est arrêté : l'EventSource abandonne) :
 *       reconnexion manuelle avec un délai croissant (1 s, 2 s, 4 s… jusqu'à 30 s) ;</li>
 *   <li>désabonnement : l'EventSource est fermé.</li>
 * </ul>
 */
export function fluxSse<T>(
  url: string,
  fabrique: (url: string) => EventSource,
  options: OptionsFlux,
  surEtat: (connecte: boolean) => void = () => {},
): Observable<T> {
  return new Observable<T>((abonne) => {
    let source: EventSource | null = null;
    let minuterie: ReturnType<typeof setTimeout> | undefined;
    let delai = options.delaiInitial;
    let termine = false;

    const ouvrir = () => {
      source = fabrique(url);
      source.onopen = () => {
        delai = options.delaiInitial;
        surEtat(true);
      };
      source.addEventListener(options.evenement, (evenement) => {
        try {
          abonne.next(JSON.parse((evenement as MessageEvent).data) as T);
        } catch {
          /* événement illisible : ignoré */
        }
      });
      source.onerror = () => {
        surEtat(false);
        if (source && source.readyState === EventSource.CLOSED && !termine) {
          source.close();
          minuterie = setTimeout(ouvrir, delai);
          delai = Math.min(delai * 2, options.delaiMax);
        }
      };
    };

    ouvrir();
    return () => {
      termine = true;
      clearTimeout(minuterie);
      source?.close();
      surEtat(false);
    };
  });
}

/** Flux des workflows de l'orchestrateur (GET /api/orchestrateur/workflows/flux via la gateway), partagé. */
@Injectable({ providedIn: 'root' })
export class FluxWorkflowsService {
  /** Vrai tant que le flux SSE est ouvert. */
  readonly connecte = signal(false);

  readonly workflows$: Observable<Workflow> = fluxSse<Workflow>(
    environment.fluxWorkflowsUrl,
    inject(FABRIQUE_EVENT_SOURCE),
    { evenement: 'workflow', delaiInitial: 1000, delaiMax: 30000 },
    (connecte) => this.connecte.set(connecte),
  ).pipe(share());
}
