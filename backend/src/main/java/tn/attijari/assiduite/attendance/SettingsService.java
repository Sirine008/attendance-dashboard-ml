package tn.attijari.assiduite.attendance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.domain.AppSetting;
import tn.attijari.assiduite.repository.AppSettingRepository;

import java.util.List;
import java.util.NoSuchElementException;

/** Paramètres métier modifiables (table {@code app_settings}). */
@Service
public class SettingsService {

    public static final String LATE_TOLERANCE = "late_tolerance_minutes";
    public static final String DUPLICATE_WINDOW = "duplicate_window_seconds";
    public static final String LONG_ABSENCE_MIN_DAYS = "long_absence_min_days";
    public static final String SCHEDULE_LEARNING_DAYS = "schedule_learning_days";
    public static final String SCHEDULE_ROUNDING = "schedule_rounding_minutes";
    public static final String PREDICTION_MIN_HISTORY = "prediction_min_history_days";

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    public int getInt(String key, int fallback) {
        return repository.findById(key).map(s -> Integer.parseInt(s.getValue().trim())).orElse(fallback);
    }

    public List<AppSetting> findAll() {
        return repository.findAll();
    }

    @Transactional
    public AppSetting update(String key, String value) {
        AppSetting s = repository.findById(key).orElseThrow(() -> new NoSuchElementException("Paramètre inconnu : " + key));
        Integer.parseInt(value.trim()); // tous les paramètres actuels sont entiers
        s.setValue(value.trim());
        return repository.save(s);
    }

    public AttendanceCalculator.Rules rules() {
        return new AttendanceCalculator.Rules(getInt(DUPLICATE_WINDOW, 120), getInt(LONG_ABSENCE_MIN_DAYS, 3));
    }
}
