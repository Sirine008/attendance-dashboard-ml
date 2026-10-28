import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { EChartsCoreOption } from 'echarts/core';
import { catchError, of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { chartBase } from '../../core/chart-theme';
import { dateTime, frDate, num, pct } from '../../core/format';
import { Backtest, Horizon, HorizonSummary, MetricSet, ModelInfo } from '../../core/models';
import { ThemeService } from '../../core/theme.service';
import { ChartCardComponent } from '../../shared/chart-card.component';

const NAMES: Record<string, string> = {
  logistic: 'Régression logistique',
  lgbm: 'LightGBM (gradient boosting)',
  baseline_hist: 'Baseline (a) — taux historique',
  baseline_dow: 'Baseline (b) — taux par jour de semaine',
};
const KINDS = ['logistic', 'lgbm', 'baseline_hist', 'baseline_dow'];

@Component({
  selector: 'app-model-health',
  imports: [MatButtonModule, MatIconModule, MatButtonToggleModule, MatProgressBarModule, MatSnackBarModule, ChartCardComponent],
  templateUrl: './model-health.component.html',
  styles: `
    .summary { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
    .summary .card { padding: 14px 16px; }
    .k { color: var(--text-muted); font-size: .8rem; margin: 0 0 4px; }
    .v { font-weight: 650; font-size: 1.05rem; margin: 0; overflow-wrap: anywhere; }
    .verdict { display: flex; gap: 10px; align-items: flex-start; }
    table.simple { width: 100%; border-collapse: collapse; font-size: .88rem; }
    table.simple th { text-align: left; font-weight: 600; color: var(--text-muted); padding: 8px 10px; border-bottom: 1px solid var(--border); }
    table.simple td { padding: 7px 10px; border-bottom: 1px solid var(--border); font-variant-numeric: tabular-nums; }
    table.simple td.num, table.simple th.num { text-align: right; }
    tr.chosen td { background: var(--brand-yellow-soft); font-weight: 600; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .confirm { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .hz { align-self: flex-start; }
  `,
})
export class ModelHealthComponent {
  private readonly api = inject(ApiService);
  private readonly theme = inject(ThemeService);
  private readonly snack = inject(MatSnackBar);
  protected readonly NAMES = NAMES;
  protected readonly KINDS = KINDS;
  protected readonly fmt = { dateTime, frDate, num };

  readonly info = signal<ModelInfo | null>(null);
  readonly loading = signal(true);
  readonly horizon = signal<Horizon>('D1');
  readonly backtest = signal<Backtest | null>(null);
  readonly backtestLoading = signal(true);
  readonly busy = signal(false);
  readonly confirming = signal(false);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.modelInfo().pipe(catchError(() => of(null))).subscribe((i) => {
      this.info.set(i);
      this.loading.set(false);
    });
    this.loadBacktest();
  }

  loadBacktest(): void {
    this.backtestLoading.set(true);
    this.api.backtest(this.horizon()).pipe(catchError(() => of(null))).subscribe((b) => {
      this.backtest.set(b);
      this.backtestLoading.set(false);
    });
  }

  setHorizon(h: Horizon): void {
    this.horizon.set(h);
    this.loadBacktest();
  }

  readonly summary = computed(() => this.info()?.active?.metrics ?? null);
  readonly hz = computed<HorizonSummary | null>(() => this.summary()?.horizons[this.horizon()] ?? null);

  rows(set: 'validation' | 'test'): { kind: string; m: MetricSet }[] {
    const h = this.hz();
    return h ? KINDS.filter((k) => h[set][k]).map((k) => ({ kind: k, m: h[set][k] })) : [];
  }

  pct1(v: number): string {
    return pct(v);
  }

  f3(v: number | null | undefined): string {
    return v === null || v === undefined ? '—' : v.toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 });
  }

  readonly calibrationOptions = computed<EChartsCoreOption | null>(() => {
    this.theme.mode();
    const h = this.hz();
    if (!h) {
      return null;
    }
    const { base, axis, colors } = chartBase(this.theme);
    const palette: Record<string, string> = { logistic: colors.c2, lgbm: colors.c1, baseline_hist: colors.muted, baseline_dow: colors.c3 };
    const series = KINDS.filter((k) => h.test[k]?.calibration_curve).map((k) => {
      const c = h.test[k].calibration_curve!;
      return { name: NAMES[k], type: 'line', symbolSize: 7, data: c.predicted.map((p, i) => [p, c.observed[i]]),
        lineStyle: { color: palette[k], width: k === h.algorithm ? 3 : 1.5 }, itemStyle: { color: palette[k] } };
    });
    const max = Math.max(0.05, ...series.flatMap((s) => s.data.flat()));
    return {
      ...base,
      grid: { ...base.grid, top: 20, bottom: 60 },
      tooltip: { ...base.tooltip, trigger: 'item', formatter: (p: { seriesName: string; data: number[] }) =>
        `${p.seriesName}<br/>Prédit ${(p.data[0] * 100).toFixed(1)} % — observé ${(p.data[1] * 100).toFixed(1)} %` },
      xAxis: { type: 'value', name: 'Probabilité prédite', nameLocation: 'middle', nameGap: 26, min: 0, max, ...axis,
        axisLabel: { ...axis.axisLabel, formatter: (v: number) => `${Math.round(v * 100)} %` } },
      yAxis: { type: 'value', min: 0, max, ...axis,
        axisLabel: { ...axis.axisLabel, formatter: (v: number) => `${Math.round(v * 100)} %` } },
      series: [
        { name: 'Calibration parfaite', type: 'line', symbol: 'none', data: [[0, 0], [max, max]], lineStyle: { type: 'dashed', color: colors.muted }, itemStyle: { color: colors.muted } },
        ...series,
      ],
    };
  });

  readonly backtestOptions = computed<EChartsCoreOption | null>(() => {
    this.theme.mode();
    const b = this.backtest();
    if (!b?.weeks.length) {
      return null;
    }
    const { base, axis, colors } = chartBase(this.theme);
    return {
      ...base,
      tooltip: { ...base.tooltip, trigger: 'axis' },
      xAxis: { type: 'category', data: b.weeks.map((w) => `sem. ${frDate(w.week_start, { day: '2-digit', month: '2-digit' })}`), ...axis, splitLine: { show: false } },
      yAxis: { type: 'value', ...axis, minInterval: 1 },
      series: [
        { name: 'Absences réelles', type: 'bar', data: b.weeks.map((w) => w.actual_absences), itemStyle: { color: colors.absent, borderRadius: [3, 3, 0, 0] }, barMaxWidth: 26 },
        { name: 'Absences attendues (somme des probabilités)', type: 'line', data: b.weeks.map((w) => w.expected_absences), symbolSize: 7,
          lineStyle: { color: colors.c1, width: 2.5 }, itemStyle: { color: colors.c1 } },
      ],
    };
  });

  retrain(): void {
    this.confirming.set(false);
    this.busy.set(true);
    this.api.train().pipe(catchError(() => of(null))).subscribe((r) => {
      this.busy.set(false);
      this.snack.open(r ? 'Nouveau modèle entraîné et prédictions régénérées.' : 'Échec du réentraînement (service indisponible).', 'OK', { duration: 6000 });
      this.load();
    });
  }

  runPredictions(): void {
    this.busy.set(true);
    this.api.runPredictions().pipe(catchError(() => of(null))).subscribe((r) => {
      this.busy.set(false);
      this.snack.open(r?.success ? `Prédictions générées (${r.stored}).` : 'Échec : service de prédiction indisponible.', 'OK', { duration: 6000 });
      this.load();
    });
  }
}
