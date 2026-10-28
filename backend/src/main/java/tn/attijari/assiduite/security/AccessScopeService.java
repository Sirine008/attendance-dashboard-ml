package tn.attijari.assiduite.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.domain.Employee;
import tn.attijari.assiduite.domain.Team;
import tn.attijari.assiduite.repository.EmployeeRepository;
import tn.attijari.assiduite.repository.TeamRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Filtrage côté serveur du périmètre visible : un MANAGER ne voit que son équipe ; RH et ADMIN voient tout.
 * Les collaborateurs synthétiques (démo) sont exclus sauf demande explicite ou filtre sur leur équipe.
 */
@Service
@Transactional(readOnly = true)
public class AccessScopeService {

    private final EmployeeRepository employees;
    private final TeamRepository teams;

    public AccessScopeService(EmployeeRepository employees, TeamRepository teams) {
        this.employees = employees;
        this.teams = teams;
    }

    /** Équipe effectivement filtrée : celle du manager (toute autre demande est refusée), sinon le filtre demandé. */
    public Long effectiveTeam(CurrentUser user, Long requestedTeamId) {
        if (user.isManager()) {
            if (requestedTeamId != null && !requestedTeamId.equals(user.teamId())) {
                throw new AccessDeniedException("Un manager ne peut consulter que son équipe");
            }
            return user.teamId();
        }
        return requestedTeamId;
    }

    public List<Employee> visibleEmployees(CurrentUser user, Long requestedTeamId, boolean includeSynthetic) {
        Long team = effectiveTeam(user, requestedTeamId);
        if (team != null) {
            return employees.findByTeamIdOrderByDisplayNameAsc(team);
        }
        return employees.findAllByOrderByDisplayNameAsc().stream()
                .filter(e -> includeSynthetic || !e.isSynthetic())
                .toList();
    }

    public List<Long> visibleEmployeeIds(CurrentUser user, Long requestedTeamId, boolean includeSynthetic) {
        return visibleEmployees(user, requestedTeamId, includeSynthetic).stream().map(Employee::getId).toList();
    }

    public Employee requireVisible(CurrentUser user, Long employeeId) {
        Employee e = employees.findWithTeamById(employeeId)
                .orElseThrow(() -> new NoSuchElementException("Collaborateur introuvable"));
        if (user.isManager() && (e.getTeam() == null || !Objects.equals(e.getTeam().getId(), user.teamId()))) {
            throw new AccessDeniedException("Collaborateur hors de votre équipe");
        }
        return e;
    }

    public List<Team> visibleTeams(CurrentUser user) {
        if (user.isManager()) {
            return teams.findById(user.teamId()).map(List::of).orElse(List.of());
        }
        return teams.findAll().stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
    }
}
