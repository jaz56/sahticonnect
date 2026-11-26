import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FiltrePatients, NiveauRisque, PatientsGQL, PatientsQuery } from '../../../graphql/generated';
import { Pastille } from '../../../shared/pastille';
import { SessionService } from '../../../core/session.service';

/** Tableau de bord du médecin : query GraphQL patients(filtre), badge du niveau de risque. */
@Component({
  selector: 'app-tableau-de-bord',
  imports: [
    FormsModule, MatCardModule, MatTableModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatIconModule,
    MatButtonModule, MatProgressBarModule, Pastille,
  ],
  templateUrl: './tableau-de-bord.html',
  styles: `tr.mat-mdc-row { cursor: pointer; } tr.mat-mdc-row:hover { background: var(--mat-sys-surface-container); }`,
})
export default class TableauDeBord {
  private readonly patientsGql = inject(PatientsGQL);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected readonly colonnes = ['niveauRisque', 'patient', 'age', 'service', 'pathologie', 'medecinReferent'];
  protected readonly services = ['Cardiologie', 'Pneumologie', 'Médecine interne'];
  protected readonly niveaux: NiveauRisque[] = ['CRITIQUE', 'ELEVE', 'MODERE', 'FAIBLE'];

  protected readonly service = signal<string | null>(null);
  protected readonly niveau = signal<NiveauRisque | null>(null);
  protected readonly pathologie = signal('');
  protected pathologieSaisie = '';

  protected readonly patients = signal<PatientsQuery['patients']>([]);
  protected readonly chargement = signal(false);

  constructor() {
    effect(() => {
      const filtre: FiltrePatients = {
        service: this.service() ?? undefined,
        niveauRisque: this.niveau() ?? undefined,
        pathologie: this.pathologie() || undefined,
      };
      this.chargement.set(true);
      this.patientsGql.fetch({ variables: { filtre } }).subscribe({
        next: (r) => {
          this.patients.set(r.data?.patients ?? []);
          this.chargement.set(false);
        },
        error: () => this.chargement.set(false),
      });
    });
  }

  protected ouvrir(id: string): void {
    this.session.choisirPatient(id);
    this.router.navigate(['/medecin/patients', id]);
  }
}
