import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AppSetting, ArrivalDistribution, ArrivalPoint, AuditEntry, Backtest, CalendarDay, DayDetail, EmployeePredictions,
  EmployeeProfile, EmployeeRow, HeatCell, Holiday, Horizon, ImportBatch, KpiResponse, LeaveRow, LoginResult, Meta,
  ModelInfo, PredictionStatus, RiskCard, ScheduleRow, SpecialPeriod, Terminal, TrendPoint, UserRow,
} from './models';

export interface Scope {
  from: string;
  to: string;
  teamId: number | null;
  synthetic: boolean;
}

function params(values: Record<string, string | number | boolean | null | undefined>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(values)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, String(v));
    }
  }
  return p;
}

const scopeParams = (s: Scope) => params({ from: s.from, to: s.to, teamId: s.teamId, synthetic: s.synthetic });

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<LoginResult> {
    return this.http.post<LoginResult>('/api/auth/login', { username, password });
  }

  meta(): Observable<Meta> {
    return this.http.get<Meta>('/api/meta');
  }

  kpis(s: Scope): Observable<KpiResponse> {
    return this.http.get<KpiResponse>('/api/dashboard/kpis', { params: scopeParams(s) });
  }

  trend(s: Scope): Observable<TrendPoint[]> {
    return this.http.get<TrendPoint[]>('/api/dashboard/trend', { params: scopeParams(s) });
  }

  heatmap(s: Scope): Observable<HeatCell[]> {
    return this.http.get<HeatCell[]>('/api/dashboard/heatmap', { params: scopeParams(s) });
  }

  arrivals(s: Scope): Observable<ArrivalDistribution> {
    return this.http.get<ArrivalDistribution>('/api/dashboard/arrivals', { params: scopeParams(s) });
  }

  employees(s: Scope): Observable<EmployeeRow[]> {
    return this.http.get<EmployeeRow[]>('/api/employees', { params: scopeParams(s) });
  }

  employee(id: number, from: string, to: string): Observable<EmployeeProfile> {
    return this.http.get<EmployeeProfile>(`/api/employees/${id}`, { params: params({ from, to }) });
  }

  calendar(id: number, month: string): Observable<CalendarDay[]> {
    return this.http.get<CalendarDay[]>(`/api/employees/${id}/calendar`, { params: params({ month }) });
  }

  day(id: number, date: string): Observable<DayDetail> {
    return this.http.get<DayDetail>(`/api/employees/${id}/days/${date}`);
  }

  employeeArrivals(id: number, from: string, to: string): Observable<ArrivalPoint[]> {
    return this.http.get<ArrivalPoint[]>(`/api/employees/${id}/arrivals`, { params: params({ from, to }) });
  }

  employeePredictions(id: number): Observable<EmployeePredictions> {
    return this.http.get<EmployeePredictions>(`/api/employees/${id}/predictions`);
  }

  risks(horizon: Horizon, teamId: number | null, minLevel: string | null, synthetic: boolean): Observable<RiskCard[]> {
    return this.http.get<RiskCard[]>('/api/predictions/latest', { params: params({ horizon, teamId, minLevel, synthetic }) });
  }

  predictionStatus(): Observable<PredictionStatus> {
    return this.http.get<PredictionStatus>('/api/predictions/status');
  }

  modelInfo(): Observable<ModelInfo> {
    return this.http.get<ModelInfo>('/api/model/info');
  }

  backtest(horizon: Horizon, weeks = 8): Observable<Backtest> {
    return this.http.get<Backtest>('/api/model/backtest', { params: params({ horizon, weeks }) });
  }

  train(): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>('/api/model/train', {});
  }

  runPredictions(): Observable<{ success: boolean; message: string; stored: number }> {
    return this.http.post<{ success: boolean; message: string; stored: number }>('/api/admin/predictions/run', {});
  }

  // ------------------------------------------------------------- administration
  importFile(file: File): Observable<ImportBatch> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportBatch>('/api/imports', form);
  }

  imports(): Observable<ImportBatch[]> {
    return this.http.get<ImportBatch[]>('/api/imports');
  }

  schedules(): Observable<ScheduleRow[]> {
    return this.http.get<ScheduleRow[]>('/api/admin/schedules');
  }

  updateSchedule(employeeId: number, body: { startTime: string; endTime: string | null; toleranceMinutes: number }): Observable<void> {
    return this.http.put<void>(`/api/admin/schedules/${employeeId}`, body);
  }

  holidays(): Observable<Holiday[]> {
    return this.http.get<Holiday[]>('/api/admin/holidays');
  }

  saveHoliday(h: Partial<Holiday>): Observable<Holiday> {
    return h.id ? this.http.put<Holiday>(`/api/admin/holidays/${h.id}`, h) : this.http.post<Holiday>('/api/admin/holidays', h);
  }

  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/holidays/${id}`);
  }

  periods(): Observable<SpecialPeriod[]> {
    return this.http.get<SpecialPeriod[]>('/api/admin/special-periods');
  }

  savePeriod(p: Partial<SpecialPeriod>): Observable<SpecialPeriod> {
    return p.id ? this.http.put<SpecialPeriod>(`/api/admin/special-periods/${p.id}`, p) : this.http.post<SpecialPeriod>('/api/admin/special-periods', p);
  }

  deletePeriod(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/special-periods/${id}`);
  }

  leaves(): Observable<LeaveRow[]> {
    return this.http.get<LeaveRow[]>('/api/admin/leaves');
  }

  createLeave(body: { employeeId: number; startDate: string; endDate: string; leaveType: string; comment: string }): Observable<unknown> {
    return this.http.post('/api/admin/leaves', body);
  }

  deleteLeave(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/leaves/${id}`);
  }

  terminals(): Observable<Terminal[]> {
    return this.http.get<Terminal[]>('/api/admin/terminals');
  }

  updateTerminal(t: Terminal): Observable<Terminal> {
    return this.http.put<Terminal>(`/api/admin/terminals/${t.id}`, { label: t.label, buildingBoundary: t.buildingBoundary });
  }

  settings(): Observable<AppSetting[]> {
    return this.http.get<AppSetting[]>('/api/admin/settings');
  }

  updateSetting(key: string, value: string): Observable<AppSetting> {
    return this.http.put<AppSetting>(`/api/admin/settings/${key}`, { value });
  }

  users(): Observable<UserRow[]> {
    return this.http.get<UserRow[]>('/api/admin/users');
  }

  saveUser(u: Partial<UserRow> & { password?: string }): Observable<UserRow> {
    return u.id ? this.http.put<UserRow>(`/api/admin/users/${u.id}`, u) : this.http.post<UserRow>('/api/admin/users', u);
  }

  audit(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>('/api/admin/audit');
  }
}
