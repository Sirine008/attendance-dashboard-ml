package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.attijari.assiduite.audit.AuditService;
import tn.attijari.assiduite.domain.AppUser;
import tn.attijari.assiduite.domain.AuditLog;
import tn.attijari.assiduite.domain.Role;
import tn.attijari.assiduite.repository.AppUserRepository;
import tn.attijari.assiduite.repository.AuditLogRepository;
import tn.attijari.assiduite.repository.TeamRepository;
import tn.attijari.assiduite.security.CurrentUser;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Utilisateurs & audit (Admin)")
public class UserAdminController {

    private final AppUserRepository users;
    private final TeamRepository teams;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AuditLogRepository auditLogs;

    public UserAdminController(AppUserRepository users, TeamRepository teams, PasswordEncoder encoder, AuditService audit,
                               AuditLogRepository auditLogs) {
        this.users = users;
        this.teams = teams;
        this.encoder = encoder;
        this.audit = audit;
        this.auditLogs = auditLogs;
    }

    public record UserRow(Long id, String username, String fullName, String role, Long teamId, String teamName, boolean enabled) {
        static UserRow of(AppUser u) {
            return new UserRow(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name(),
                    u.getTeam() != null ? u.getTeam().getId() : null, u.getTeam() != null ? u.getTeam().getName() : null, u.isEnabled());
        }
    }

    public record UserBody(@NotBlank @Size(max = 60) String username, @NotBlank String fullName, @NotNull Role role, Long teamId,
                           boolean enabled, @Size(min = 8, message = "8 caractères minimum") String password) {
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserRow> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserRow::of).toList();
    }

    @PostMapping("/users")
    @Transactional
    @Operation(summary = "Créer un utilisateur (un MANAGER doit être rattaché à une équipe)")
    public UserRow create(@Valid @RequestBody UserBody body) {
        if (body.password() == null) {
            throw new IllegalArgumentException("Mot de passe requis");
        }
        String username = body.username().trim().toLowerCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalStateException("Identifiant déjà utilisé");
        }
        AppUser u = new AppUser();
        u.setUsername(username);
        apply(u, body);
        u = users.save(u);
        audit.log(CurrentUser.get().username(), "CREATE_USER", "app_user", u.getId(), username + " " + body.role());
        return UserRow.of(u);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public UserRow update(@PathVariable Long id, @Valid @RequestBody UserBody body) {
        AppUser u = users.findById(id).orElseThrow();
        apply(u, body);
        audit.log(CurrentUser.get().username(), "UPDATE_USER", "app_user", id, u.getUsername() + " " + body.role());
        return UserRow.of(users.save(u));
    }

    private void apply(AppUser u, UserBody body) {
        if (body.role() == Role.MANAGER && body.teamId() == null) {
            throw new IllegalArgumentException("Un manager doit être rattaché à une équipe");
        }
        u.setFullName(body.fullName());
        u.setRole(body.role());
        u.setTeam(body.teamId() != null ? teams.findById(body.teamId()).orElseThrow() : null);
        u.setEnabled(body.enabled());
        if (body.password() != null && !body.password().isBlank()) {
            u.setPasswordHash(encoder.encode(body.password()));
        }
    }

    @GetMapping("/audit")
    @Operation(summary = "Journal d'audit (200 dernières entrées)")
    public List<AuditLog> auditLog() {
        return auditLogs.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }
}
