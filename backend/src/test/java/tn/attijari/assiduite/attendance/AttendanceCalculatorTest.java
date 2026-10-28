package tn.attijari.assiduite.attendance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tn.attijari.assiduite.attendance.AttendanceCalculator.PunchInput;
import tn.attijari.assiduite.domain.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static tn.attijari.assiduite.domain.Direction.IN;
import static tn.attijari.assiduite.domain.Direction.OUT;

class AttendanceCalculatorTest {

    // Semaine de référence : lundi 2024-05-13 → vendredi 2024-05-17
    static final LocalDate MON = LocalDate.of(2024, 5, 13);
    static final LocalTime REF = LocalTime.of(8, 0);
    private long ids = 1;

    PunchInput p(LocalDate d, String hhmmss, Direction dir, String door) {
        boolean boundary = door.equals("TB") || door.equals("TA");
        return new PunchInput(ids++, LocalDateTime.of(d, LocalTime.parse(hhmmss)), dir, door, boundary);
    }

    List<PunchInput> normalDay(LocalDate d) {
        return List.of(p(d, "07:55:00", IN, "TB"), p(d, "12:30:00", OUT, "TB"), p(d, "13:30:00", IN, "TB"), p(d, "17:00:00", OUT, "TB"));
    }

    AttendanceCalculator.Result run(LocalDate from, LocalDate to, List<PunchInput> punches) {
        return run(from, to, punches, Set.of(), List.of(), List.of());
    }

    AttendanceCalculator.Result run(LocalDate from, LocalDate to, List<PunchInput> punches, Set<LocalDate> holidays,
                                    List<SpecialPeriod> periods, List<Leave> leaves) {
        return AttendanceCalculator.compute(new AttendanceCalculator.Input(1L, from, to, punches, holidays, periods, leaves,
                REF, 10, AttendanceCalculator.Rules.defaults()));
    }

    DailyAttendance day(AttendanceCalculator.Result r, LocalDate d) {
        return r.days().stream().filter(a -> a.getWorkDate().equals(d)).findFirst().orElse(null);
    }

    @Nested
    class PresenceAndHours {
        @Test
        void normalDayIsPresentWithWorkedHours() {
            DailyAttendance a = day(run(MON, MON, normalDay(MON)), MON);
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(a.getFirstIn()).isEqualTo(LocalTime.of(7, 55));
            assertThat(a.getLastOut()).isEqualTo(LocalTime.of(17, 0));
            assertThat(a.getWorkedMinutes()).isEqualTo(275 + 210);
            assertThat(a.isIncomplete()).isFalse();
            assertThat(a.getPunchCount()).isEqualTo(4);
        }

        @Test
        void oddPunchCountIsIncompleteAndHoursAreNotInvented() {
            List<PunchInput> punches = List.of(p(MON, "08:00:00", IN, "TB"), p(MON, "12:00:00", OUT, "TB"), p(MON, "13:00:00", IN, "TB"));
            DailyAttendance a = day(run(MON, MON, punches), MON);
            assertThat(a.isIncomplete()).isTrue();
            assertThat(a.getWorkedMinutes()).isEqualTo(240); // seule la paire valide 08:00→12:00
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        }

        @Test
        void dayWithOnlyAnExitIsPresentButIncompleteWithoutHours() {
            DailyAttendance a = day(run(MON, MON, List.of(p(MON, "17:00:00", OUT, "TB"))), MON);
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(a.isIncomplete()).isTrue();
            assertThat(a.getWorkedMinutes()).isNull();
            assertThat(a.getFirstIn()).isNull();
        }

        @Test
        void internalDoorIsNotABuildingExit() {
            List<PunchInput> punches = List.of(
                    p(MON, "08:00:00", IN, "TB"),
                    p(MON, "10:00:00", OUT, "SS1 B"),   // passage en zone interne
                    p(MON, "10:20:00", IN, "SS1 B"),
                    p(MON, "17:00:00", OUT, "TB"));
            DailyAttendance a = day(run(MON, MON, punches), MON);
            assertThat(a.getWorkedMinutes()).isEqualTo(9 * 60);
            assertThat(a.isIncomplete()).isFalse();
            assertThat(a.getPunchCount()).isEqualTo(4);
            assertThat(a.getBoundaryPunchCount()).isEqualTo(2);
        }

