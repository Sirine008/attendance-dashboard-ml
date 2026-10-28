package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import tn.attijari.assiduite.analytics.AnalyticsService;
import tn.attijari.assiduite.domain.Team;
import tn.attijari.assiduite.security.AccessScopeService;
import tn.attijari.assiduite.security.CurrentUser;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Vue d'ensemble", description = "Indicateurs agrégés, filtrés selon le périmètre de l'utilisateur")
public class DashboardController {

    private final AnalyticsService analytics;
    private final AccessScopeService scope;
    private final PeriodParams periods;

    public DashboardController(AnalyticsService analytics, AccessScopeService scope, PeriodParams periods) {
        this.analytics = analytics;
        this.scope = scope;
        this.periods = periods;
    }

    public record TeamView(Long id, String name, boolean synthetic) {
    }

    public record Meta(LocalDate dataStart, LocalDate dataEnd, List<TeamView> teams) {
    }

    @GetMapping("/meta")
    @Operation(summary = "Bornes des données disponibles et équipes visibles")
    public Meta meta() {
        List<TeamView> teams = scope.visibleTeams(CurrentUser.get()).stream()
                .map((Team t) -> new TeamView(t.getId(), t.getName(), t.isSynthetic())).toList();
        return new Meta(periods.dataStart(), periods.dataEnd(), teams);
    }

    private List<Long> ids(Long teamId, boolean synthetic) {
        return scope.visibleEmployeeIds(CurrentUser.get(), teamId, synthetic);
    }

    @GetMapping("/dashboard/kpis")
    @Operation(summary = "KPIs de la période + période précédente de même durée (pour les variations)")
    public AnalyticsService.KpiResponse kpis(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Long teamId,
                                             @RequestParam(defaultValue = "false") boolean synthetic) {
        PeriodParams.Period p = periods.resolve(from, to);
        return analytics.kpis(ids(teamId, synthetic), p.from(), p.to());
    }

    @GetMapping("/dashboard/trend")
    @Operation(summary = "Tendance (granularité day | week | month, auto par défaut)")
    public List<AnalyticsService.TrendPoint> trend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) Long teamId,
                                                   @RequestParam(required = false) String granularity,
                                                   @RequestParam(defaultValue = "false") boolean synthetic) {
        PeriodParams.Period p = periods.resolve(from, to);
        return analytics.trend(ids(teamId, synthetic), p.from(), p.to(), granularity);
    }

    @GetMapping("/dashboard/heatmap")
    @Operation(summary = "Absences ponctuelles par jour de semaine × semaine")
    public List<AnalyticsService.HeatCell> heatmap(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) Long teamId,
                                                   @RequestParam(defaultValue = "false") boolean synthetic) {
        PeriodParams.Period p = periods.resolve(from, to);
        return analytics.heatmap(ids(teamId, synthetic), p.from(), p.to());
    }

    @GetMapping("/dashboard/arrivals")
    @Operation(summary = "Répartition des heures d'arrivée et de l'écart à l'horaire de référence")
    public AnalyticsService.ArrivalDistribution arrivals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         @RequestParam(required = false) Long teamId,
                                                         @RequestParam(defaultValue = "false") boolean synthetic) {
        PeriodParams.Period p = periods.resolve(from, to);
        return analytics.arrivals(ids(teamId, synthetic), p.from(), p.to());
    }
}
