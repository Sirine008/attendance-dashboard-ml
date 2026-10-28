import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { approxPct } from '../core/format';
import { RiskLevel } from '../core/models';
import { RISK } from '../core/status';

/** Niveau de risque : icône + libellé (+ probabilité arrondie), jamais la couleur seule. */
@Component({
  selector: 'app-risk-badge',
  imports: [MatIconModule, MatTooltipModule],
  template: `
    @if (status() === 'INSUFFICIENT_HISTORY') {
      <span class="badge muted" matTooltip="Moins de 30 jours ouvrés d'historique : pas d'estimation affichée">
        <mat-icon aria-hidden="true">hourglass_empty</mat-icon>Historique insuffisant
      </span>
    } @else if (meta(); as m) {
      <span class="badge" [style.color]="m.color" [style.background]="m.bg" [style.borderColor]="m.color"
            [attr.aria-label]="'Risque ' + m.label + (probability() !== null ? ', ' + prob() : '')">
        <mat-icon aria-hidden="true">{{ m.icon }}</mat-icon>{{ m.label }}
        @if (showProbability() && probability() !== null) {
          <span class="p tabular">{{ prob() }}</span>
        }
      </span>
    } @else {
      <span class="badge muted">—</span>
    }
  `,
  styles: `
    .badge { display: inline-flex; align-items: center; gap: 3px; padding: 2px 9px 2px 5px; border-radius: 999px;
      font-size: .78rem; font-weight: 600; border: 1px solid transparent; white-space: nowrap; }
    .badge mat-icon { font-size: 15px; width: 15px; height: 15px; }
    .badge.muted { color: var(--text-muted); background: var(--surface-2); border-color: var(--border); font-weight: 500; }
    .p { font-weight: 500; margin-left: 4px; opacity: .9; }
  `,
})
export class RiskBadgeComponent {
  readonly level = input<RiskLevel | null>(null);
  readonly status = input<string | null>('OK');
  readonly probability = input<number | null>(null);
  readonly showProbability = input(false);
  readonly meta = computed(() => (this.level() ? RISK[this.level()!] : null));
  readonly prob = computed(() => approxPct(this.probability()));
}