        @Test
        void nearDuplicateIsIgnoredAndFlagged() {
            List<PunchInput> punches = new ArrayList<>(normalDay(MON));
            PunchInput dup = p(MON, "07:55:04", IN, "TB");
            punches.add(dup);
            AttendanceCalculator.Result r = run(MON, MON, punches);
            DailyAttendance a = day(r, MON);
            assertThat(r.duplicatePunchIds()).containsExactly(dup.id());
            assertThat(a.getDuplicateCount()).isEqualTo(1);
            assertThat(a.isIncomplete()).isFalse();
            assertThat(a.getWorkedMinutes()).isEqualTo(485);
        }

        @Test
        void samePunchOnDifferentDoorIsNotADuplicate() {
            List<PunchInput> punches = List.of(p(MON, "08:00:00", IN, "TB"), p(MON, "08:00:30", IN, "SS1 B"), p(MON, "17:00:00", OUT, "TB"));
            assertThat(run(MON, MON, punches).duplicatePunchIds()).isEmpty();
        }
    }

    @Nested
    class Lateness {
        @Test
        void arrivalWithinToleranceIsNotLate() {
            DailyAttendance a = day(run(MON, MON, List.of(p(MON, "08:10:00", IN, "TB"), p(MON, "17:00:00", OUT, "TB"))), MON);
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(a.getLateMinutes()).isZero();
        }

        @Test
        void arrivalAfterToleranceIsLateWithMinutesFromReference() {
            DailyAttendance a = day(run(MON, MON, List.of(p(MON, "08:25:30", IN, "TB"), p(MON, "17:00:00", OUT, "TB"))), MON);
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.RETARD);
            assertThat(a.getLateMinutes()).isEqualTo(25);
        }

