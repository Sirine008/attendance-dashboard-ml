import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { ApiService, Scope } from './api.service';
import { isoDate, parseIso } from './format';
import { Meta } from './models';
import { safeStorage } from './storage';

export type PeriodMode = 'day' | 'month' | 'year' | 'range';

interface Saved {
  mode: PeriodMode;
  anchor: string;
  rangeFrom: string;
  rangeTo: string;
  teamId: number | null;
  synthetic: boolean;
}

const KEY = 'assiduite.filters';

/** Filtres globaux partagés par les pages (période, équipe, données synthétiques). */
@Injectable({ providedIn: 'root' })
export class FiltersService {
  private readonly api = inject(ApiService);

  readonly meta = signal<Meta | null>(null);
  readonly mode = signal<PeriodMode>('month');
  readonly anchor = signal<string>(isoDate(new Date()));
  readonly rangeFrom = signal<string>(isoDate(new Date()));
  readonly rangeTo = signal<string>(isoDate(new Date()));
  readonly teamId = signal<number | null>(null);
  readonly synthetic = signal(false);
  private initialized = false;

  readonly period = computed(() => {
    const a = parseIso(this.anchor());
    switch (this.mode()) {
      case 'day':
        return { from: this.anchor(), to: this.anchor() };
      case 'month':
        return { from: isoDate(new Date(a.getFullYear(), a.getMonth(), 1)), to: isoDate(new Date(a.getFullYear(), a.getMonth() + 1, 0)) };
      case 'year':
        return { from: `${a.getFullYear()}-01-01`, to: `${a.getFullYear()}-12-31` };
      default:
        return { from: this.rangeFrom(), to: this.rangeTo() };
    }
  });

  readonly scope = computed<Scope>(() => ({ ...this.period(), teamId: this.teamId(), synthetic: this.synthetic() }));

  readonly periodLabel = computed(() => {
    const p = this.period();
    const a = parseIso(this.anchor());
    switch (this.mode()) {
      case 'day':
        return a.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
      case 'month':
        return a.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
      case 'year':
        return `Année ${a.getFullYear()}`;
      default:
        return `Du ${parseIso(p.from).toLocaleDateString('fr-FR')} au ${parseIso(p.to).toLocaleDateString('fr-FR')}`;
    }
  });

  constructor() {
    effect(() => {
      if (!this.initialized) {
        return;
      }
      const s: Saved = { mode: this.mode(), anchor: this.anchor(), rangeFrom: this.rangeFrom(), rangeTo: this.rangeTo(),
        teamId: this.teamId(), synthetic: this.synthetic() };
      safeStorage.set(KEY, JSON.stringify(s), true);
    });
  }

  /** Charge les bornes des données et initialise les filtres (une seule fois par session). */
  init(): void {
    if (this.meta()) {
      return;
    }
    this.api.meta().subscribe((m) => {
      this.meta.set(m);
      const saved = this.restore();
      if (saved) {
        this.mode.set(saved.mode);
        this.anchor.set(saved.anchor);
        this.rangeFrom.set(saved.rangeFrom);
        this.rangeTo.set(saved.rangeTo);
        this.teamId.set(m.teams.some((t) => t.id === saved.teamId) ? saved.teamId : null);
        this.synthetic.set(saved.synthetic);
      } else {
        // Par défaut : le dernier mois de données disponibles (le fichier s'arrête en mai 2025).
        this.anchor.set(m.dataEnd);
        const end = parseIso(m.dataEnd);
        this.rangeFrom.set(isoDate(new Date(end.getFullYear(), end.getMonth() - 2, end.getDate())));
        this.rangeTo.set(m.dataEnd);
      }
      if (m.teams.length === 1) {
        this.teamId.set(m.teams[0].id);
      }
      this.initialized = true;
    });
  }

  reset(): void {
    this.meta.set(null);
    this.initialized = false;
    safeStorage.remove(KEY, true);
  }

  private restore(): Saved | null {
    try {
      const raw = safeStorage.get(KEY, true);
      return raw ? (JSON.parse(raw) as Saved) : null;
    } catch {
      return null;
    }
  }
}
