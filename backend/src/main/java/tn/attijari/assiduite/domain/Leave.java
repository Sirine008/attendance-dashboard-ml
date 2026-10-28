package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leave")
@Getter
@Setter
@NoArgsConstructor
public class Leave {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id")
    private Long employeeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String leaveType = "CONGE_ANNUEL";
    private String comment;
    private String createdBy;
    private Instant createdAt = Instant.now();

    public boolean covers(LocalDate d) {
        return !d.isBefore(startDate) && !d.isAfter(endDate);
    }
}
