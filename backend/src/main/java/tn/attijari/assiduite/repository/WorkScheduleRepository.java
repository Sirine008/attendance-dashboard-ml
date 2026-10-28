package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.WorkSchedule;

import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    Optional<WorkSchedule> findByEmployeeId(Long employeeId);
}
