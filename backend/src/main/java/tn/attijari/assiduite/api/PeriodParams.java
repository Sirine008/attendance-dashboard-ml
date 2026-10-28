package tn.attijari.assiduite.api;

import org.springframework.stereotype.Component;
import tn.attijari.assiduite.repository.DailyAttendanceRepository;

import java.time.LocalDate;

/** Résolution des bornes de période : par défaut, les 30 derniers jours de données disponibles. */
@Component
public class PeriodParams {

    private final DailyAttendanceRepository daily;

    public PeriodParams(DailyAttendanceRepository daily) {
        this.daily = daily;
    }

    public record Period(LocalDate from, LocalDate to) {
    }

    public LocalDate dataEnd() {
        LocalDate max = daily.findMaxDate();
        return max != null ? max : LocalDate.now();
    }

    public LocalDate dataStart() {
        LocalDate min = daily.findMinDate();
        return min != null ? min : LocalDate.now();
    }

    public Period resolve(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : dataEnd();
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) {
            throw new IllegalArgumentException("La date de début doit précéder la date de fin");
        }
        if (f.plusYears(3).isBefore(t)) {
            throw new IllegalArgumentException("Période limitée à 3 ans");
        }
        return new Period(f, t);
    }
}
