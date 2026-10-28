import { Component, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AuthService } from '../core/auth.service';
import { FiltersService, PeriodMode } from '../core/filters.service';

/** Sélecteur de période (Jour / Mois / Année / plage) + filtre équipe + données synthétiques. */
@Component({
  selector: 'app-period-filter',
  imports: [FormsModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatIconModule],
  template: `
    <div class="filters" role="group" aria-label="Filtres">
      @if (showPeriod()) {
        <mat-button-toggle-group [value]="f.mode()" (change)="f.mode.set($event.value)" aria-label="Type de période" hideSingleSelectionIndicator>
          <mat-button-toggle value="day">Jour</mat-button-toggle>
          <mat-button-toggle value="month">Mois</mat-button-toggle>
          <mat-button-toggle value="year">Année</mat-button-toggle>
          <mat-button-toggle value="range">Plage</mat-button-toggle>
        </mat-button-toggle-group>

        @switch (f.mode()) {
          @case ('day') {
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Jour</mat-label>
              <input matInput type="date" [min]="min()" [max]="max()" [ngModel]="f.anchor()" (ngModelChange)="setAnchor($event)" />
            </mat-form-field>
          }
          @case ('month') {
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Mois</mat-label>
              <input matInput type="month" [min]="min().slice(0, 7)" [max]="max().slice(0, 7)" [ngModel]="f.anchor().slice(0, 7)"
                     (ngModelChange)="setAnchor($event ? $event + '-01' : '')" />
            </mat-form-field>
          }
          @case ('year') {
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Année</mat-label>
              <mat-select [value]="f.anchor().slice(0, 4)" (selectionChange)="setAnchor($event.value + '-01-01')">
                @for (y of years(); track y) {
                  <mat-option [value]="y">{{ y }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          @case ('range') {
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Du</mat-label>
              <input matInput type="date" [min]="min()" [max]="max()" [ngModel]="f.rangeFrom()" (ngModelChange)="$event && f.rangeFrom.set($event)" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Au</mat-label>
              <input matInput type="date" [min]="f.rangeFrom()" [max]="max()" [ngModel]="f.rangeTo()" (ngModelChange)="$event && f.rangeTo.set($event)" />
            </mat-form-field>
          }
        }
      }

      @if (!showTeam()) {
      } @else if (teams().length > 1) {
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="team">
          <mat-label>Équipe</mat-label>
          <mat-select [value]="f.teamId() ?? 0" (selectionChange)="f.teamId.set($event.value || null)">
            <mat-option [value]="0">Toutes les équipes</mat-option>
            @for (t of teams(); track t.id) {
              <mat-option [value]="t.id">{{ t.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      } @else if (teams().length === 1) {
        <span class="team-label"><mat-icon aria-hidden="true">groups</mat-icon>{{ teams()[0].name }}</span>
      }

      @if (hasSyntheticTeam()) {
        <mat-slide-toggle [checked]="f.synthetic()" (change)="f.synthetic.set($event.checked)">
          Inclure les données synthétiques (démo)
        </mat-slide-toggle>
      }
    </div>
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
    mat-form-field { width: 170px; }
    mat-form-field.team { width: 240px; }
    .team-label { display: inline-flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: .9rem; }
    @media (max-width: 600px) { mat-form-field, mat-form-field.team { width: 100%; } mat-button-toggle-group { width: 100%; }
      mat-button-toggle { flex: 1; } }
  `,
})
export class PeriodFilterComponent {
  protected readonly f = inject(FiltersService);
  private readonly auth = inject(AuthService);
  readonly showPeriod = input(true);
  readonly showTeam = input(true);

  readonly min = computed(() => this.f.meta()?.dataStart ?? '2024-01-01');
  readonly max = computed(() => this.f.meta()?.dataEnd ?? '2030-12-31');
  readonly teams = computed(() => (this.f.meta()?.teams ?? []).filter((t) => !t.synthetic || this.f.synthetic()));
  readonly hasSyntheticTeam = computed(() => this.auth.isHrOrAdmin() && (this.f.meta()?.teams ?? []).some((t) => t.synthetic));
  readonly years = computed(() => {
    const a = Number(this.min().slice(0, 4)), b = Number(this.max().slice(0, 4));
    return Array.from({ length: b - a + 1 }, (_, i) => String(a + i));
  });

  setAnchor(v: string): void {
    if (v) {
      this.f.anchor.set(v);
    }
  }

  protected readonly modes: PeriodMode[] = ['day', 'month', 'year', 'range'];
}
