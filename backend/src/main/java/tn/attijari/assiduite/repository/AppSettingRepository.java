package tn.attijari.assiduite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.attijari.assiduite.domain.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
