import { AttendanceStatus, DayKind, RiskLevel } from './models';

/** Statuts : toujours icône + libellé + couleur (jamais la couleur seule). */
export interface StatusMeta {
  label: string;
  short: string;
  icon: string;
  color: string;
}

export const ATTENDANCE: Record<AttendanceStatus | 'FERIE' | 'WEEKEND' | 'HORS_SUIVI' | 'INCOMPLET', StatusMeta> = {
  PRESENT: { label: 'Présent', short: 'P', icon: 'check_circle', color: 'var(--st-present)' },
  RETARD: { label: 'Retard', short: 'R', icon: 'schedule', color: 'var(--st-late)' },
  ABSENCE_PONCTUELLE: { label: 'Absence ponctuelle', short: 'A', icon: 'cancel', color: 'var(--st-absent)' },
  ABSENCE_LONGUE: { label: 'Absence longue (congé probable)', short: 'AL', icon: 'event_busy', color: 'var(--st-long)' },
  CONGE: { label: 'Congé déclaré', short: 'C', icon: 'beach_access', color: 'var(--st-leave)' },
  FERIE: { label: 'Jour férié', short: 'F', icon: 'celebration', color: 'var(--st-holiday)' },
  WEEKEND: { label: 'Week-end', short: '', icon: 'weekend', color: 'var(--st-holiday)' },
  HORS_SUIVI: { label: 'Hors période suivie', short: '', icon: 'remove', color: 'var(--st-holiday)' },
  INCOMPLET: { label: 'Pointage incomplet', short: '!', icon: 'warning', color: 'var(--st-incomplete)' },
};

export function dayMeta(kind: DayKind, status: AttendanceStatus | null): StatusMeta {
  if (status) {
    return ATTENDANCE[status];
  }
  if (kind === 'FERIE' || kind === 'WEEKEND' || kind === 'HORS_SUIVI' || kind === 'CONGE') {
    return ATTENDANCE[kind];
  }
  return ATTENDANCE.HORS_SUIVI;
}

export const RISK: Record<RiskLevel, StatusMeta & { bg: string; order: number }> = {
  FAIBLE: { label: 'Faible', short: 'F', icon: 'south', color: 'var(--success)', bg: 'var(--success-soft)', order: 0 },
  MODERE: { label: 'Modéré', short: 'M', icon: 'trending_flat', color: 'var(--warning)', bg: 'var(--warning-soft)', order: 1 },
  ELEVE: { label: 'Élevé', short: 'É', icon: 'north', color: 'var(--danger)', bg: 'var(--danger-soft)', order: 2 },
};

export const ROLE_LABEL = { ADMIN: 'Administrateur', RH: 'Ressources humaines', MANAGER: 'Manager (N+1)' } as const;
