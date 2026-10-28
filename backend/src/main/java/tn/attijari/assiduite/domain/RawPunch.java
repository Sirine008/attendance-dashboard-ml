package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "raw_punch")
@Getter
@Setter
@NoArgsConstructor
public class RawPunch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id")
    private Long employeeId;
    private LocalDateTime punchTime;
    @Enumerated(EnumType.STRING)
    private Direction direction;
    @Column(name = "terminal_id")
    private Long terminalId;
    private String terminalRaw;
    @Column(name = "is_duplicate")
    private boolean duplicate;
    private String sourceFile;
    private Long importBatchId;
}
