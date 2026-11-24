import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Creneau, Medecin, PortailService, RendezVous, TypeConsultation } from '../../../api/portail';
import { SessionService } from '../../../core/session.service';
import { NotificationService } from '../../../core/notification.service';
import { dateIso } from '../../../core/dates';
import { DtPipe } from '../../../core/dt.pipe';

/** Prise de RDV : médecin → date → créneaux libres → réservation. */
@Component({
  selector: 'app-prise-rdv',
  imports: [
    FormsModule, DatePipe, RouterLink, DtPipe, MatCardModule, MatFormFieldModule, MatSelectModule, MatInputModule,
    MatDatepickerModule, MatButtonModule, MatButtonToggleModule, MatChipsModule, MatIconModule, MatProgressBarModule,
  ],
  templateUrl: './prise-rdv.html',
})
export default class PriseRdv {
  private readonly portail = inject(PortailService);
  private readonly notifications = inject(NotificationService);
  protected readonly session = inject(SessionService);

  protected readonly medecins = toSignal(this.portail.listerMedecins({}), { initialValue: [] as Medecin[] });
  protected readonly medecinId = signal<string | null>(null);
  protected readonly medecin = computed(() => this.medecins().find((m) => m.id === this.medecinId()) ?? null);
  protected readonly date = signal<Date | null>(null);
  protected readonly type = signal<TypeConsultation>(TypeConsultation.PRESENTIEL);
  protected readonly creneaux = signal<Creneau[] | null>(null);
  protected readonly creneauId = signal<string | null>(null);
  protected readonly recherche = signal(false);
  protected readonly reservation = signal(false);
  protected readonly confirme = signal<RendezVous | null>(null);
  protected motif = '';

  protected readonly aujourdhui = new Date();
  protected readonly types = [TypeConsultation.PRESENTIEL, TypeConsultation.TELECONSULTATION];

  protected choisirMedecin(id: string): void {
    this.medecinId.set(id);
    if (!this.medecin()?.teleconsultation) {
      this.type.set(TypeConsultation.PRESENTIEL);
    }
    this.rechercher();
  }

  protected rechercher(): void {
    const medecinId = this.medecinId();
    const date = this.date();
    this.creneauId.set(null);
    this.confirme.set(null);
    if (!medecinId || !date) {
      this.creneaux.set(null);
      return;
    }
    this.recherche.set(true);
    this.portail.rechercherCreneaux({ medecinId, date: dateIso(date), type: this.type(), disponibles: true }).subscribe({
      next: (c) => {
        this.creneaux.set(c);
        this.recherche.set(false);
      },
      error: () => this.recherche.set(false),
    });
  }

  protected reserver(): void {
    const creneauId = this.creneauId();
    if (!creneauId) {
      return;
    }
    this.reservation.set(true);
    this.portail
      .creerRendezVous({
        rendezVousCreation: {
          identifiantPatient: this.session.patientId(),
          medecinId: this.medecinId() ?? undefined,
          creneauId,
          type: this.type(),
          motif: this.motif || undefined,
        },
      })
      .subscribe({
        next: (rdv) => {
          this.confirme.set(rdv);
          this.reservation.set(false);
          this.notifications.succes(`RDV ${rdv.id} confirmé`);
          this.rechercher();
          this.confirme.set(rdv);
        },
        // 409 CRENEAU_DEJA_RESERVE (pris entre-temps) : snackbar de l'intercepteur, puis créneaux rafraîchis
        error: () => {
          this.reservation.set(false);
          this.rechercher();
        },
      });
  }
}
