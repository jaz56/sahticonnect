import { Component, HostListener, computed, effect, inject, signal } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { map } from 'rxjs';
import { PATIENTS, SessionService } from './core/session.service';
import { sectionsPour } from './core/navigation';
import { Authentification } from './core/auth/authentification.service';

const LIBELLES_ROLES: Record<string, string> = { ADMIN: 'Administrateur', MEDECIN: 'Médecin', PATIENT: 'Patient' };

/**
 * Structure de l'application : barre latérale fixe (sections selon les rôles du jeton, élément actif marqué d'un filet
 * d'accent), barre du haut fine (patient sélectionné, utilisateur, déconnexion), contenu qui défile avec la page.
 * Écran étroit : la barre latérale devient un tiroir (bouton Menu, voile, Échap).
 */
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatIconModule, MatButtonModule, MatSelectModule, MatFormFieldModule, MatTooltipModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(SessionService);
  protected readonly auth = inject(Authentification);

  protected readonly patients = PATIENTS;
  /** Le menu découle des rôles du jeton (plus de sélecteur de rôle). */
  protected readonly sections = computed(() => sectionsPour(this.auth.roles()));
  protected readonly nomPatient = computed(() => PATIENTS.find((p) => p.id === this.session.patientId())?.nom ?? '');
  /** Rôle principal, en clair (le plus large : administrateur, puis médecin, puis patient). */
  protected readonly libelleRole = computed(() => {
    const roles = this.auth.roles();
    const principal = (['ADMIN', 'MEDECIN', 'PATIENT'] as const).find((r) => roles.includes(r));
    return principal ? LIBELLES_ROLES[principal] : '';
  });
  /** Écran étroit (téléphone, tablette en portrait) : menu en tiroir. */
  protected readonly etroit = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );
  protected readonly menuOuvert = signal(false);

  constructor() {
    // repassage en écran large : le tiroir n'a plus de sens
    effect(() => {
      if (!this.etroit()) {
        this.menuOuvert.set(false);
      }
    });
  }

  protected fermerMenu(): void {
    this.menuOuvert.set(false);
  }

  @HostListener('document:keydown.escape')
  protected echap(): void {
    if (this.menuOuvert()) {
      this.fermerMenu();
    }
  }
}