        @Test
        void specialPeriodWithOwnScheduleOverridesPersonalReference() {
            SpecialPeriod summer = new SpecialPeriod();
            summer.setKind(PeriodKind.SEANCE_UNIQUE);
            summer.setStartDate(MON);
            summer.setEndDate(MON.plusDays(30));
            summer.setRefStartTime(LocalTime.of(7, 30));
            List<PunchInput> punches = List.of(p(MON, "07:55:00", IN, "TB"), p(MON, "13:30:00", OUT, "TB"));
            DailyAttendance a = day(run(MON, MON, punches, Set.of(), List.of(summer), List.of()), MON);
            assertThat(a.getReferenceStart()).isEqualTo(LocalTime.of(7, 30));
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.RETARD);
            assertThat(a.getLateMinutes()).isEqualTo(25);
            assertThat(a.getSpecialPeriodKind()).isEqualTo("SEANCE_UNIQUE");
        }

        @Test
        void specialPeriodWithoutScheduleKeepsPersonalReference() {
            SpecialPeriod ramadan = new SpecialPeriod();
            ramadan.setKind(PeriodKind.RAMADAN);
            ramadan.setStartDate(MON);
            ramadan.setEndDate(MON);
            DailyAttendance a = day(run(MON, MON, normalDay(MON), Set.of(), List.of(ramadan), List.of()), MON);
            assertThat(a.getReferenceStart()).isEqualTo(REF);
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(a.getSpecialPeriodKind()).isEqualTo("RAMADAN");
        }
    }

    @Nested
    class CalendarAndAbsences {
        @Test
        void holidayWithoutPunchProducesNoRowAndIsNeverAnAbsence() {
            LocalDate holiday = MON.plusDays(1);
            List<PunchInput> punches = new ArrayList<>(normalDay(MON));
            punches.addAll(normalDay(MON.plusDays(2)));
            AttendanceCalculator.Result r = run(MON, MON.plusDays(2), punches, Set.of(holiday), List.of(), List.of());
            assertThat(day(r, holiday)).isNull();
            assertThat(r.days()).hasSize(2);
        }

        @Test
        void workedHolidayOrSaturdayIsNonWorkingAndNeverLate() {
            LocalDate saturday = MON.plusDays(5);
            DailyAttendance a = day(run(saturday, saturday, List.of(p(saturday, "10:00:00", IN, "TB"), p(saturday, "12:00:00", OUT, "TB"))), saturday);
            assertThat(a.isWorkingDay()).isFalse();
            assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(a.getLateMinutes()).isZero();
        }

        @Test
        void oneOrTwoMissingDaysArePonctuelle() {
            List<PunchInput> punches = new ArrayList<>(normalDay(MON));
            punches.addAll(normalDay(MON.plusDays(3))); // mardi & mercredi sans pointage
            AttendanceCalculator.Result r = run(MON, MON.plusDays(3), punches);
            assertThat(day(r, MON.plusDays(1)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
            assertThat(day(r, MON.plusDays(2)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
        }

        @Test
        void threeWorkingDaysAcrossAWeekendAreLongue() {
            // jeudi, vendredi, lundi suivant sans pointage (week-end entre deux) = 3 jours ouvrés consécutifs
            LocalDate wed = MON.plusDays(2);
            List<PunchInput> punches = new ArrayList<>(normalDay(wed));
            punches.addAll(normalDay(MON.plusDays(8)));
            AttendanceCalculator.Result r = run(wed, MON.plusDays(8), punches);
            assertThat(day(r, MON.plusDays(3)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_LONGUE);
            assertThat(day(r, MON.plusDays(4)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_LONGUE);
            assertThat(day(r, MON.plusDays(7)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_LONGUE);
            assertThat(day(r, MON.plusDays(5))).isNull(); // samedi
        }

        @Test
        void holidayInsideARunDoesNotBreakIt() {
            List<PunchInput> punches = new ArrayList<>(normalDay(MON));
            punches.addAll(normalDay(MON.plusDays(4)));
            AttendanceCalculator.Result r = run(MON, MON.plusDays(4), punches, Set.of(MON.plusDays(2)), List.of(), List.of());
            // mardi + jeudi absents, mercredi férié → série de 2 jours ouvrés seulement
            assertThat(day(r, MON.plusDays(1)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
            assertThat(day(r, MON.plusDays(3)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
        }

        @Test
        void declaredLeaveIsNeverAnAbsenceAndBreaksRuns() {
            Leave leave = new Leave();
            leave.setStartDate(MON.plusDays(2));
            leave.setEndDate(MON.plusDays(2));
            List<PunchInput> punches = new ArrayList<>(normalDay(MON));
            AttendanceCalculator.Result r = run(MON, MON.plusDays(4), punches, Set.of(), List.of(), List.of(leave));
            assertThat(day(r, MON.plusDays(1)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
            assertThat(day(r, MON.plusDays(2)).getStatus()).isEqualTo(AttendanceStatus.CONGE);
            assertThat(day(r, MON.plusDays(3)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
            assertThat(day(r, MON.plusDays(4)).getStatus()).isEqualTo(AttendanceStatus.ABSENCE_PONCTUELLE);
        }
    }

    @Test
    void referenceStartIsMedianOfFirstEntriesRoundedToQuarterHour() {
        List<PunchInput> punches = new ArrayList<>();
        String[] arrivals = {"09:35:00", "09:40:00", "09:41:00", "09:50:00", "09:20:00"};
        for (int i = 0; i < arrivals.length; i++) {
            LocalDate d = MON.plusDays(i);
            punches.add(p(d, arrivals[i], IN, "TB"));
            punches.add(p(d, "08:00:00", IN, "SS1 B")); // porte interne ignorée
        }
        assertThat(AttendanceCalculator.deduceReferenceStart(punches, MON, 60, 15)).contains(LocalTime.of(9, 45));
    }
}
