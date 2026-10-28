import { approxPct, hours, initials, minutesToHm, pct } from './format';
import { toCsv } from '../pages/employees/employees.component';
import { EmployeeRow } from './models';

describe('format', () => {
  it('formate les pourcentages et probabilités à la française', () => {
    expect(pct(0.8333)).toBe('83,3 %');
    expect(pct(null)).toBe('—');
    expect(approxPct(0.3412)).toBe('≈ 35 %');
    expect(approxPct(0.072)).toBe('≈ 7 %');
    expect(approxPct(0.001)).toBe('≈ 1 %');
  });

  it('formate heures et minutes', () => {
    expect(hours(7.3)).toBe('7 h 18');
    expect(minutesToHm(485)).toBe('8h05');
  });

  it('calcule des initiales en ignorant la ponctuation', () => {
    expect(initials('Responsable RH (démo)')).toBe('RR');
    expect(initials('Manager équipe A (démo)')).toBe('MA');
    expect(initials('Collaborateur V')).toBe('CV');
  });
});

describe('export CSV', () => {
  const row: EmployeeRow = {
    id: 1, code: 'YYYYYY', displayName: 'Collaborateur Y', teamId: 1, teamName: 'Équipe; A', synthetic: false,
    kpis: { employees: 1, expectedDays: 20, presentDays: 18, presenceRate: 0.9, absencesPonctuelles: 2, absencesLongues: 0,
      leaveDays: 0, lateDays: 3, lateRate: 0.17, avgLateMinutes: 22.5, avgWorkedHours: 7.25, incompleteDays: 1 },
    weeklyPresence: [], riskD1: { status: 'OK', riskLevel: 'ELEVE', probability: 0.2, targetDate: '2025-05-20' }, riskW1: null,
  };

  it('produit un CSV « ; » compatible Excel (BOM, décimales à virgule, échappement)', () => {
    const csv = toCsv([row]);
    expect(csv.startsWith('﻿')).toBe(true);
    const line = csv.split('\r\n')[1];
    expect(line).toContain('"Équipe; A"');
    expect(line).toContain(';90,0;');
    expect(line).toContain(';22,5;');
    expect(line).toContain(';Élevé;');
  });
});
