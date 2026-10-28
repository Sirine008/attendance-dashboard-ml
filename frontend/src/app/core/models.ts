export type Role = 'ADMIN' | 'RH' | 'MANAGER';
export type RiskLevel = 'FAIBLE' | 'MODERE' | 'ELEVE';
export type Horizon = 'D1' | 'W1';
export type AttendanceStatus = 'PRESENT' | 'RETARD' | 'ABSENCE_PONCTUELLE' | 'ABSENCE_LONGUE' | 'CONGE';

export interface UserInfo {
  username: string;
  fullName: string;
  role: Role;
  teamId: number | null;
  teamName: string | null;
}

export interface LoginResult {
  token: string;
  expiresAt: string;
  user: UserInfo;
}

export interface Team {
  id: number;
  name: string;
  synthetic: boolean;
}

export interface Meta {
  dataStart: string;
  dataEnd: string;
  teams: Team[];
}

export interface Kpis {
  employees: number;
  expectedDays: number;
  presentDays: number;
  presenceRate: number | null;
  absencesPonctuelles: number;
  absencesLongues: number;
  leaveDays: number;
  lateDays: number;
  lateRate: number | null;
  avgLateMinutes: number | null;
  avgWorkedHours: number | null;
  incompleteDays: number;
}

export interface KpiResponse {
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  current: Kpis;
  previous: Kpis;
}

export interface TrendPoint {
  periodStart: string;
  label: string;
  presenceRate: number | null;
  absencesPonctuelles: number;
  absencesLongues: number;
  lateDays: number;
  avgLateMinutes: number | null;
  avgWorkedHours: number | null;
}

export interface HeatCell {
  weekStart: string;
  isoWeek: number;
  weekday: number;
  expectedDays: number;
  absences: number;
  lateDays: number;
  absenceRate: number | null;
}

export interface Bin {
  label: string;
  count: number;
}

export interface ArrivalDistribution {
  arrivalTimes: Bin[];
  deltaToReference: Bin[];
  days: number;
}

export interface RiskBadge {
  status: 'OK' | 'INSUFFICIENT_HISTORY';
  riskLevel: RiskLevel | null;
  probability: number | null;
  targetDate: string;
}

export interface EmployeeRow {
  id: number;
  code: string;
  displayName: string;
  teamId: number | null;
  teamName: string | null;
  synthetic: boolean;
  kpis: Kpis;
  weeklyPresence: (number | null)[];
  riskD1: RiskBadge | null;
  riskW1: RiskBadge | null;
}

export interface ScheduleView {
  startTime: string;
  endTime: string | null;
  toleranceMinutes: number;
  source: 'AUTO' | 'RH';
}

export interface EmployeeProfile {
  id: number;
  code: string;
  displayName: string;
  teamId: number | null;
  teamName: string | null;
  synthetic: boolean;
  hireDate: string | null;
  hireDateFictive: boolean;
  seniorityYears: number | null;
  schedule: ScheduleView | null;
  from: string;
  to: string;
  kpis: Kpis;
  previousKpis: Kpis;
  trackedWorkingDays: number;
}

export type DayKind = 'JOUR_OUVRE' | 'NON_OUVRE_TRAVAILLE' | 'FERIE' | 'WEEKEND' | 'CONGE' | 'HORS_SUIVI';

export interface CalendarDay {
  date: string;
  kind: DayKind;
  status: AttendanceStatus | null;
  incomplete: boolean;
  firstIn: string | null;
  lastOut: string | null;
  lateMinutes: number;
  workedMinutes: number | null;
  label: string | null;
}

export interface PunchView {
  time: string;
  direction: 'IN' | 'OUT';
  terminal: string;
  boundary: boolean;
  duplicate: boolean;
  terminalRaw: string;
}

export interface DayDetail {
  date: string;
  summary: CalendarDay | null;
  punches: PunchView[];
}

export interface ArrivalPoint {
  date: string;
  firstInMinutes: number | null;
  referenceMinutes: number | null;
  late: boolean;
}

export interface Factor {
  feature: string;
  label: string;
  direction: 'UP' | 'DOWN';
  weight: number;
}

export interface PredictionView {
  employeeId: number;
  horizon: Horizon;
  status: 'OK' | 'INSUFFICIENT_HISTORY';
  referenceDate: string;
  targetDate: string;
  targetEnd: string | null;
  probability: number | null;
  riskLevel: RiskLevel | null;
  expectedDays: number | null;
  factors: Factor[];
  modelVersion: string;
  createdAt: string;
}

