import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <main class="login">
      <section class="panel" aria-labelledby="login-title">
        <img src="assets/brand/logo.svg" alt="Attijari bank" class="logo" />
        <h1 id="login-title">Tableau de bord d'assiduité</h1>
        <p class="muted">Département Études Informatiques — accès réservé (RH, managers, administration).</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field>
            <mat-label>Identifiant</mat-label>
            <input matInput formControlName="username" autocomplete="username" autofocus />
            <mat-icon matPrefix aria-hidden="true">person</mat-icon>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Mot de passe</mat-label>
            <input matInput [type]="hide() ? 'password' : 'text'" formControlName="password" autocomplete="current-password" />
            <mat-icon matPrefix aria-hidden="true">lock</mat-icon>
            <button mat-icon-button matSuffix type="button" (click)="hide.set(!hide())"
                    [attr.aria-label]="hide() ? 'Afficher le mot de passe' : 'Masquer le mot de passe'">
              <mat-icon>{{ hide() ? 'visibility' : 'visibility_off' }}</mat-icon>
            </button>
          </mat-form-field>
          @if (error()) {
            <div class="notice danger" role="alert"><mat-icon aria-hidden="true">error</mat-icon>{{ error() }}</div>
          }
          <button mat-flat-button type="submit" class="submit" [disabled]="loading()">
            @if (loading()) { <mat-spinner diameter="18" /> } @else { Se connecter }
          </button>
        </form>
        <p class="faint small foot">Comptes de démonstration : voir le README du projet.</p>
      </section>
      <button mat-icon-button class="theme" (click)="theme.toggle()" [attr.aria-label]="theme.mode() === 'dark' ? 'Mode clair' : 'Mode sombre'">
        <mat-icon>{{ theme.mode() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
      </button>
    </main>
  `,
  styles: `
    .login { min-height: 100vh; display: grid; place-items: center; padding: 16px; background:
      radial-gradient(1200px 400px at 50% -10%, var(--brand-yellow-soft), transparent 70%), var(--bg); position: relative; }
    .panel { width: min(420px, 100%); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
      box-shadow: var(--shadow); padding: 32px 28px; display: flex; flex-direction: column; gap: 6px;
      border-top: 4px solid var(--brand-red); }
    .logo { height: 40px; width: auto; align-self: flex-start; margin-bottom: 12px; }
    h1 { margin: 0; font-size: 1.35rem; font-weight: 650; }
    p { margin: 0 0 12px; }
    form { display: flex; flex-direction: column; gap: 4px; }
    .submit { height: 44px; margin-top: 8px; }
    .submit mat-spinner { margin: 0 auto; }
    .foot { margin: 16px 0 0; text-align: center; }
    .theme { position: absolute; top: 12px; right: 12px; }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly theme = inject(ThemeService);
  readonly hide = signal(true);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.error.set('Veuillez saisir votre identifiant et votre mot de passe.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => void this.router.navigate(['/overview']),
      error: (e: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(e.status === 401 ? 'Identifiant ou mot de passe incorrect.' : 'Service indisponible, réessayez plus tard.');
      },
    });
  }
}
