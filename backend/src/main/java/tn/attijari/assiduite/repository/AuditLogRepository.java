package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