export interface HistoryItem {
  prediction: PredictionView;
  actualAbsence: boolean | null;
  actualDetail: string;
}

export interface EmployeePredictions {
  latestD1: PredictionView | null;
  latestW1: PredictionView | null;
  historyD1: HistoryItem[];
  historyW1: HistoryItem[];
}

export interface RiskCard {
  employeeId: number;
  code: string;
  displayName: string;
  teamName: string | null;
  synthetic: boolean;
  prediction: PredictionView;
}

export interface PredictionStatus {
  available: boolean;
  mlReachable: boolean;
  modelVersion: string | null;
  lastPredictionAt: string | null;
  lastRunAt: string | null;
  lastRunSuccess: boolean | null;
  message: string;
}

export interface MetricSet {
  n: number;
  positives: number;
  base_rate: number;
  pr_auc?: number;
  roc_auc?: number;
  brier: number;
  precision_at_3: number | null;
  recall_modere?: number | null;
  recall_eleve?: number | null;
  precision_modere?: number | null;
  precision_eleve?: number | null;
  flagged_share_eleve?: number;
  calibration_curve?: { predicted: number[]; observed: number[] };
}

export interface HorizonSummary {
  algorithm: string;
  promoted_model: boolean;
  thresholds: { MODERE: number; ELEVE: number };
  validation: Record<string, MetricSet>;
  test: Record<string, MetricSet>;
  n_train: number;
  n_test: number;
  expected_days_factor: number | null;
}

export interface ModelSummary {
  version: string;
  trained_at: string;
  data: { employees: number; start: string; end: string };
  horizons: Record<string, HorizonSummary>;
}

export interface RegistryRow {
  version: string;
  trainedAt: string;
  algorithm: string;
  active: boolean;
  notes: string | null;
}

export interface ModelInfo {
  status: PredictionStatus;
  active: { version: string; trainedAt: string; algorithm: string; metrics: ModelSummary; thresholds: unknown } | null;
  history: RegistryRow[];
}

export interface BacktestWeek {
  week_start: string;
  samples: number;
  expected_absences: number;
  actual_absences: number;
  flagged_high: number;
  flagged_high_absent: number;
}

export interface Backtest {
  model_version: string;
  horizon: Horizon;
  weeks: BacktestWeek[];
  pr_auc: number | null;
  brier: number | null;
  note: string;
}

export interface ImportBatch {
  id: number;
  sourceFile: string;
  status: string;
  rowsRead: number;
  rowsInserted: number;
  rowsAlreadyKnown: number;
  rowsRejected: number;
  nearDuplicates: number;
  report: {
    feuilles?: Record<string, { collaborateur: string; lignes: number; 'insérées': number; pointagesWeekEnd: number }>;
    nouveauxCollaborateurs?: string[];
    journeesIncompletes?: number;
    doublonsProches?: number;
    journeesNonOuvreesTravaillees?: number;
    anomalies?: string[];
  } | null;
  triggeredBy: string;
  startedAt: string;
  finishedAt: string | null;
}

export interface ScheduleRow {
  employeeId: number;
  code: string;
  displayName: string;
  teamName: string | null;
  synthetic: boolean;
  startTime: string | null;
  endTime: string | null;
  toleranceMinutes: number;
  source: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface Holiday {
  id: number;
  holidayDate: string;
  label: string;
  religious: boolean;
  toVerify: boolean;
}

export interface SpecialPeriod {
  id: number;
  kind: 'SEANCE_UNIQUE' | 'RAMADAN' | 'AUTRE';
  label: string;
  startDate: string;
  endDate: string;
  refStartTime: string | null;
  refEndTime: string | null;
}

export interface LeaveRow {
  id: number;
  employeeId: number;
  employeeName: string;
  startDate: string;
  endDate: string;
  leaveType: string;
  comment: string | null;
  createdBy: string;
  createdAt: string;
}

export interface Terminal {
  id: number;
  code: string;
  label: string;
  buildingBoundary: boolean;
}

export interface AppSetting {
  key: string;
  value: string;
  description: string;
}

export interface UserRow {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  teamId: number | null;
  teamName: string | null;
  enabled: boolean;
}

export interface AuditEntry {
  id: number;
  username: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}
