import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Authentification } from '../../core/auth/authentification.service';

/** Écran affiché quand le rôle de l'utilisateur ne donne pas accès à la page demandée (garde de route). */
@Component({
  selector: 'app-acces-refuse',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    <div class="page">
      <mat-card class="refus" appearance="outlined" data-testid="acces-refuse">
        <mat-card-header>
          <mat-icon matCardAvatar>block</mat-icon>
          <mat-card-title>Accès refusé</mat-card-title>
          <mat-card-subtitle>403 · ACCES_REFUSE</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p>
            Votre compte <b>{{ auth.utilisateur()?.login }}</b> ({{ auth.roles().join(', ') || 'aucun rôle' }}) ne donne pas
            accès à cette page.
          </p>
          <p class="sous-titre">
            Les droits sont vérifiés par l'API Gateway à chaque requête : même en saisissant l'adresse à la main, les données
            restent inaccessibles.
          </p>
        </mat-card-content>
        <mat-card-actions>
          <a mat-flat-button routerLink="/"><mat-icon>home</mat-icon> Retour à l'accueil</a>
          <button mat-button (click)="auth.deconnecter()"><mat-icon>logout</mat-icon> Changer de compte</button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: `.refus { max-width: 640px; margin: 48px auto; border-left: 6px solid var(--sahti-critique); }`,
})
export default class AccesRefuse {
  protected readonly auth = inject(Authentification);
}
