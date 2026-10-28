package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.attijari.assiduite.domain.RawPunch;

import java.time.LocalDateTime;
import java.util.List;

public interface RawPunchRepository extends JpaRepository<RawPunch, Long> {

    List<RawPunch> findByEmployeeIdOrderByPunchTimeAsc(Long employeeId);

    List<RawPunch> findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(Long employeeId, LocalDateTime from, LocalDateTime to);

    @Query("select min(p.punchTime) from RawPunch p")
    LocalDateTime findMinPunchTime();

    @Query("select max(p.punchTime) from RawPunch p")
    LocalDateTime findMaxPunchTime();

    @Query("select distinct p.employeeId from RawPunch p")
    List<Long> findDistinctEmployeeIds();
}
