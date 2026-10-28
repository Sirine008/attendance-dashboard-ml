package tn.attijari.assiduite.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "daily_attendance")
@Getter
@Setter
@NoArgsConstructor
public class DailyAttendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id")
    private Long employeeId;
    private LocalDate workDate;
    private boolean workingDay;
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
    private LocalTime firstIn;
    private LocalTime lastOut;
    private int lateMinutes;
    private Integer workedMinutes;
    private int punchCount;
    private int boundaryPunchCount;
    private int duplicateCount;
    private boolean incomplete;
    private LocalTime referenceStart;
    private String specialPeriodKind;

    public boolean isPresent() {
        return status == AttendanceStatus.PRESENT || status == AttendanceStatus.RETARD;
    }

    public boolean isLate() {
        return status == AttendanceStatus.RETARD;
    }
}
