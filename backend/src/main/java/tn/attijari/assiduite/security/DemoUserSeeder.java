package tn.attijari.assiduite.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.config.AppProperties;
import tn.attijari.assiduite.domain.AppUser;
import tn.attijari.assiduite.domain.Role;
import tn.attijari.assiduite.repository.AppUserRepository;
import tn.attijari.assiduite.repository.TeamRepository;

/**
 * SEED FICTIF : comptes de démonstration (1 admin, 1 RH, 2 managers N+1), créés si la table est vide.
 * Mot de passe commun configurable ({@code app.demo.password}) — à désactiver en production.
 */
@Component
@Order(1)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AppUserRepository users;
    private final TeamRepository teams;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DemoUserSeeder(AppUserRepository users, TeamRepository teams, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.teams = teams;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demo().seedUsers() || users.count() > 0) {
            return;
        }
        String hash = encoder.encode(props.demo().password());
        create("admin", "Administrateur (démo)", Role.ADMIN, null, hash);
        create("rh", "Responsable RH (démo)", Role.RH, null, hash);
        create("manager.a", "Manager équipe A (démo)", Role.MANAGER, "Équipe Études A (fictive)", hash);
        create("manager.b", "Manager équipe B (démo)", Role.MANAGER, "Équipe Études B (fictive)", hash);
        log.info("Comptes de démonstration créés (admin, rh, manager.a, manager.b)");
    }

    private void create(String username, String name, Role role, String team, String hash) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(name);
        u.setRole(role);
        u.setPasswordHash(hash);
        if (team != null) {
            u.setTeam(teams.findByName(team).orElseThrow());
        }
        users.save(u);
    }
}
