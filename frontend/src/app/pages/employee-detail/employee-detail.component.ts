import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { EChartsCoreOption } from 'echarts/core';
import { catchError, combineLatest, debounceTime, filter, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { chartBase } from '../../core/chart-theme';
import { FiltersService } from '../../core/filters.service';
import { approxPct, frDate, hm, hours, initials, longDate, minutesToHm, num, parseIso, pct } from '../../core/format';
import { ArrivalPoint, CalendarDay, DayDetail, EmployeePredictions, EmployeeProfile, Horizon, PredictionView } from '../../core/models';
import { ATTENDANCE, RISK, dayMeta } from '../../core/status';
import { ThemeService } from '../../core/theme.service';
import { ChartCardComponent } from '../../shared/chart-card.component';
import { KpiCardComponent } from '../../shared/kpi-card.component';
import { PeriodFilterComponent } from '../../shared/period-filter.component';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';

interface Segment {
  left: number;
  width: number;
  label: string;
}

const DAY_START = 6 * 60;
const DAY_END = 20 * 60;

@Component({
  selector: 'app-employee-detail',
  imports: [RouterLink, MatIconModule, MatButtonModule, MatButtonToggleModule, MatTooltipModule, KpiCardComponent, ChartCardComponent,
    PeriodFilterComponent, RiskBadgeComponent],
  templateUrl: './employee-detail.component.html',
  styleUrl: './employee-detail.component.scss',
})
export class EmployeeDetailComponent {
  readonly id = input.required<string>();
  protected readonly f = inject(FiltersService);
  private readonly api = inject(ApiService);
  private readonly theme = inject(ThemeService);
  protected readonly fmt = { pct, num, hours, hm, frDate, longDate, approxPct, minutesToHm };
  protected readonly ATTENDANCE = ATTENDANCE;
  protected readonly RISK = RISK;
  protected readonly legend = ['PRESENT', 'RETARD', 'ABSENCE_PONCTUELLE', 'ABSENCE_LONGUE', 'CONGE', 'FERIE', 'INCOMPLET'] as const;

  readonly profile = signal<EmployeeProfile | null>(null);
  readonly notFound = signal<string | null>(null);
  readonly month = signal<string | null>(null);
  readonly calendar = signal<CalendarDay[]>([]);
  readonly calendarLoading = signal(true);
  readonly selectedDay = signal<string | null>(null);
  readonly dayDetail = signal<DayDetail | null>(null);
  readonly arrivals = signal<ArrivalPoint[]>([]);
  readonly arrivalsLoading = signal(true);
  readonly predictions = signal<EmployeePredictions | null>(null);
  readonly historyHorizon = signal<Horizon>('D1');

  constructor() {
    const id$ = toObservable(this.id);
    const scope$ = toObservable(this.f.period);
    combineLatest([id$, scope$])
      .pipe(
        filter(() => this.f.meta() !== null),
        debounceTime(100),
        switchMap(([id, p]) => {
          this.arrivalsLoading.set(true);
          return combineLatest([
            this.api.employee(Number(id), p.from, p.to).pipe(catchError((e) => {
              this.notFound.set(e.status === 403 ? 'Ce collaborateur ne fait pas partie de votre périmètre.' : 'Collaborateur introuvable.');
              return of(null);
            })),
            this.api.employeeArrivals(Number(id), p.from, p.to).pipe(catchError(() => of([] as ArrivalPoint[]))),
          ]);
        }),
        takeUntilDestroyed(),
      )
      .subscribe(([profile, arrivals]) => {
        this.profile.set(profile);
        this.arrivals.set(arrivals);
        this.arrivalsLoading.set(false);
        if (profile && !this.month()) {
          this.month.set(this.clampMonth(this.f.period().to.slice(0, 7)));
        }
      });

    id$.pipe(switchMap((id) => this.api.employeePredictions(Number(id)).pipe(catchError(() => of(null)))), takeUntilDestroyed())
      .subscribe((p) => this.predictions.set(p));

    combineLatest([id$, toObservable(this.month)])
      .pipe(
        filter(([, m]) => !!m),
        switchMap(([id, m]) => {
          this.calendarLoading.set(true);
          return this.api.calendar(Number(id), m!).pipe(catchError(() => of([] as CalendarDay[])));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((days) => {
        this.calendar.set(days);
        this.calendarLoading.set(false);
        const sel = this.selectedDay();
        if (!sel || !days.some((d) => d.date === sel)) {
          const last = [...days].reverse().find((d) => d.status && d.status !== 'ABSENCE_LONGUE');
          this.selectDay(last?.date ?? null);
        }
      });

    effect(() => {
      // changement de période globale : le calendrier suit le mois de fin de période
      const to = this.f.period().to;
      if (this.profile()) {
        this.month.set(this.clampMonth(to.slice(0, 7)));
      }
    });
  }

  private clampMonth(m: string): string {
    const meta = this.f.meta();
    if (!meta) {
      return m;
    }
    const min = meta.dataStart.slice(0, 7), max = meta.dataEnd.slice(0, 7);
    return m < min ? min : m > max ? max : m;
  }

  shiftMonth(delta: number): void {
    const m = this.month();
    if (!m) {
      return;
    }
    const d = parseIso(`${m}-01`);
    d.setMonth(d.getMonth() + delta);
    this.month.set(this.clampMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`));
  }

  readonly canPrev = computed(() => !!this.month() && !!this.f.meta() && this.month()! > this.f.meta()!.dataStart.slice(0, 7));
  readonly canNext = computed(() => !!this.month() && !!this.f.meta() && this.month()! < this.f.meta()!.dataEnd.slice(0, 7));
  readonly monthLabel = computed(() => (this.month() ? parseIso(`${this.month()}-01`).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }) : ''));

  /** Grille lundi → dimanche avec cases vides de début de mois. */
  readonly grid = computed(() => {
    const days = this.calendar();
    if (!days.length) {
      return [];
    }
    const offset = (parseIso(days[0].date).getDay() + 6) % 7;
    return [...Array.from({ length: offset }, () => null), ...days];
  });

  selectDay(date: string | null): void {
    this.selectedDay.set(date);
    this.dayDetail.set(null);
    if (date) {
      this.api.day(Number(this.id()), date).pipe(catchError(() => of(null))).subscribe((d) => this.dayDetail.set(d));
    }
  }

  meta(d: CalendarDay) {
    return dayMeta(d.kind, d.status);
  }

  dayAria(d: CalendarDay): string {
    const m = this.meta(d);
    const parts = [longDate(d.date), m.label];
    if (d.firstIn) parts.push(`arrivée ${hm(d.firstIn)}`);
    if (d.lateMinutes) parts.push(`${d.lateMinutes} min de retard`);
    if (d.incomplete) parts.push('pointage incomplet');
    if (d.label) parts.push(d.label);
    return parts.join(', ');
  }

  readonly initials = computed(() => initials(this.profile()?.displayName ?? ''));

  readonly kpiCards = computed(() => {
    const p = this.profile();
    if (!p) {
      return [];
    }
    const c = p.kpis, prev = p.previousKpis;
    const d = (a: number | null, b: number | null) => (a === null || b === null ? null : a - b);
    return [
      { label: 'Taux de présence', icon: 'how_to_reg', value: pct(c.presenceRate), delta: d(c.presenceRate, prev.presenceRate), kind: 'points' as const, good: 'up' as const },
      { label: 'Absences ponctuelles', icon: 'person_off', value: num(c.absencesPonctuelles), delta: d(c.absencesPonctuelles, prev.absencesPonctuelles), kind: 'number' as const, good: 'down' as const },
      { label: 'Absences longues', icon: 'event_busy', value: num(c.absencesLongues), delta: d(c.absencesLongues, prev.absencesLongues), kind: 'number' as const, good: 'neutral' as const },
      { label: 'Retards', icon: 'schedule', value: num(c.lateDays), delta: d(c.lateDays, prev.lateDays), kind: 'number' as const, good: 'down' as const },
      { label: 'Retard moyen', icon: 'timer', value: c.avgLateMinutes !== null ? `${num(c.avgLateMinutes)} min` : '—', delta: d(c.avgLateMinutes, prev.avgLateMinutes), kind: 'minutes' as const, good: 'down' as const },
      { label: 'Heures moyennes', icon: 'hourglass_bottom', value: hours(c.avgWorkedHours), delta: d(c.avgWorkedHours, prev.avgWorkedHours), kind: 'hours' as const, good: 'neutral' as const },
      { label: 'Pointages incomplets', icon: 'rule', value: num(c.incompleteDays), delta: d(c.incompleteDays, prev.incompleteDays), kind: 'number' as const, good: 'down' as const },
    ];
  });

  // ------------------------------------------------------------------ timeline d'une journée
  readonly timeline = computed(() => {
    const d = this.dayDetail();
    if (!d) {
      return null;
    }
    const toMin = (t: string) => {
      const [h, m, s] = t.split(':').map(Number);
      return h * 60 + m + (s ?? 0) / 60;
    };
    const pos = (m: number) => ((Math.min(Math.max(m, DAY_START), DAY_END) - DAY_START) / (DAY_END - DAY_START)) * 100;
    const boundary = d.punches.filter((p) => p.boundary && !p.duplicate);
    const segments: Segment[] = [];
    for (let i = 0; i < boundary.length - 1; i++) {
      if (boundary[i].direction === 'IN' && boundary[i + 1].direction === 'OUT') {
        const a = toMin(boundary[i].time), b = toMin(boundary[i + 1].time);
        segments.push({ left: pos(a), width: Math.max(pos(b) - pos(a), 0.5), label: `${hm(boundary[i].time)} → ${hm(boundary[i + 1].time)}` });
        i++;
      }
    }
    const markers = d.punches.map((p) => ({ ...p, left: pos(toMin(p.time)) }));
    const ticks = Array.from({ length: (DAY_END - DAY_START) / 120 + 1 }, (_, i) => ({ label: `${(DAY_START / 60) + i * 2}h`, left: (i * 120 / (DAY_END - DAY_START)) * 100 }));
    return { segments, markers, ticks };
  });

  // ------------------------------------------------------------------ arrivées vs référence
  readonly arrivalOptions = computed<EChartsCoreOption | null>(() => {
    this.theme.mode();
    const pts = this.arrivals().filter((p) => p.firstInMinutes !== null);
    if (!pts.length) {
      return null;
    }
    const { base, axis, colors } = chartBase(this.theme);
    const fmtMin = (v: number) => minutesToHm(v);
    const values = pts.map((p) => p.firstInMinutes!);
    const refs = pts.map((p) => p.referenceMinutes ?? null);
    const lo = Math.min(...values, ...refs.filter((r): r is number => r !== null)) - 15;
    const hi = Math.max(...values, ...refs.filter((r): r is number => r !== null)) + 15;
    return {
      ...base,
      tooltip: { ...base.tooltip, trigger: 'axis', valueFormatter: (v: number) => (v == null ? '—' : fmtMin(v)) },
      xAxis: { type: 'category', data: pts.map((p) => frDate(p.date, { day: '2-digit', month: '2-digit' })), ...axis, splitLine: { show: false } },
      yAxis: { type: 'value', min: Math.floor(lo / 15) * 15, max: Math.ceil(hi / 15) * 15, inverse: false, ...axis,
        axisLabel: { ...axis.axisLabel, formatter: fmtMin } },
      series: [
        { name: 'Horaire de référence', type: 'line', step: 'middle', symbol: pts.length < 3 ? 'diamond' : 'none', symbolSize: 8, data: refs,
          lineStyle: { color: colors.c3, width: 2, type: 'dashed' }, itemStyle: { color: colors.c3 } },
        { name: 'Heure d\'arrivée', type: 'line', data: pts.map((p) => ({ value: p.firstInMinutes, itemStyle: { color: p.late ? colors.late : colors.c1 } })),
          symbolSize: 6, lineStyle: { color: colors.c1, width: 1.5 }, itemStyle: { color: colors.c1 } },
      ],
    };
  });

  // ------------------------------------------------------------------ prédictions
  readonly history = computed(() => {
    const p = this.predictions();
    if (!p) {
      return [];
    }
    return (this.historyHorizon() === 'D1' ? p.historyD1 : p.historyW1).slice(0, 15);
  });

  readonly hitSummary = computed(() => {
    const items = this.history().filter((h) => h.actualAbsence !== null && h.prediction.status === 'OK');
    if (!items.length) {
      return null;
    }
    const high = items.filter((h) => h.prediction.riskLevel === 'ELEVE');
    const absent = items.filter((h) => h.actualAbsence);
    return { n: items.length, high: high.length, highAbsent: high.filter((h) => h.actualAbsence).length, absent: absent.length };
  });

  periodText(p: PredictionView): string {
    return p.horizon === 'D1' ? longDate(p.targetDate) : `du ${frDate(p.targetDate)} au ${frDate(p.targetEnd)}`;
  }
}
