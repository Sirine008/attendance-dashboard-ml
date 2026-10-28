import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { EChartsCoreOption } from 'echarts/core';
import { catchError, debounceTime, filter, forkJoin, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { chartBase } from '../../core/chart-theme';
import { FiltersService } from '../../core/filters.service';
import { frDate, hours, num, pct } from '../../core/format';
import { ArrivalDistribution, HeatCell, KpiResponse, TrendPoint } from '../../core/models';
import { ThemeService } from '../../core/theme.service';
import { ChartCardComponent } from '../../shared/chart-card.component';
import { KpiCardComponent } from '../../shared/kpi-card.component';
import { PeriodFilterComponent } from '../../shared/period-filter.component';

const DAYS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven'];

@Component({
  selector: 'app-overview',
  imports: [PeriodFilterComponent, KpiCardComponent, ChartCardComponent, MatIconModule, MatButtonToggleModule],
  templateUrl: './overview.component.html',
  styles: `
    .charts-main { display: grid; gap: 16px; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); }
    @media (max-width: 1100px) { .charts-main { grid-template-columns: minmax(0, 1fr); } }
    .period { color: var(--text-muted); font-size: .9rem; }
  `,
})
export class OverviewComponent {
  protected readonly f = inject(FiltersService);
  private readonly api = inject(ApiService);
  private readonly theme = inject(ThemeService);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly kpis = signal<KpiResponse | null>(null);
  readonly trend = signal<TrendPoint[]>([]);
  readonly heat = signal<HeatCell[]>([]);
  readonly arrivals = signal<ArrivalDistribution | null>(null);
  readonly arrivalView = signal<'time' | 'delta'>('time');

  constructor() {
    toObservable(this.f.scope)
      .pipe(
        filter(() => this.f.meta() !== null),
        debounceTime(150),
        tap(() => {
          this.loading.set(true);
          this.error.set(false);
        }),
        switchMap((s) =>
          forkJoin({ k: this.api.kpis(s), t: this.api.trend(s), h: this.api.heatmap(s), a: this.api.arrivals(s) }).pipe(
            catchError(() => {
              this.error.set(true);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((r) => {
        if (r) {
          this.kpis.set(r.k);
          this.trend.set(r.t);
          this.heat.set(r.h);
          this.arrivals.set(r.a);
        }
        this.loading.set(false);
      });
  }

  readonly cards = computed(() => {
    const k = this.kpis();
    const c = k?.current, p = k?.previous;
    const d = (a: number | null | undefined, b: number | null | undefined) => (a === null || a === undefined || b === null || b === undefined ? null : a - b);
    return [
      { label: 'Taux de présence', icon: 'how_to_reg', value: pct(c?.presenceRate), delta: d(c?.presenceRate, p?.presenceRate), kind: 'points' as const, good: 'up' as const, accent: true,
        hint: 'Jours présents / jours ouvrés attendus (hors congés déclarés)' },
      { label: 'Absences ponctuelles', icon: 'person_off', value: num(c?.absencesPonctuelles), delta: d(c?.absencesPonctuelles, p?.absencesPonctuelles), kind: 'number' as const, good: 'down' as const, accent: true,
        hint: '1 à 2 jours ouvrés consécutifs sans pointage (jours-personnes)' },
      { label: 'Absences longues', icon: 'event_busy', value: num(c?.absencesLongues), delta: d(c?.absencesLongues, p?.absencesLongues), kind: 'number' as const, good: 'neutral' as const, accent: false,
        hint: '≥ 3 jours ouvrés consécutifs sans pointage : congé probable, affiché à part' },
      { label: 'Retards', icon: 'schedule', value: num(c?.lateDays), delta: d(c?.lateDays, p?.lateDays), kind: 'number' as const, good: 'down' as const, accent: false,
        hint: "1re entrée après l'horaire de référence personnel + tolérance" },
      { label: 'Retard moyen', icon: 'timer', value: c?.avgLateMinutes != null ? `${num(c.avgLateMinutes, 0)} min` : '—', delta: d(c?.avgLateMinutes, p?.avgLateMinutes), kind: 'minutes' as const, good: 'down' as const, accent: false,
        hint: 'Moyenne des minutes de retard, sur les seuls jours de retard' },
      { label: 'Heures moyennes / jour', icon: 'hourglass_bottom', value: hours(c?.avgWorkedHours), delta: d(c?.avgWorkedHours, p?.avgWorkedHours), kind: 'hours' as const, good: 'neutral' as const, accent: false,
        hint: 'Journées complètes uniquement (aucune heure inventée)' },
      { label: 'Pointages incomplets', icon: 'rule', value: num(c?.incompleteDays), delta: d(c?.incompleteDays, p?.incompleteDays), kind: 'number' as const, good: 'down' as const, accent: false,
        hint: 'Journées avec un nombre impair de pointages ou une séquence entrée/sortie incohérente' },
    ];
  });

  readonly comparison = computed(() => {
    const k = this.kpis();
    return k ? `Comparaison avec la période du ${frDate(k.previousFrom)} au ${frDate(k.previousTo)}` : '';
  });

  readonly trendOptions = computed<EChartsCoreOption | null>(() => {
    this.theme.mode();
    const t = this.trend();
    if (!t.length) {
      return null;
    }
    const { base, axis, colors } = chartBase(this.theme);
    return {
      ...base,
      grid: { ...base.grid, top: 34, bottom: 54 },
      tooltip: { ...base.tooltip, trigger: 'axis', valueFormatter: (v: number) => (v == null ? '—' : String(v)) },
      legend: { ...base.legend },
      xAxis: { type: 'category', data: t.map((p) => (/^\d{4}-\d{2}-\d{2}$/.test(p.label) ? frDate(p.label, { day: '2-digit', month: '2-digit' }) : p.label)), ...axis, splitLine: { show: false } },
      yAxis: [
        { type: 'value', name: 'Jours-personnes', nameTextStyle: { color: colors.muted, align: 'left' }, ...axis, minInterval: 1 },
        { type: 'value', name: 'Présence', min: (v: { min: number }) => Math.max(0, Math.floor(v.min * 10) / 10), max: 1,
          nameTextStyle: { color: colors.muted }, ...axis, splitLine: { show: false },
          axisLabel: { ...axis.axisLabel, formatter: (v: number) => `${Math.round(v * 100)} %` } },
      ],
      series: [
        { name: 'Absences ponctuelles', type: 'bar', stack: 'abs', data: t.map((p) => p.absencesPonctuelles), itemStyle: { color: colors.absent, borderRadius: [0, 0, 0, 0] }, barMaxWidth: 18 },
        { name: 'Absences longues', type: 'bar', stack: 'abs', data: t.map((p) => p.absencesLongues), itemStyle: { color: colors.long }, barMaxWidth: 18 },
        { name: 'Retards', type: 'bar', data: t.map((p) => p.lateDays), itemStyle: { color: colors.c3 }, barMaxWidth: 18 },
        { name: 'Taux de présence', type: 'line', yAxisIndex: 1, smooth: false, symbolSize: 6, data: t.map((p) => p.presenceRate),
          lineStyle: { width: 2.5, color: colors.c1 }, itemStyle: { color: colors.c1 },
          tooltip: { valueFormatter: (v: number) => (v == null ? '—' : `${(v * 100).toFixed(1)} %`) } },
      ],
    };
  });

  readonly heatOptions = computed<EChartsCoreOption | null>(() => {
    this.theme.mode();
    const cells = this.heat();
    if (!cells.length) {
      return null;
    }
    const { base, axis, colors } = chartBase(this.theme);
    const weeks = [...new Set(cells.map((c) => c.weekStart))].sort();
    const max = Math.max(1, ...cells.map((c) => c.absences));
    return {
      ...base,
      grid: { left: 8, right: 8, top: 8, bottom: 56, containLabel: true },
      tooltip: {
        ...base.tooltip,
        formatter: (p: { data: { cell: HeatCell } }) => {
          const c = p.data.cell;
          return `<strong>${DAYS[c.weekday - 1]} — semaine du ${frDate(c.weekStart)}</strong><br/>`
            + `Absences ponctuelles : ${c.absences} / ${c.expectedDays} j.<br/>Retards : ${c.lateDays}`;
        },
      },
      xAxis: { type: 'category', data: weeks.map((w) => `S${cells.find((c) => c.weekStart === w)!.isoWeek}`), ...axis, splitArea: { show: false } },
      yAxis: { type: 'category', data: DAYS, inverse: true, ...axis },
      visualMap: {
        min: 0, max, calculable: false, orient: 'horizontal', left: 'center', bottom: 0, itemHeight: 140, itemWidth: 10,
        text: [`${max} abs.`, '0'], textStyle: { color: colors.muted, fontSize: 11 },
        inRange: { color: [colors.surface2, colors.c3, colors.absent] },
      },
      series: [{
        type: 'heatmap',
        data: cells.map((c) => ({ value: [weeks.indexOf(c.weekStart), c.weekday - 1, c.absences], cell: c })),
        label: { show: weeks.length <= 12, color: colors.text, fontSize: 11, formatter: (p: { value: number[] }) => (p.value[2] ? String(p.value[2]) : '') },
        itemStyle: { borderColor: colors.surface, borderWidth: 2, borderRadius: 3 },
      }],
    };
  });

  readonly arrivalOptions = computed<EChartsCoreOption | null>(() => {
    this.theme.mode();
    const a = this.arrivals();
    if (!a || !a.days) {
      return null;
    }
    const { base, axis, colors } = chartBase(this.theme);
    const delta = this.arrivalView() === 'delta';
    const bins = delta ? a.deltaToReference : a.arrivalTimes;
    return {
      ...base,
      legend: { show: false },
      grid: { ...base.grid, bottom: 30 },
      tooltip: { ...base.tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: bins.map((b) => b.label), ...axis, splitLine: { show: false },
        name: delta ? 'minutes vs horaire de référence' : '', nameLocation: 'middle', nameGap: 28, nameTextStyle: { color: colors.muted } },
      yAxis: { type: 'value', name: 'Jours', nameTextStyle: { color: colors.muted }, ...axis, minInterval: 1 },
      series: [{
        type: 'bar', name: 'Jours de présence', barMaxWidth: 22,
        data: bins.map((b) => ({
          value: b.count,
          itemStyle: { color: delta ? (deltaValue(b.label) >= 10 ? colors.late : colors.c1) : colors.c1, borderRadius: [3, 3, 0, 0] },
        })),
      }],
    };
  });
}

/** Borne basse d'une tranche d'écart (« -50 », « +10 », « ≥ +120 »). */
function deltaValue(label: string): number {
  const m = label.match(/-?\d+/);
  return m ? Number(m[0]) : 0;
}
