package tn.attijari.assiduite.analytics;

import tn.attijari.assiduite.domain.AttendanceStatus;
import tn.attijari.assiduite.domain.DailyAttendance;

import java.util.Collection;

/**
 * Indicateurs agrégés sur un ensemble de journées.
 * <ul>
 *   <li>taux de présence = jours présents / jours ouvrés attendus (jours ouvrés hors congés déclarés) ;</li>
 *   <li>retard moyen = moyenne des minutes de retard sur les seuls jours en retard ;</li>
 *   <li>heures moyennes = moyenne sur les journées présentes <b>complètes</b> (les incomplètes ne sont pas extrapolées).</li>
 * </ul>
 */
public record Kpis(int employees,
                   int expectedDays,
                   int presentDays,
                   Double presenceRate,
                   int absencesPonctuelles,
                   int absencesLongues,
                   int leaveDays,
                   int lateDays,
                   Double lateRate,
                   Double avgLateMinutes,
                   Double avgWorkedHours,
                   int incompleteDays) {

    public static Kpis of(Collection<DailyAttendance> rows, int employees) {
        int expected = 0, present = 0, absP = 0, absL = 0, leave = 0, late = 0, lateMinutes = 0, incomplete = 0, completeDays = 0;
        long workedMinutes = 0;
        for (DailyAttendance d : rows) {
            if (!d.isWorkingDay()) {
                continue;
            }
            AttendanceStatus s = d.getStatus();
            if (s == AttendanceStatus.CONGE) {
                leave++;
                continue;
            }
            expected++;
            switch (s) {
                case ABSENCE_PONCTUELLE -> absP++;
                case ABSENCE_LONGUE -> absL++;
                default -> {
                    present++;
                    if (s == AttendanceStatus.RETARD) {
                        late++;
                        lateMinutes += d.getLateMinutes();
                    }
                    if (d.isIncomplete()) {
                        incomplete++;
                    } else if (d.getWorkedMinutes() != null) {
                        completeDays++;
                        workedMinutes += d.getWorkedMinutes();
                    }
                }
            }
        }
        return new Kpis(employees, expected, present,
                ratio(present, expected), absP, absL, leave, late, ratio(late, present),
                late > 0 ? round1((double) lateMinutes / late) : null,
                completeDays > 0 ? round2(workedMinutes / 60.0 / completeDays) : null,
                incomplete);
    }

    static Double ratio(int num, int den) {
        return den == 0 ? null : Math.round(10000.0 * num / den) / 10000.0;
    }

    static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
