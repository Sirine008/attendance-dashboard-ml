package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "work_schedule")
@Getter
@Setter
@NoArgsConstructor
public class WorkSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id")
    private Long employeeId;
    private LocalTime startTime;
    private LocalTime endTime;
    private int toleranceMinutes = 10;
    /** AUTO (déduit des données) ou RH (saisi). */
    private String source;
    private String updatedBy;
    private Instant updatedAt = Instant.now();
}
