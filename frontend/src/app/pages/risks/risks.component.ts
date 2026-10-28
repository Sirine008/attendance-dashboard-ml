import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, filter, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { FiltersService } from '../../core/filters.service';
import { approxPct, frDate, longDate, num } from '../../core/format';
import { Horizon, RiskCard } from '../../core/models';
import { RISK } from '../../core/status';
import { PeriodFilterComponent } from '../../shared/period-filter.component';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';

@Component({
  selector: 'app-risks',
  imports: [MatButtonToggleModule, MatButtonModule, MatIconModule, RouterLink, PeriodFilterComponent, RiskBadgeComponent],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Risques & prédictions</h1>
          <p>Aide à l'anticipation pour organiser le travail (remplacements, réunions, continuité de service) — jamais une évaluation.</p>
        </div>
      </header>

      <div class="notice">
        <mat-icon aria-hidden="true">info</mat-icon>
        <span>Chaque carte présente une <strong>estimation statistique, non une certitude</strong>, et les facteurs <strong>associés</strong>
          à cette estimation (pas des causes). Les collaborateurs sont listés par ordre alphabétique.</span>
      </div>

      <div class="filters">
        <mat-button-toggle-group [value]="horizon()" (change)="horizon.set($event.value)" aria-label="Horizon" hideSingleSelectionIndicator>
          <mat-button-toggle value="D1">Prochain jour ouvré</mat-button-toggle>
          <mat-button-toggle value="W1">Semaine à venir</mat-button-toggle>
        </mat-button-toggle-group>
        <mat-button-toggle-group [value]="level()" (change)="level.set($event.value)" aria-label="Niveau minimal" hideSingleSelectionIndicator>
          <mat-button-toggle value="ELEVE">Élevé</mat-button-toggle>
          <mat-button-toggle value="MODERE">Modéré et élevé</mat-button-toggle>
          <mat-button-toggle value="ALL">Tous</mat-button-toggle>
        </mat-button-toggle-group>
        <app-period-filter [showPeriod]="false" />
      </div>

      @if (target(); as t) {
        <p class="muted target">Estimations pour <strong>{{ t }}</strong>, calculées avec les données au {{ refDate() }}.</p>
      }

      @if (loading()) {
        <div class="cards">@for (i of [1, 2, 3]; track i) { <div class="card skeleton" style="height: 220px"></div> }</div>
      } @else if (error()) {
        <div class="notice danger" role="alert"><mat-icon aria-hidden="true">cloud_off</mat-icon>Estimations indisponibles pour le moment.</div>
      } @else if (!cards().length) {
        <div class="card empty-state">
          <mat-icon aria-hidden="true">verified</mat-icon>
          <span>Aucun collaborateur n'a une estimation de ce niveau pour cette échéance.</span>
          @if (level() !== 'ALL') { <button mat-stroked-button (click)="level.set('ALL')">Voir toutes les estimations</button> }
        </div>
      } @else {
        <p class="muted small" aria-live="polite">{{ cards().length }} collaborateur(s)</p>
        <div class="cards">
          @for (c of cards(); track c.employeeId) {
            @let p = c.prediction;
            <article class="card risk" [style.--band]="p.riskLevel ? RISK[p.riskLevel].color : 'var(--border)'"
                     [style.--band-bg]="p.riskLevel ? RISK[p.riskLevel].bg : 'var(--surface-2)'">
              <header>
                <a [routerLink]="['/employees', c.employeeId]" class="name">{{ c.displayName }}</a>
                @if (c.synthetic) { <span class="synthetic-badge">Synthétique</span> }
                <span class="faint small team">{{ c.teamName }}</span>
              </header>
              @if (p.status === 'INSUFFICIENT_HISTORY') {
                <app-risk-badge status="INSUFFICIENT_HISTORY" />
              } @else {
                <div class="band">
                  <app-risk-badge [level]="p.riskLevel" />
                  <span class="prob tabular">{{ approx(p.probability) }}</span>
                  @if (p.expectedDays !== null) {
                    <span class="small muted">≈ {{ n(p.expectedDays, 1) }} j d'absence attendus</span>
                  }
                </div>
                <p class="ft small muted">Facteurs associés à l'estimation</p>
                <ul class="factors">
                  @for (fa of p.factors; track fa.feature) {
                    <li><mat-icon aria-hidden="true" [class.up]="fa.direction === 'UP'">{{ fa.direction === 'UP' ? 'arrow_upward' : 'arrow_downward' }}</mat-icon>
                      <span>{{ fa.label }} <span class="faint">({{ fa.direction === 'UP' ? '↑ risque' : '↓ risque' }})</span></span></li>
                  }
                </ul>
              }
              <p class="disclaimer small"><mat-icon aria-hidden="true">info</mat-icon>Estimation statistique, non une certitude.</p>
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
    .target { margin: 0; }
    .cards { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); }
    .risk { display: flex; flex-direction: column; gap: 8px; border-top: 4px solid var(--band); }
    .risk header { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 8px; }
    .name { font-weight: 650; font-size: 1.02rem; color: var(--text); text-decoration: none; }
    .name:hover { text-decoration: underline; }
    .team { width: 100%; }
    .band { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 8px 10px; border-radius: var(--radius-sm); background: var(--band-bg); }
    .prob { font-size: 1.35rem; font-weight: 700; }
    .ft { margin: 4px 0 0; font-weight: 600; }
    .factors { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; font-size: .88rem; }
    .factors li { display: flex; gap: 6px; align-items: flex-start; }
    .factors mat-icon { font-size: 17px; width: 17px; height: 17px; flex: none; color: var(--success); }
    .factors mat-icon.up { color: var(--danger); }
    .disclaimer { display: flex; align-items: center; gap: 6px; margin: auto 0 0; padding-top: 6px; color: var(--text-muted); border-top: 1px dashed var(--border); }
    .disclaimer mat-icon { font-size: 15px; width: 15px; height: 15px; }
    @media (max-width: 600px) { mat-button-toggle-group { width: 100%; } mat-button-toggle { flex: 1; } }
  `,
})
export class RisksComponent {
  protected readonly f = inject(FiltersService);
  private readonly api = inject(ApiService);
  protected readonly RISK = RISK;
  readonly horizon = signal<Horizon>('D1');
  readonly level = signal<'ELEVE' | 'MODERE' | 'ALL'>('ELEVE');
  readonly cards = signal<RiskCard[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  constructor() {
    combineLatest([toObservable(this.horizon), toObservable(this.level), toObservable(this.f.teamId), toObservable(this.f.synthetic), toObservable(this.f.meta)])
      .pipe(
        filter(([, , , , meta]) => meta !== null),
        debounceTime(100),
        tap(() => {
          this.loading.set(true);
          this.error.set(false);
        }),
        switchMap(([h, l, team, syn]) => this.api.risks(h, team, l === 'ALL' ? null : l, syn).pipe(catchError(() => {
          this.error.set(true);
          return of([] as RiskCard[]);
        }))),
        takeUntilDestroyed(),
      )
      .subscribe((c) => {
        this.cards.set(c);
        this.loading.set(false);
      });
  }

  readonly target = computed(() => {
    const p = this.cards()[0]?.prediction;
    if (!p) {
      return null;
    }
    return p.horizon === 'D1' ? longDate(p.targetDate) : `la semaine du ${frDate(p.targetDate)} au ${frDate(p.targetEnd)}`;
  });
  readonly refDate = computed(() => frDate(this.cards()[0]?.prediction.referenceDate));

  approx(v: number | null): string {
    return approxPct(v);
  }

  n(v: number | null, d = 0): string {
    return num(v, d);
  }
}
