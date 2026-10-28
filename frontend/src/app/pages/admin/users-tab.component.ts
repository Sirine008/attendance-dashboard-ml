import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { FiltersService } from '../../core/filters.service';
import { Role, UserRow } from '../../core/models';
import { ROLE_LABEL } from '../../core/status';

interface Draft {
  id?: number;
  username: string;
  fullName: string;
  role: Role;
  teamId: number | null;
  enabled: boolean;
  password: string;
}

@Component({
  selector: 'app-users-tab',
  imports: [FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule],
  template: `
    <form class="form" (ngSubmit)="save()">
      <mat-form-field subscriptSizing="dynamic"><mat-label>Identifiant</mat-label><input matInput [(ngModel)]="d.username" name="u" [disabled]="!!d.id" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic" class="wide"><mat-label>Nom complet</mat-label><input matInput [(ngModel)]="d.fullName" name="n" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Rôle</mat-label>
        <mat-select [(ngModel)]="d.role" name="r">@for (r of roles; track r) { <mat-option [value]="r">{{ ROLE_LABEL[r] }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field subscriptSizing="dynamic" class="wide"><mat-label>Équipe (managers)</mat-label>
        <mat-select [(ngModel)]="d.teamId" name="t"><mat-option [value]="null">—</mat-option>
          @for (t of f.meta()?.teams ?? []; track t.id) { <mat-option [value]="t.id">{{ t.name }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>{{ d.id ? 'Nouveau mot de passe' : 'Mot de passe' }}</mat-label>
        <input matInput type="password" [(ngModel)]="d.password" name="p" autocomplete="new-password" /></mat-form-field>
      <mat-checkbox [(ngModel)]="d.enabled" name="en">Actif</mat-checkbox>
      <button mat-flat-button type="submit"><mat-icon>{{ d.id ? 'save' : 'person_add' }}</mat-icon>{{ d.id ? 'Enregistrer' : 'Créer' }}</button>
      @if (d.id) { <button mat-button type="button" (click)="reset()">Annuler</button> }
    </form>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Identifiant</th><th>Nom</th><th>Rôle</th><th>Équipe</th><th>Statut</th><th></th></tr></thead>
        <tbody>
          @for (u of users(); track u.id) {
            <tr><td>{{ u.username }}</td><td>{{ u.fullName }}</td><td>{{ ROLE_LABEL[u.role] }}</td><td class="small">{{ u.teamName ?? '—' }}</td>
              <td><span class="tag" [class.ok]="u.enabled">{{ u.enabled ? 'Actif' : 'Désactivé' }}</span></td>
              <td><button mat-icon-button (click)="edit(u)" [attr.aria-label]="'Modifier ' + u.username"><mat-icon>edit</mat-icon></button></td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
})
export class UsersTabComponent {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly f = inject(FiltersService);
  protected readonly ROLE_LABEL = ROLE_LABEL;
  readonly roles: Role[] = ['ADMIN', 'RH', 'MANAGER'];
  readonly users = signal<UserRow[]>([]);
  d: Draft = this.empty();

  constructor() {
    this.load();
  }

  private empty(): Draft {
    return { username: '', fullName: '', role: 'MANAGER', teamId: null, enabled: true, password: '' };
  }

  load(): void {
    this.api.users().subscribe((u) => this.users.set(u));
  }

  reset(): void {
    this.d = this.empty();
  }

  edit(u: UserRow): void {
    this.d = { id: u.id, username: u.username, fullName: u.fullName, role: u.role, teamId: u.teamId, enabled: u.enabled, password: '' };
  }

  save(): void {
    const body = { ...this.d, password: this.d.password || undefined };
    this.api.saveUser(body).subscribe({
      next: () => {
        this.snack.open('Utilisateur enregistré.', 'OK', { duration: 4000 });
        this.reset();
        this.load();
      },
      error: (e: HttpErrorResponse) => this.snack.open(e.error?.detail ?? 'Enregistrement impossible.', 'OK', { duration: 6000 }),
    });
  }
}
