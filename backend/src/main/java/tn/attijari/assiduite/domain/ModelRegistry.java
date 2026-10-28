package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Écrit par ml-service à chaque entraînement ; lu par le backend. */
@Entity
@Table(name = "model_registry")
@Getter
@Setter
@NoArgsConstructor
public class ModelRegistry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String version;
    private Instant trainedAt;
    private String algorithm;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metrics;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> thresholds;
    @Column(name = "is_active")
    private boolean active;
    private String notes;
}
