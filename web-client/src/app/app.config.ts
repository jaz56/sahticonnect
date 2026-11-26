import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import localeFrTn from '@angular/common/locales/fr-TN';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';
import { routes } from './app.routes';
import { environment } from '../environments/environment';
import { erreurInterceptor } from './core/erreur.interceptor';
import { jetonInterceptor } from './core/auth/jeton.interceptor';
import { provideAuthentification } from './core/auth/auth.config';
import { provideGraphQl } from './core/graphql/graphql.provider';
import { provideApi as provideApiPortail } from './api/portail';
import { provideApi as provideApiOrchestrateur } from './api/orchestrateur';

registerLocaleData(localeFrTn);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // OIDC Keycloak (Authorization Code + PKCE) ; le jeton est ajouté avant l'intercepteur d'erreurs
    provideAuthentification(),
    provideHttpClient(withInterceptors([jetonInterceptor, erreurInterceptor])),
    // clients REST générés depuis les contrats OpenAPI, branchés sur la gateway
    provideApiPortail(environment.portailUrl),
    provideApiOrchestrateur(environment.orchestrateurUrl),
    provideGraphQl(),
    // français, formats tunisiens : 24/11/2026 14:30, heure de Tunis (UTC+1, sans heure d'été)
    { provide: LOCALE_ID, useValue: 'fr-TN' },
    { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { dateFormat: 'dd/MM/yyyy HH:mm', timezone: '+0100' } },
    { provide: MAT_DATE_LOCALE, useValue: 'fr-TN' },
    provideNativeDateAdapter(),
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
  ],
};
