import { Component, inject, signal } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CnamSoapService } from '../../../core/cnam/cnam-soap.service';
import {
  ComparerGarantiesReponse,
  ConsulterHistoriqueReponse,
  FeuilleDeSoins,
  Filiere,
  LettreCle,
  ResultatSoap,
  VerifierDroitsCnamReponse,
} from '../../../core/cnam/cnam.types';
import { formaterXml, lireActes } from '../../../core/cnam/xml';
import { PATIENTS, SessionService } from '../../../core/session.service';
import { dateIso } from '../../../core/dates';
import { DtPipe } from '../../../core/dt.pipe';
import { Pastille } from '../../../shared/pastille';

type Operation = 'verifier' | 'comparer' | 'historique' | 'emettre';

/** Les 4 opérations SOAP de dossier-cnam (via la gateway) : formulaires, résultat lisible, XML échangés. */
@Component({
  selector: 'app-cnam',
  imports: [
    DatePipe, NgTemplateOutlet, FormsModule, DtPipe, MatCardModule, MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressBarModule, Pastille,
  ],
  templateUrl: './cnam.html',
  styleUrl: './cnam.scss',
})
export default class Cnam {
  private readonly cnam = inject(CnamSoapService);
  private readonly session = inject(SessionService);

  protected readonly patients = PATIENTS;
  protected readonly lettresCles: LettreCle[] = ['C', 'CS', 'V', 'K', 'B', 'Z'];
  protected readonly filieres: Filiere[] = ['PUBLIQUE', 'PRIVEE', 'REMBOURSEMENT'];
  protected readonly formaterXml = formaterXml;
  protected readonly envoi = signal<Operation | null>(null);

  // ---------- verifierDroitsCNAM ----------
  protected verifier = {
    patientId: this.session.patientId(),
    vignette: this.session.patient().vignette,
    lettreCle: 'C' as LettreCle,
    coefficient: 1,
    libelle: 'Consultation généraliste',
  };
  protected readonly resultatVerifier = signal<ResultatSoap<VerifierDroitsCnamReponse> | null>(null);

  // ---------- comparerGaranties ----------
  protected comparer = {
    patientId: this.session.patientId(),
    libelleA: 'Hôpital public', filiereA: 'PUBLIQUE' as Filiere, actesA: 'C 1 + B 20',
    libelleB: 'Clinique privée', filiereB: 'PRIVEE' as Filiere, actesB: 'C 1 + B 20',
  };
  protected readonly resultatComparer = signal<ResultatSoap<ComparerGarantiesReponse> | null>(null);

  // ---------- consulterHistorique ----------
  protected historique = { patientId: this.session.patientId(), dateDebut: '', dateFin: '' };
  protected readonly resultatHistorique = signal<ResultatSoap<ConsulterHistoriqueReponse> | null>(null);

  // ---------- emettreFeuilleDeSoins ----------
  protected emettre = {
    patientId: this.session.patientId(),
    vignette: this.session.patient().vignette,
    dateSoins: dateIso(new Date()),
    etablissement: 'Hôpital Charles Nicolle',
    medecin: 'Dr Mohamed Chaabane',
    actes: 'CS 1 + K 10',
    referenceIntervention: '',
  };
  protected readonly resultatEmettre = signal<ResultatSoap<FeuilleDeSoins> | null>(null);

  /** Changer de patient propose sa vignette (modifiable : pour montrer la Fault VIGNETTE_INVALIDE). */
  protected vignetteDe(patientId: string): string {
    return PATIENTS.find((p) => p.id === patientId)?.vignette ?? '';
  }

  protected envoyerVerifier(): void {
    const v = this.verifier;
    this.executer('verifier', this.resultatVerifier, this.cnam.verifierDroitsCNAM({
      identifiantPatient: v.patientId,
      codeVignetteCNAM: v.vignette,
      acte: { lettreCle: v.lettreCle, coefficient: v.coefficient, libelle: v.libelle || undefined },
    }));
  }

  protected envoyerComparer(): void {
    const c = this.comparer;
    this.executer('comparer', this.resultatComparer, this.cnam.comparerGaranties({
      identifiantPatient: c.patientId,
      priseEnChargeA: { libelle: c.libelleA, filiere: c.filiereA, actes: lireActes(c.actesA) },
      priseEnChargeB: { libelle: c.libelleB, filiere: c.filiereB, actes: lireActes(c.actesB) },
    }));
  }

  protected envoyerHistorique(): void {
    const h = this.historique;
    this.executer('historique', this.resultatHistorique, this.cnam.consulterHistorique({
      identifiantPatient: h.patientId,
      dateDebut: h.dateDebut || undefined,
      dateFin: h.dateFin || undefined,
    }));
  }

  protected envoyerEmettre(): void {
    const e = this.emettre;
    this.executer('emettre', this.resultatEmettre, this.cnam.emettreFeuilleDeSoins({
      identifiantPatient: e.patientId,
      codeVignetteCNAM: e.vignette,
      dateSoins: e.dateSoins,
      etablissement: e.etablissement,
      medecin: e.medecin,
      actes: lireActes(e.actes),
      referenceIntervention: e.referenceIntervention || undefined,
    }));
  }

  private executer<T>(operation: Operation, cible: { set(v: ResultatSoap<T> | null): void }, appel: Observable<ResultatSoap<T>>): void {
    this.envoi.set(operation);
    cible.set(null);
    appel.subscribe((resultat) => {
      cible.set(resultat);
      this.envoi.set(null);
    });
  }

  protected actes(actes: { lettreCle: string; coefficient: number }[]): string {
    return actes.map((a) => `${a.lettreCle} ${a.coefficient}`).join(' + ');
  }
}
