package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByCode(String code);

    @EntityGraph(attributePaths = "team")
    List<Employee> findAllByOrderByDisplayNameAsc();

    @EntityGraph(attributePaths = "team")
    List<Employee> findByTeamIdOrderByDisplayNameAsc(Long teamId);

    @EntityGraph(attributePaths = "team")
    Optional<Employee> findWithTeamById(Long id);
}
