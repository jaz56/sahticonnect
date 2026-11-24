import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Patient, PatientMiseAJour, PortailService } from '../../../api/portail';
import { SessionService } from '../../../core/session.service';
import { NotificationService } from '../../../core/notification.service';
import { erreursParChamp, lireErreur } from '../../../core/erreurs';

/** Champs du formulaire ↔ chemins des erreurs de validation renvoyées par le portail (details[].champ). */
const CHAMPS: Record<string, string> = {
  telephone: 'telephone',
  email: 'email',
  adresse: 'adresse',
  'contactUrgence.nom': 'urgenceNom',
  'contactUrgence.lien': 'urgenceLien',
  'contactUrgence.telephone': 'urgenceTelephone',
};

@Component({
  selector: 'app-profil',
  imports: [
    ReactiveFormsModule, DatePipe, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './profil.html',
})
export default class Profil {
  private readonly portail = inject(PortailService);
  private readonly notifications = inject(NotificationService);
  protected readonly session = inject(SessionService);

  protected readonly patient = signal<Patient | null>(null);
  protected readonly chargement = signal(false);
  protected readonly enregistrement = signal(false);

  protected readonly formulaire = inject(FormBuilder).nonNullable.group({
    telephone: ['', [Validators.pattern(/^\+?[0-9 ]{8,20}$/)]],
    email: ['', [Validators.email]],
    adresse: [''],
    urgenceNom: [''],
    urgenceLien: [''],
    urgenceTelephone: ['', [Validators.pattern(/^\+?[0-9 ]{8,20}$/)]],
  });

  constructor() {
    effect(() => this.charger(this.session.patientId()));
  }

  private charger(id: string): void {
    this.chargement.set(true);
    this.portail.consulterPatient({ id }).subscribe({
      next: (p) => {
        this.patient.set(p);
        this.formulaire.reset({
          telephone: p.telephone ?? '',
          email: p.email ?? '',
          adresse: p.adresse ?? '',
          urgenceNom: p.contactUrgence?.nom ?? '',
          urgenceLien: p.contactUrgence?.lien ?? '',
          urgenceTelephone: p.contactUrgence?.telephone ?? '',
        });
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
  }

  protected enregistrer(): void {
    const v = this.formulaire.getRawValue();
    const miseAJour: PatientMiseAJour = {
      telephone: v.telephone || undefined,
      email: v.email || undefined,
      adresse: v.adresse || undefined,
      contactUrgence: v.urgenceNom && v.urgenceTelephone
        ? { nom: v.urgenceNom, lien: v.urgenceLien || undefined, telephone: v.urgenceTelephone }
        : undefined,
    };
    this.enregistrement.set(true);
    this.portail.modifierPatient({ id: this.session.patientId(), patientMiseAJour: miseAJour }).subscribe({
      next: (p) => {
        this.patient.set(p);
        this.formulaire.markAsPristine();
        this.enregistrement.set(false);
        this.notifications.succes('Coordonnées enregistrées');
      },
      error: (e: HttpErrorResponse) => {
        this.enregistrement.set(false);
        this.afficherSurLesChamps(e);
      },
    });
  }

  /** 400 : une erreur par champ (details) ; 409 EMAIL_DEJA_UTILISE : sur le champ email. */
  private afficherSurLesChamps(reponse: HttpErrorResponse): void {
    const erreur = lireErreur(reponse);
    const parChamp = erreursParChamp(erreur);
    if (erreur.code === 'EMAIL_DEJA_UTILISE') {
      parChamp['email'] = erreur.message;
    }
    for (const [champ, message] of Object.entries(parChamp)) {
      const controle = this.formulaire.get(CHAMPS[champ] ?? champ);
      controle?.setErrors({ serveur: message });
      controle?.markAsTouched();
    }
  }

  protected erreurServeur(nom: string): string | null {
    return this.formulaire.get(nom)?.getError('serveur') ?? null;
  }
}
