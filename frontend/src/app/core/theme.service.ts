import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { safeStorage } from './storage';

export type ThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  readonly mode = signal<ThemeMode>(this.initial());

  constructor() {
    this.apply(this.mode());
  }

  toggle(): void {
    const next: ThemeMode = this.mode() === 'dark' ? 'light' : 'dark';
    this.mode.set(next);
    safeStorage.set('assiduite.theme', next);
    this.apply(next);
  }

  /** Lit un token CSS (pour les graphiques ECharts, qui ne comprennent pas var()). */
  token(name: string): string {
    return getComputedStyle(this.doc.documentElement).getPropertyValue(name).trim() || '#888';
  }

  private apply(mode: ThemeMode): void {
    this.doc.documentElement.setAttribute('data-theme', mode);
  }

  private initial(): ThemeMode {
    const saved = safeStorage.get('assiduite.theme');
    if (saved === 'light' || saved === 'dark') {
      return saved;
    }
    try {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }
}
