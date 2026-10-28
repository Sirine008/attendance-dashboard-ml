package tn.attijari.assiduite.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.attijari.assiduite.domain.Role;

/** Utilisateur courant, reconstruit à partir des claims du JWT. */
public record CurrentUser(String username, Role role, Long teamId) {

    public static CurrentUser get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        Number team = jwt.getClaim("teamId");
        return new CurrentUser(jwt.getSubject(), Role.valueOf(jwt.getClaimAsString("role")), team != null ? team.longValue() : null);
    }

    public boolean isManager() {
        return role == Role.MANAGER;
    }
}
