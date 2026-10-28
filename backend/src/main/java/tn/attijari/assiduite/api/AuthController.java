package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import tn.attijari.assiduite.security.AuthService;
import tn.attijari.assiduite.security.CurrentUser;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion : renvoie un JWT (Bearer) valable 8 h")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    @GetMapping("/me")
    @Operation(summary = "Utilisateur courant")
    public AuthService.UserInfo me() {
        return auth.me(CurrentUser.get().username());
    }
}
