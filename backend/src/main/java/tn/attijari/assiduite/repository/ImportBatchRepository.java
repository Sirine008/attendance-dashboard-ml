package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.ImportBatch;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findTop20ByOrderByStartedAtDesc();
}
