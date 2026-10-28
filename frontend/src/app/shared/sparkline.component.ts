import { Component, computed, input } from '@angular/core';

/** Mini-courbe SVG (présence hebdomadaire), légère pour les tableaux. */
@Component({
  selector: 'app-sparkline',
  template: `
    @if (points(); as p) {
      <svg [attr.width]="width()" [attr.height]="height()" [attr.viewBox]="'0 0 ' + width() + ' ' + height()"
           role="img" [attr.aria-label]="label()">
        <polyline [attr.points]="p.line" fill="none" stroke="var(--chart-1)" stroke-width="1.5" stroke-linejoin="round" />
        <circle [attr.cx]="p.lastX" [attr.cy]="p.lastY" r="2.5" fill="var(--brand-red)" />
      </svg>
    } @else {
      <span class="faint small">—</span>
    }
  `,
  styles: `:host { display: inline-flex; align-items: center; }`,
})
export class SparklineComponent {
  readonly values = input<(number | null)[]>([]);
  readonly width = input(88);
  readonly height = input(24);

  readonly points = computed(() => {
    const v = this.values().map((x) => (x === null ? NaN : x));
    const valid = v.filter((x) => !Number.isNaN(x));
    if (valid.length < 2) {
      return null;
    }
    const w = this.width(), h = this.height(), pad = 3;
    const min = Math.min(...valid, 0.6), max = Math.max(...valid, 1);
    const step = (w - 2 * pad) / (v.length - 1);
    const pts: string[] = [];
    let lastX = 0, lastY = 0;
    v.forEach((x, i) => {
      if (Number.isNaN(x)) {
        return;
      }
      lastX = pad + i * step;
      lastY = h - pad - ((x - min) / (max - min || 1)) * (h - 2 * pad);
      pts.push(`${lastX.toFixed(1)},${lastY.toFixed(1)}`);
    });
    return { line: pts.join(' '), lastX, lastY };
  });

  readonly label = computed(() => {
    const v = this.values().filter((x): x is number => x !== null);
    return v.length ? `Présence hebdomadaire, dernière semaine ${Math.round(v[v.length - 1] * 100)} %` : 'Pas de données';
  });
}
