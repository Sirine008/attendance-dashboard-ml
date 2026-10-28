// Import ciblé d'ECharts (tree-shaking) : uniquement les graphiques et composants utilisés.
import { BarChart, HeatmapChart, LineChart, ScatterChart } from 'echarts/charts';
import {
  DatasetComponent, GridComponent, LegendComponent, MarkAreaComponent, MarkLineComponent, TooltipComponent, VisualMapComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([LineChart, BarChart, HeatmapChart, ScatterChart, GridComponent, TooltipComponent, LegendComponent,
  VisualMapComponent, MarkLineComponent, MarkAreaComponent, DatasetComponent, CanvasRenderer]);

export default echarts;
