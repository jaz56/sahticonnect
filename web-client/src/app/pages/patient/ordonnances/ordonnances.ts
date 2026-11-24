import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { Ordonnance, PortailService } from '../../../api/portail';
import { SessionService } from '../../../core/session.service';

@Component({
  selector: 'app-ordonnances',
  imports: [DatePipe, MatCardModule, MatSlideToggleModule, MatListModule, MatIconModule],
  template: `
    <div class="page">
      <h1>Ordonnances</h1>
      <p class="sous-titre">GET /patients/{{ session.patientId() }}/ordonnances?actives={{ actives() }} · REST</p>
      <mat-slide-toggle [checked]="actives()" (change)="actives.set($event.checked)" data-testid="filtre-actives">
        Seulement les ordonnances actives
      </mat-slide-toggle>
      <div class="grille liste">
        @for (o of ordonnances(); track o.id) {
          <mat-card appearance="outlined" [attr.data-ordonnance]="o.id">
            <mat-card-header>
              <mat-icon matCardAvatar>prescriptions</mat-icon>
              <mat-card-title>{{ o.id }}</mat-card-title>
              <mat-card-subtitle>
                {{ o.medecinNom }} · {{ o.dateEmission | date: 'dd/MM/yyyy' }}
                @if (o.dateFinValidite) { → {{ o.dateFinValidite | date: 'dd/MM/yyyy' }} }
                @if (o.renouvelable) { · renouvelable }
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-list>
                @for (l of o.lignes; track l.medicament) {
                  <mat-list-item>
                    <span matListItemTitle>{{ l.medicament }}</span>
                    <span matListItemLine>{{ l.posologie }}@if (l.dureeJours) { · {{ l.dureeJours }} jours }</span>
                  </mat-list-item>
                }
              </mat-list>
              @if (o.remarques) { <p><i>{{ o.remarques }}</i></p> }
            </mat-card-content>
          </mat-card>
        } @empty {
          <p class="vide">Aucune ordonnance.</p>
        }
      </div>
    </div>
  `,
  styles: `.liste { margin-top: 16px; }`,
})
export default class Ordonnances {
  private readonly portail = inject(PortailService);
  protected readonly session = inject(SessionService);
  protected readonly actives = signal(true);
  protected readonly ordonnances = signal<Ordonnance[]>([]);

  constructor() {
    effect(() => {
      const id = this.session.patientId();
      const actives = this.actives();
      this.portail.listerOrdonnancesPatient({ id, actives }).subscribe((o) => this.ordonnances.set(o));
    });
  }
}
