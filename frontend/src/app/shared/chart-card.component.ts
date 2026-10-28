import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';

/** Carte graphique : titre, état de chargement (skeleton), état vide, graphique ECharts. */
@Component({
  selector: 'app-chart-card',
  imports: [NgxEchartsDirective, MatIconModule],
  template: `
    <section class="card chart-card">
      <header>
        <div>
          <h2 class="card-title">{{ title() }}</h2>
          @if (subtitle()) {
            <p class="sub">{{ subtitle() }}</p>
          }
        </div>
        <ng-content select="[actions]" />
      </header>
      @if (loading()) {
        <div class="skeleton" [style.height.px]="height()" aria-label="Chargement du graphique"></div>
      } @else if (empty() || !options()) {
        <div class="empty-state" [style.height.px]="height()">
          <mat-icon aria-hidden="true">insert_chart</mat-icon>
          <span>{{ emptyText() }}</span>
        </div>
      } @else {
        <div echarts class="chart" [options]="options()!" [autoResize]="true" [style.height.px]="height()"
             role="img" [attr.aria-label]="title()"></div>
      }
      <ng-content />
    </section>
  `,
  styles: `
    .chart-card { display: flex; flex-direction: column; gap: 8px; }
    header { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; flex-wrap: wrap; }
    h2 { margin: 0 !important; }
    .sub { margin: 2px 0 0; font-size: .82rem; color: var(--text-muted); }
    .chart { width: 100%; }
  `,
})
export class ChartCardComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly options = input<EChartsCoreOption | null>(null);
  readonly loading = input(false);
  readonly empty = input(false);
  readonly emptyText = input('Aucune donnée sur la période sélectionnée');
  readonly height = input(280);
}
