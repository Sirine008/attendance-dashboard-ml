package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "team")
    Optional<AppUser> findByUsername(String username);

    @EntityGraph(attributePaths = "team")
    List<AppUser> findAllByOrderByUsernameAsc();
}
