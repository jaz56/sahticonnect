import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Observable, share } from 'rxjs';
import { FetchEventSourceInit, fetchEventSource } from '@microsoft/fetch-event-source';
import { environment } from '../../environments/environment';
import { Workflow } from '../api/orchestrateur';
import { Authentification } from './auth/authentification.service';

/** Ouvre une connexion SSE (fetchEventSource par défaut, remplaçable dans les tests). */
export type OuvreurSse = (url: string, init: FetchEventSourceInit) => Promise<void>;

export const OUVREUR_SSE = new InjectionToken<OuvreurSse>('OUVREUR_SSE', {
  providedIn: 'root',
  factory: () => fetchEventSource,
});

export interface OptionsFlux {
  /** Nom de l'événement SSE (champ « event: »). */
  evenement: string;
  /** Délai avant la 1re reconnexion, doublé à chaque échec, plafonné à delaiMax (ms). */
  delaiInitial: number;
  delaiMax: number;
}

/**
 * Encapsule un flux SSE dans un Observable.
 * <p>L'EventSource du navigateur ne sait pas poser d'en-tête : on utilise @microsoft/fetch-event-source (fetch +
 * parseur SSE), qui envoie « Authorization: Bearer … ». Le jeton ne passe donc jamais dans l'URL (journaux, historique).
 * <ul>
 *   <li>chaque événement nommé est désérialisé (JSON) et émis ;</li>
 *   <li>réponse non 2xx (401 jeton expiré, 503 orchestrateur arrêté…), coupure réseau ou fin du flux : reconnexion
 *       avec un délai croissant (1 s, 2 s, 4 s… jusqu'à 30 s) et un jeton frais à chaque tentative (la reconnexion
 *       interne de la bibliothèque est désactivée, elle réutiliserait l'ancien en-tête) ;</li>
 *   <li>désabonnement : la requête est annulée (AbortController).</li>
 * </ul>
 */
export function fluxSse<T>(
  url: string,
  ouvreur: OuvreurSse,
  jeton: () => Promise<string>,
  options: OptionsFlux,
  surEtat: (connecte: boolean) => void = () => {},
): Observable<T> {
  return new Observable<T>((abonne) => {
    const controleur = new AbortController();
    let minuterie: ReturnType<typeof setTimeout> | undefined;
    let delai = options.delaiInitial;
    let termine = false;

    const relancer = () => {
      if (termine) {
        return;
      }
      surEtat(false);
      minuterie = setTimeout(connecter, delai);
      delai = Math.min(delai * 2, options.delaiMax);
    };

    const connecter = async () => {
      const valeur = await jeton();
      if (termine) {
        return;
      }
      const headers: Record<string, string> = { Accept: 'text/event-stream' };
      if (valeur) {
        headers['Authorization'] = `Bearer ${valeur}`;
      }
      try {
        await ouvreur(url, {
          headers,
          signal: controleur.signal,
          openWhenHidden: true, // onglet en arrière-plan : on garde le flux (sinon la bibliothèque le coupe)
          onopen: async (reponse) => {
            if (!reponse.ok) {
              throw new Error(`Flux SSE refusé : HTTP ${reponse.status}`);
            }
            delai = options.delaiInitial;
            surEtat(true);
          },
          onmessage: (message) => {
            if (message.event !== options.evenement) {
              return;
            }
            try {
              abonne.next(JSON.parse(message.data) as T);
            } catch {
              /* événement illisible : ignoré */
            }
          },
          onerror: (erreur) => {
            throw erreur; // pas de reconnexion interne : on relance nous-mêmes, avec un jeton frais
          },
        });
      } catch {
        /* échec de connexion ou flux interrompu : relance ci-dessous */
      }
      relancer();
    };

    connecter();
    return () => {
      termine = true;
      clearTimeout(minuterie);
      controleur.abort();
      surEtat(false);
    };
  });
}

/** Flux des workflows de l'orchestrateur (GET /api/orchestrateur/workflows/flux via la gateway), partagé. */
@Injectable({ providedIn: 'root' })
export class FluxWorkflowsService {
  private readonly authentification = inject(Authentification);

  /** Vrai tant que le flux SSE est ouvert. */
  readonly connecte = signal(false);

  readonly workflows$: Observable<Workflow> = fluxSse<Workflow>(
    environment.fluxWorkflowsUrl,
    inject(OUVREUR_SSE),
    () => this.authentification.jeton(),
    { evenement: 'workflow', delaiInitial: 1000, delaiMax: 30000 },
    (connecte) => this.connecte.set(connecte),
  ).pipe(share());
}
