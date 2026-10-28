package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.attijari.assiduite.domain.Prediction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /** Dernière prédiction (date de référence la plus récente, puis création la plus récente) par collaborateur. */
    @Query(value = """
            SELECT DISTINCT ON (employee_id) * FROM prediction
            WHERE horizon = :horizon AND employee_id IN (:employeeIds)
            ORDER BY employee_id, reference_date DESC, created_at DESC, id DESC""", nativeQuery = true)
    List<Prediction> findLatest(String horizon, Collection<Long> employeeIds);

    /** Historique : pour chaque jour cible, la prédiction la plus récente faite avant ce jour. */
    @Query(value = """
            SELECT DISTINCT ON (target_date) * FROM prediction
            WHERE horizon = :horizon AND employee_id = :employeeId
            ORDER BY target_date DESC, reference_date DESC, created_at DESC, id DESC
            LIMIT :limit""", nativeQuery = true)
    List<Prediction> findHistory(Long employeeId, String horizon, int limit);

    @Query("select max(p.createdAt) from Prediction p")
    Instant findLastCreatedAt();
}
