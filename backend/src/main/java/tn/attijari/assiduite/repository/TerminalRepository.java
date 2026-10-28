package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.Terminal;

import java.util.Optional;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {

    Optional<Terminal> findByCode(String code);
}
