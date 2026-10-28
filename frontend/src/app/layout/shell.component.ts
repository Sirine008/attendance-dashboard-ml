import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, map, of, switchMap, timer } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { FiltersService } from '../core/filters.service';
import { initials } from '../core/format';
import { PredictionStatus, Role } from '../core/models';
import { ROLE_LABEL } from '../core/status';
import { safeStorage } from '../core/storage';
import { ThemeService } from '../core/theme.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles?: Role[];
}

const NAV: NavItem[] = [
  { path: '/overview', label: "Vue d'ensemble", icon: 'dashboard' },
  { path: '/employees', label: 'Collaborateurs', icon: 'groups' },
  { path: '/risks', label: 'Risques & prédictions', icon: 'insights' },
  { path: '/model', label: 'Santé du modèle', icon: 'monitor_heart', roles: ['RH', 'ADMIN'] },
  { path: '/admin', label: 'Administration', icon: 'admin_panel_settings', roles: ['RH', 'ADMIN'] },
  { path: '/about', label: 'À propos du modèle', icon: 'help' },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule, MatTooltipModule, MatDividerModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly api = inject(ApiService);
  private readonly filters = inject(FiltersService);
  private readonly router = inject(Router);

  readonly isMobile = toSignal(inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((r) => r.matches)), { initialValue: false });
  readonly collapsed = signal(safeStorage.get('assiduite.nav.collapsed') === '1');
  readonly mobileOpen = signal(false);
  readonly nav = computed(() => NAV.filter((n) => !n.roles || this.auth.hasRole(...n.roles)));
  readonly user = this.auth.user;
  readonly userInitials = computed(() => initials(this.user()?.fullName ?? '?'));
  readonly roleLabel = computed(() => (this.user() ? ROLE_LABEL[this.user()!.role] : ''));
  readonly status = signal<PredictionStatus | null>(null);

  constructor() {
    this.filters.init();
    // État des prédictions : vérifié au chargement puis toutes les 60 s (bandeau d'indisponibilité).
    timer(0, 60_000)
      .pipe(
        switchMap(() => this.api.predictionStatus().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((s) => this.status.set(s));
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => this.mobileOpen.set(false));
  }

  toggleNav(): void {
    if (this.isMobile()) {
      this.mobileOpen.update((v) => !v);
    } else {
      this.collapsed.update((v) => !v);
      safeStorage.set('assiduite.nav.collapsed', this.collapsed() ? '1' : '0');
    }
  }

  logout(): void {
    this.filters.reset();
    this.auth.logout();
  }

}
