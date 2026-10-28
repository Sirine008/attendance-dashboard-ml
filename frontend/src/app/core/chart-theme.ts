import { ThemeService } from './theme.service';

/** Palette et styles ECharts dérivés des tokens CSS (relus à chaque changement de thème). */
export function chartBase(theme: ThemeService) {
  const text = theme.token('--text');
  const muted = theme.token('--text-muted');
  const grid = theme.token('--chart-grid');
  const surface = theme.token('--surface');
  const border = theme.token('--border');
  return {
    colors: {
      c1: theme.token('--chart-1'),
      c2: theme.token('--chart-2'),
      c3: theme.token('--chart-3'),
      c4: theme.token('--chart-4'),
      c5: theme.token('--chart-5'),
      present: theme.token('--st-present'),
      late: theme.token('--st-late'),
      absent: theme.token('--st-absent'),
      long: theme.token('--st-long'),
      surface,
      surface2: theme.token('--surface-2'),
      text,
      muted,
    },
    base: {
      backgroundColor: 'transparent',
      textStyle: { fontFamily: 'Inter Variable, Segoe UI, system-ui, sans-serif', color: text },
      grid: { left: 8, right: 16, top: 30, bottom: 34, containLabel: true },
      tooltip: {
        backgroundColor: surface,
        borderColor: border,
        textStyle: { color: text, fontSize: 12 },
        extraCssText: 'box-shadow: 0 4px 16px rgba(0,0,0,.12); border-radius: 8px;',
      },
      legend: { bottom: 0, left: 'center', textStyle: { color: muted, fontSize: 12 }, icon: 'roundRect', itemWidth: 12, itemHeight: 8 },
    },
    axis: {
      axisLine: { lineStyle: { color: grid } },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 11 },
      splitLine: { lineStyle: { color: grid } },
    },
  };
}
