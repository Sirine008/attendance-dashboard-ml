package tn.attijari.assiduite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Name("import") @DefaultValue Import importConfig,
        @DefaultValue Jwt jwt,
        @DefaultValue Ml ml,
        @DefaultValue Prediction prediction,
        @DefaultValue Demo demo,
        @DefaultValue({"http://localhost:4200"}) List<String> corsOrigins) {

    public record Import(@DefaultValue("../data/raw") String dir,
                         @DefaultValue("true") boolean onStartup,
                         @DefaultValue("") String syntheticFile) {
    }

    public record Jwt(@DefaultValue("dev-only-secret-change-me-please-32-bytes-minimum!!") String secret,
                      @DefaultValue("8h") Duration ttl) {
    }

    public record Ml(@DefaultValue("http://localhost:8000") String url,
                     @DefaultValue("change-me-ml-admin-token") String adminToken,
                     @DefaultValue("3") int maxAttempts,
                     @DefaultValue("5s") Duration retryDelay,
                     @DefaultValue("120s") Duration readTimeout) {
    }

    public record Prediction(@DefaultValue("0 0 6 * * MON-FRI") String cron,
                             @DefaultValue("true") boolean runOnStartup,
                             @DefaultValue("40") int backfillWorkingDays) {
    }

    public record Demo(@DefaultValue("true") boolean seedUsers,
                       @DefaultValue("Demo@2025") String password) {
    }
}
