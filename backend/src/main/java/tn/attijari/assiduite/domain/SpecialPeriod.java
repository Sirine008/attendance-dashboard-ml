package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "special_period")
@Getter
@Setter
@NoArgsConstructor
public class SpecialPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private PeriodKind kind;
    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    /** Horaire imposé pendant la période ; null = l'horaire personnel reste la référence. */
    private LocalTime refStartTime;
    private LocalTime refEndTime;

    public boolean contains(LocalDate d) {
        return !d.isBefore(startDate) && !d.isAfter(endDate);
    }
}
