package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tn.attijari.assiduite.domain.DailyAttendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, Long> {

    List<DailyAttendance> findByEmployeeIdOrderByWorkDateAsc(Long employeeId);

    List<DailyAttendance> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(Long employeeId, LocalDate from, LocalDate to);

    List<DailyAttendance> findByEmployeeIdInAndWorkDateBetween(Collection<Long> employeeIds, LocalDate from, LocalDate to);

    Optional<DailyAttendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate date);

    @Modifying
    @Query("delete from DailyAttendance d where d.employeeId = :employeeId")
    void deleteByEmployeeId(Long employeeId);

    @Query("select min(d.workDate) from DailyAttendance d")
    LocalDate findMinDate();

    @Query("select max(d.workDate) from DailyAttendance d")
    LocalDate findMaxDate();
}
