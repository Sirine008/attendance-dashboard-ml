package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.attijari.assiduite.domain.Employee;
import tn.attijari.assiduite.domain.Horizon;
import tn.attijari.assiduite.prediction.PredictionQueryService;
import tn.attijari.assiduite.security.AccessScopeService;
import tn.attijari.assiduite.security.CurrentUser;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/predictions")
@Tag(name = "Risques & prédictions", description = "Estimations statistiques — aide à l'organisation du travail, jamais une évaluation")
public class PredictionController {

    private final AccessScopeService scope;
    private final PredictionQueryService predictions;

    public PredictionController(AccessScopeService scope, PredictionQueryService predictions) {
        this.scope = scope;
        this.predictions = predictions;
    }

    public record RiskCard(Long employeeId, String code, String displayName, String teamName, boolean synthetic,
                           PredictionQueryService.PredictionView prediction) {
    }

    @GetMapping("/latest")
    @Operation(summary = "Dernière estimation par collaborateur visible (horizon D1 ou W1), filtrable par niveau minimal")
    @Transactional(readOnly = true)
    public List<RiskCard> latest(@RequestParam(defaultValue = "D1") Horizon horizon,
                                 @RequestParam(required = false) Long teamId,
                                 @RequestParam(required = false) String minLevel,
                                 @RequestParam(defaultValue = "false") boolean synthetic) {
        List<Employee> emps = scope.visibleEmployees(CurrentUser.get(), teamId, synthetic);
        Map<Long, PredictionQueryService.PredictionView> latest = predictions.latest(emps.stream().map(Employee::getId).toList(), horizon);
        Set<String> levels = minLevel == null ? null : switch (minLevel) {
            case "ELEVE" -> Set.of("ELEVE");
            case "MODERE" -> Set.of("MODERE", "ELEVE");
            default -> null;
        };
        // Tri alphabétique volontaire : pas de classement « des pires » (cf. SPEC §7, page Risques).
        return emps.stream()
                .filter(e -> latest.containsKey(e.getId()))
                .filter(e -> levels == null || levels.contains(latest.get(e.getId()).riskLevel()))
                .sorted(Comparator.comparing(Employee::getDisplayName))
                .map(e -> new RiskCard(e.getId(), e.getCode(), e.getDisplayName(), e.getTeam() != null ? e.getTeam().getName() : null,
                        e.isSynthetic(), latest.get(e.getId())))
                .toList();
    }
}
