package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.PublicHoliday;

import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    List<PublicHoliday> findAllByOrderByHolidayDateAsc();
}
