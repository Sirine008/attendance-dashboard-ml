import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

/**
 * Carte KPI avec variation vs période précédente.
 * `goodWhen` indique le sens favorable : la couleur de la variation est doublée d'une flèche et d'un texte.
 */
@Component({
  selector: 'app-kpi-card',
  imports: [MatIconModule, MatTooltipModule],
  template: `
    <div class="kpi" [class.accent]="accent()" [attr.aria-busy]="loading()">
      <div class="head">
        <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
        <span class="label">{{ label() }}</span>
        @if (hint()) {
          <mat-icon class="hint" [matTooltip]="hint()!" aria-hidden="false" [attr.aria-label]="hint()">info</mat-icon>
        }
      </div>
      @if (loading()) {
        <div class="skeleton value-skel"></div>
        <div class="skeleton delta-skel"></div>
      } @else {
        <div class="value tabular">{{ value() }}</div>
        @if (deltaText(); as d) {
          <div class="delta" [class]="tone()" [attr.aria-label]="'Variation : ' + d + ' (' + toneLabel() + ')'">
            <mat-icon aria-hidden="true">{{ deltaIcon() }}</mat-icon>
            <span class="tabular">{{ d }}</span>
            <span class="vs">vs préc.</span>
          </div>
        } @else {
          <div class="delta neutral"><span class="vs">pas de comparaison</span></div>
        }
      }
    </div>
  `,
  styles: `
    .kpi { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 16px;
      box-shadow: var(--shadow); display: flex; flex-direction: column; gap: 6px; height: 100%; position: relative; overflow: hidden; }
    .kpi.accent::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 4px; background: var(--brand-red); }
    .head { display: flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: .82rem; font-weight: 500; }
    .head mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .hint { margin-left: auto; color: var(--text-faint); cursor: help; }
    .label { line-height: 1.2; }
    .value { font-size: 1.65rem; font-weight: 650; letter-spacing: -0.02em; color: var(--text); }
    .delta { display: flex; align-items: center; gap: 4px; font-size: .78rem; font-weight: 600; white-space: nowrap; }
    .delta mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .delta .vs { color: var(--text-faint); font-weight: 400; }
    .good { color: var(--success); } .bad { color: var(--danger); } .neutral { color: var(--text-muted); }
    .value-skel { height: 32px; width: 60%; } .delta-skel { height: 14px; width: 80%; }
  `,
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input<string>('—');
  readonly icon = input('insights');
  readonly delta = input<number | null>(null);
  /** points = points de pourcentage ; number = unités ; percent = variation relative */
  readonly deltaKind = input<'points' | 'number' | 'minutes' | 'hours'>('number');
  readonly goodWhen = input<'up' | 'down' | 'neutral'>('neutral');
  readonly loading = input(false);
  readonly accent = input(false);
  readonly hint = input<string | null>(null);

  readonly deltaText = computed(() => {
    const d = this.delta();
    if (d === null || d === undefined || Number.isNaN(d)) {
      return null;
    }
    const sign = d > 0 ? '+' : d < 0 ? '−' : '±';
    const a = Math.abs(d);
    switch (this.deltaKind()) {
      case 'points':
        return `${sign}${(a * 100).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} pt`;
      case 'minutes':
        return `${sign}${a.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} min`;
      case 'hours':
        return `${sign}${Math.round(a * 60)} min`;
      default:
        return `${sign}${a.toLocaleString('fr-FR', { maximumFractionDigits: 1 })}`;
    }
  });

  readonly tone = computed(() => {
    const d = this.delta();
    if (!d || this.goodWhen() === 'neutral') {
      return 'neutral';
    }
    return (d > 0) === (this.goodWhen() === 'up') ? 'good' : 'bad';
  });

  readonly toneLabel = computed(() => ({ good: 'évolution favorable', bad: 'évolution défavorable', neutral: 'neutre' })[this.tone()]);
  readonly deltaIcon = computed(() => {
    const d = this.delta() ?? 0;
    return d > 0 ? 'arrow_upward' : d < 0 ? 'arrow_downward' : 'remove';
  });
}
