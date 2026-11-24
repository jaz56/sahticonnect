import { Component, computed, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Creneau, Medecin, PortailService, RendezVous, StatutRendezVous } from '../../../api/portail';
import { SessionService } from '../../../core/session.service';
import { NotificationService } from '../../../core/notification.service';
import { dateIso } from '../../../core/dates';
import { Pastille } from '../../../shared/pastille';

/** Mes RDV : liste, déplacement (créneaux libres du même médecin), annulation. */
@Component({
  selector: 'app-mes-rdv',
  imports: [
    DatePipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatChipsModule, MatProgressBarModule, Pastille,
  ],
  templateUrl: './mes-rdv.html',
  styles: `.deplacement { margin-top: 16px; } .actions { white-space: nowrap; }`,
})
export default class MesRdv {
  private readonly portail = inject(PortailService);
  private readonly notifications = inject(NotificationService);
  protected readonly session = inject(SessionService);

  protected readonly colonnes = ['debut', 'medecin', 'type', 'statut', 'motif', 'actions'];
  protected readonly rendezVous = signal<RendezVous[]>([]);
  protected readonly chargement = signal(false);
  private readonly medecins = toSignal(this.portail.listerMedecins({}), { initialValue: [] as Medecin[] });
  protected readonly nomsMedecins = computed(
    () => new Map(this.medecins().map((m) => [m.id, `Dr ${m.prenom} ${m.nom}`])),
  );

  /** RDV en cours de déplacement, et créneaux libres proposés. */
  protected readonly aDeplacer = signal<RendezVous | null>(null);
  protected readonly dateDeplacement = signal<Date | null>(null);
  protected readonly creneaux = signal<Creneau[] | null>(null);
  protected readonly aujourdhui = new Date();

  constructor() {
    effect(() => this.charger(this.session.patientId()));
  }

  protected charger(patientId = this.session.patientId()): void {
    this.chargement.set(true);
    this.portail.listerRendezVous({ patientId }).subscribe({
      next: (liste) => {
        this.rendezVous.set([...liste].sort((a, b) => b.debut.localeCompare(a.debut)));
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
  }

  protected modifiable(rdv: RendezVous): boolean {
    return rdv.statut === StatutRendezVous.CONFIRME && new Date(rdv.debut) > new Date();
  }

  protected commencerDeplacement(rdv: RendezVous): void {
    this.aDeplacer.set(rdv);
    this.dateDeplacement.set(null);
    this.creneaux.set(null);
  }

  protected chercherCreneaux(date: Date | null): void {
    const rdv = this.aDeplacer();
    this.dateDeplacement.set(date);
    if (!rdv || !date) {
      return;
    }
    // même médecin, même type de consultation
    this.portail.rechercherCreneaux({ medecinId: rdv.medecinId, date: dateIso(date), type: rdv.type, disponibles: true })
      .subscribe((c) => this.creneaux.set(c));
  }

  protected deplacer(creneau: Creneau): void {
    const rdv = this.aDeplacer();
    if (!rdv) {
      return;
    }
    this.portail.modifierRendezVous({ id: rdv.id, rendezVousMiseAJour: { creneauId: creneau.id } }).subscribe(() => {
      this.notifications.succes(`RDV ${rdv.id} déplacé`);
      this.aDeplacer.set(null);
      this.charger();
    });
  }

  protected annuler(rdv: RendezVous): void {
    if (!confirm(`Annuler le RDV ${rdv.id} ?`)) {
      return;
    }
    this.portail.annulerRendezVous({ id: rdv.id }).subscribe(() => {
      this.notifications.succes(`RDV ${rdv.id} annulé`);
      this.charger();
    });
  }
}
