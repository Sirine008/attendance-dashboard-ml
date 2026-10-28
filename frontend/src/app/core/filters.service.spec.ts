import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FiltersService } from './filters.service';

describe('FiltersService', () => {
  let service: FiltersService;
  let http: HttpTestingController;

  beforeEach(() => {
    try { sessionStorage.clear(); } catch { /* ignore */ }
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FiltersService);
    http = TestBed.inject(HttpTestingController);
  });

  it('se place par défaut sur le dernier mois de données', () => {
    service.init();
    http.expectOne('/api/meta').flush({ dataStart: '2024-01-02', dataEnd: '2025-05-19', teams: [{ id: 1, name: 'A', synthetic: false }, { id: 2, name: 'B', synthetic: false }] });
    expect(service.period()).toEqual({ from: '2025-05-01', to: '2025-05-31' });
    expect(service.scope().teamId).toBeNull();
  });

  it('calcule les bornes jour / année / plage', () => {
    service.anchor.set('2024-02-15');
    service.mode.set('day');
    expect(service.period()).toEqual({ from: '2024-02-15', to: '2024-02-15' });
    service.mode.set('year');
    expect(service.period()).toEqual({ from: '2024-01-01', to: '2024-12-31' });
    service.mode.set('month');
    expect(service.period()).toEqual({ from: '2024-02-01', to: '2024-02-29' });
    service.mode.set('range');
    service.rangeFrom.set('2024-03-01');
    service.rangeTo.set('2024-03-10');
    expect(service.period()).toEqual({ from: '2024-03-01', to: '2024-03-10' });
  });

  it("fixe l'équipe d'un manager (une seule équipe visible)", () => {
    service.init();
    http.expectOne('/api/meta').flush({ dataStart: '2024-01-02', dataEnd: '2025-05-19', teams: [{ id: 7, name: 'A', synthetic: false }] });
    expect(service.teamId()).toBe(7);
  });
});
