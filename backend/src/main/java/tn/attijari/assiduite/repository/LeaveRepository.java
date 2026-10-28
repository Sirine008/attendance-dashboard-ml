package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.Leave;

import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByEmployeeId(Long employeeId);

    List<Leave> findAllByOrderByStartDateDesc();
}
