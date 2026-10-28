package tn.attijari.assiduite.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.config.AppProperties;
import tn.attijari.assiduite.domain.AppUser;
import tn.attijari.assiduite.repository.AppUserRepository;

import java.time.Instant;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
    }

    public record UserInfo(String username, String fullName, String role, Long teamId, String teamName) {
        static UserInfo of(AppUser u) {
            return new UserInfo(u.getUsername(), u.getFullName(), u.getRole().name(),
                    u.getTeam() != null ? u.getTeam().getId() : null, u.getTeam() != null ? u.getTeam().getName() : null);
        }
    }

    public record LoginResult(String token, Instant expiresAt, UserInfo user) {
    }

    @Transactional(readOnly = true)
    public LoginResult login(String username, String password) {
        AppUser u = users.findByUsername(username.trim().toLowerCase())
                .filter(AppUser::isEnabled)
                .filter(x -> encoder.matches(password, x.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        Instant now = Instant.now();
        Instant exp = now.plus(props.jwt().ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("attijari-assiduite")
                .subject(u.getUsername())
                .issuedAt(now)
                .expiresAt(exp)
                .claim("role", u.getRole().name())
                .claim("name", u.getFullName());
        if (u.getTeam() != null) {
            claims.claim("teamId", u.getTeam().getId());
        }
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return new LoginResult(token, exp, UserInfo.of(u));
    }

    @Transactional(readOnly = true)
    public UserInfo me(String username) {
        return users.findByUsername(username).map(UserInfo::of).orElseThrow(() -> new BadCredentialsException("Utilisateur inconnu"));
    }
}
