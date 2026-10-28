package tn.attijari.assiduite.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Attijari Bank — API assiduité & prédiction des absences").version("1.0.0")
                        .description("""
                                API du tableau de bord d'assiduité (Département Études Informatiques).
                                Authentification : POST /api/auth/login puis en-tête `Authorization: Bearer <jwt>`.
                                Rôles : ADMIN, RH, MANAGER (un manager ne voit que son équipe).
                                Les prédictions sont des estimations statistiques d'aide à l'organisation, jamais une évaluation."""))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
