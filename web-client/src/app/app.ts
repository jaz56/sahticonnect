import { Component, computed, inject } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { map } from 'rxjs';
import { PATIENTS, ROLES, Role, SessionService } from './core/session.service';
import { MENUS } from './core/navigation';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule,
    MatButtonToggleModule, MatSelectModule, MatFormFieldModule, MatTooltipModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly roles = ROLES;
  protected readonly patients = PATIENTS;
  protected readonly menu = computed(() => MENUS[this.session.role()]);
  /** Écran étroit (téléphone, tablette en portrait) : menu en tiroir. */
  protected readonly etroit = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  protected changerRole(role: Role): void {
    this.session.role.set(role);
    this.router.navigateByUrl(MENUS[role][0].lien);
  }
}
