package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Prédiction historisée : jamais mise à jour, une nouvelle ligne par exécution. */
@Entity
@Table(name = "prediction")
@Getter
@Setter
@NoArgsConstructor
public class Prediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id")
    private Long employeeId;
    private LocalDate referenceDate;
    private LocalDate targetDate;
    private LocalDate targetEnd;
    @Enumerated(EnumType.STRING)
    private Horizon horizon;
    @Enumerated(EnumType.STRING)
    private PredictionStatus status;
    private BigDecimal probability;
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    private BigDecimal expectedDays;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> topFactors;
    private String modelVersion;
    private Instant createdAt = Instant.now();
}
