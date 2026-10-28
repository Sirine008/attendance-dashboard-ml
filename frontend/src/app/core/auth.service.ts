import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ApiService } from './api.service';
import { LoginResult, Role, UserInfo } from './models';
import { safeStorage } from './storage';

const KEY = 'assiduite.session';

interface Session {
  token: string;
  expiresAt: string;
  user: UserInfo;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isHrOrAdmin = computed(() => this.hasRole('RH', 'ADMIN'));
  readonly isAdmin = computed(() => this.hasRole('ADMIN'));

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const u = this.session()?.user;
    return !!u && roles.includes(u.role);
  }

  login(username: string, password: string): Observable<LoginResult> {
    return this.api.login(username, password).pipe(
      tap((r) => {
        const s: Session = { token: r.token, expiresAt: r.expiresAt, user: r.user };
        safeStorage.set(KEY, JSON.stringify(s), true);
        this.session.set(s);
      }),
    );
  }

  logout(redirect = true): void {
    safeStorage.remove(KEY, true);
    this.session.set(null);
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  private restore(): Session | null {
    const raw = safeStorage.get(KEY, true);
    if (!raw) {
      return null;
    }
    try {
      const s = JSON.parse(raw) as Session;
      return new Date(s.expiresAt).getTime() > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
