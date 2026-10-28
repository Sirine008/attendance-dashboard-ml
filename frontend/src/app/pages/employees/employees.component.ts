import { AfterViewInit, Component, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginator, MatPaginatorIntl, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { catchError, debounceTime, filter, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { FiltersService } from '../../core/filters.service';
import { hours, num, pct } from '../../core/format';
import { EmployeeRow } from '../../core/models';
import { RISK } from '../../core/status';
import { PeriodFilterComponent } from '../../shared/period-filter.component';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';
import { SparklineComponent } from '../../shared/sparkline.component';

export function frenchPaginatorIntl(): MatPaginatorIntl {
  const p = new MatPaginatorIntl();
  p.itemsPerPageLabel = 'Lignes par page';
  p.nextPageLabel = 'Page suivante';
  p.previousPageLabel = 'Page précédente';
  p.firstPageLabel = 'Première page';
  p.lastPageLabel = 'Dernière page';
  p.getRangeLabel = (page, size, length) => {
    if (length === 0) {
      return '0 sur 0';
    }
    const start = page * size;
    return `${start + 1} – ${Math.min(start + size, length)} sur ${length}`;
  };
  return p;
}

/** Colonnes exportées en CSV (séparateur « ; », compatible Excel français). */
export function toCsv(rows: EmployeeRow[]): string {
  const header = ['Code', 'Collaborateur', 'Équipe', 'Synthétique', 'Taux de présence (%)', 'Absences ponctuelles', 'Absences longues',
    'Retards', 'Retard moyen (min)', 'Heures moyennes', 'Pointages incomplets', 'Risque J+1', 'Risque semaine'];
  const esc = (v: unknown) => {
    const s = v === null || v === undefined ? '' : String(v);
    return /[";\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = rows.map((r) => [
    r.code, r.displayName, r.teamName ?? '', r.synthetic ? 'oui' : 'non',
    r.kpis.presenceRate === null ? '' : (r.kpis.presenceRate * 100).toFixed(1).replace('.', ','),
    r.kpis.absencesPonctuelles, r.kpis.absencesLongues, r.kpis.lateDays,
    r.kpis.avgLateMinutes === null ? '' : String(r.kpis.avgLateMinutes).replace('.', ','),
    r.kpis.avgWorkedHours === null ? '' : String(r.kpis.avgWorkedHours).replace('.', ','),
    r.kpis.incompleteDays,
    r.riskD1?.riskLevel ? RISK[r.riskD1.riskLevel].label : r.riskD1 ? 'Historique insuffisant' : '',
    r.riskW1?.riskLevel ? RISK[r.riskW1.riskLevel].label : r.riskW1 ? 'Historique insuffisant' : '',
  ].map(esc).join(';'));
  return '﻿' + [header.join(';'), ...lines].join('\r\n');
}

@Component({
  selector: 'app-employees',
  imports: [PeriodFilterComponent, MatTableModule, MatSortModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatIconModule,
    MatButtonModule, MatTooltipModule, FormsModule, SparklineComponent, RiskBadgeComponent],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './employees.component.html',
  styles: `
    .toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
    .search { width: 280px; }
    @media (max-width: 600px) { .search { width: 100%; } }
    .name { display: flex; flex-direction: column; gap: 2px; min-width: 150px; }
    .name strong { font-weight: 600; }
    .code { color: var(--text-faint); font-size: .78rem; }
    td.num, th.num { text-align: right; }
    .mat-column-spark { width: 100px; }
    table { min-width: 980px; }
    .nowrap { white-space: nowrap; }
    .loading-row { padding: 12px 16px; display: flex; flex-direction: column; gap: 10px; }
    .loading-row .skeleton { height: 28px; }
  `,
})
export class EmployeesComponent implements AfterViewInit {
  protected readonly f = inject(FiltersService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly columns = ['name', 'presence', 'absP', 'absL', 'late', 'avgLate', 'hours', 'incomplete', 'spark', 'riskD1', 'riskW1'];
  readonly data = new MatTableDataSource<EmployeeRow>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly search = signal('');
  readonly count = signal(0);
  protected readonly fmt = { pct, num, hours };

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor() {
    this.data.filterPredicate = (r, q) => `${r.displayName} ${r.code} ${r.teamName ?? ''}`.toLowerCase().includes(q);
    this.data.sortingDataAccessor = (r, col) => {
      const k = r.kpis;
      switch (col) {
        case 'name': return r.displayName.toLowerCase();
        case 'team': return r.teamName ?? '';
        case 'presence': return k.presenceRate ?? -1;
        case 'absP': return k.absencesPonctuelles;
        case 'absL': return k.absencesLongues;
        case 'late': return k.lateDays;
        case 'avgLate': return k.avgLateMinutes ?? -1;
        case 'hours': return k.avgWorkedHours ?? -1;
        case 'incomplete': return k.incompleteDays;
        case 'riskD1': return r.riskD1?.riskLevel ? RISK[r.riskD1.riskLevel].order : -1;
        case 'riskW1': return r.riskW1?.riskLevel ? RISK[r.riskW1.riskLevel].order : -1;
        default: return 0;
      }
    };
    effect(() => {
      this.data.filter = this.search().trim().toLowerCase();
      this.count.set(this.data.filteredData.length);
    });
    toObservable(this.f.scope)
      .pipe(
        filter(() => this.f.meta() !== null),
        debounceTime(150),
        tap(() => this.loading.set(true)),
        switchMap((s) => this.api.employees(s).pipe(catchError(() => {
          this.error.set(true);
          return of([] as EmployeeRow[]);
        }))),
        takeUntilDestroyed(),
      )
      .subscribe((rows) => {
        this.data.data = rows;
        this.count.set(this.data.filteredData.length);
        this.loading.set(false);
      });
  }

  ngAfterViewInit(): void {
    this.data.sort = this.sort;
    this.data.paginator = this.paginator;
  }

  open(r: EmployeeRow): void {
    void this.router.navigate(['/employees', r.id]);
  }

  exportCsv(): void {
    const rows = this.data.sortData(this.data.filteredData.slice(), this.sort);
    const blob = new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `collaborateurs_${this.f.period().from}_${this.f.period().to}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  readonly hasData = computed(() => !this.loading() && this.count() > 0);
}
