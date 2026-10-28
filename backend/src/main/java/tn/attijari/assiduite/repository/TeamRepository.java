package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.Team;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByName(String name);
}
