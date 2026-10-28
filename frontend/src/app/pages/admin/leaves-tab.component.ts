import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { frDate } from '../../core/format';
import { LeaveRow, ScheduleRow } from '../../core/models';

@Component({
  selector: 'app-leaves-tab',
  imports: [FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <p class="muted small">Un jour couvert par un congé déclaré n'est jamais compté comme absence (il est exclu des indicateurs et de l'apprentissage).</p>
    <form class="form" (ngSubmit)="add()">
      <mat-form-field subscriptSizing="dynamic" class="wide"><mat-label>Collaborateur</mat-label>
        <mat-select [(ngModel)]="employeeId" name="e" required>
          @for (e of employees(); track e.employeeId) { <mat-option [value]="e.employeeId">{{ e.displayName }} ({{ e.code }})</mat-option> }
        </mat-select></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Du</mat-label><input matInput type="date" [(ngModel)]="start" name="s" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Au</mat-label><input matInput type="date" [(ngModel)]="end" name="en" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Type</mat-label>
        <mat-select [(ngModel)]="type" name="t"><mat-option value="CONGE_ANNUEL">Congé annuel</mat-option><mat-option value="MALADIE">Maladie</mat-option>
          <mat-option value="FORMATION">Formation / mission</mat-option><mat-option value="AUTRE">Autre</mat-option></mat-select></mat-form-field>
      <mat-form-field subscriptSizing="dynamic" class="wide"><mat-label>Commentaire</mat-label><input matInput [(ngModel)]="comment" name="c" maxlength="255" /></mat-form-field>
      <button mat-flat-button type="submit" [disabled]="!employeeId || !start || !end"><mat-icon>add</mat-icon>Déclarer</button>
    </form>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Collaborateur</th><th>Du</th><th>Au</th><th>Type</th><th>Commentaire</th><th>Saisi par</th><th></th></tr></thead>
        <tbody>
          @for (l of leaves(); track l.id) {
            <tr><td>{{ l.employeeName }}</td><td>{{ fd(l.startDate) }}</td><td>{{ fd(l.endDate) }}</td><td>{{ l.leaveType }}</td>
              <td class="small">{{ l.comment }}</td><td class="small muted">{{ l.createdBy }}</td>
              <td><button mat-icon-button (click)="remove(l)" aria-label="Supprimer le congé"><mat-icon>delete</mat-icon></button></td></tr>
          } @empty { <tr><td colspan="7" class="muted">Aucun congé déclaré (le fichier source n'en contient pas).</td></tr> }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
})
export class LeavesTabComponent {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  readonly leaves = signal<LeaveRow[]>([]);
  readonly employees = signal<ScheduleRow[]>([]);
  readonly fd = frDate;
  employeeId: number | null = null;
  start = '';
  end = '';
  type = 'CONGE_ANNUEL';
  comment = '';

  constructor() {
    this.load();
    this.api.schedules().subscribe((e) => this.employees.set(e.filter((x) => !x.synthetic)));
  }

  load(): void {
    this.api.leaves().subscribe((l) => this.leaves.set(l));
  }

  add(): void {
    this.api.createLeave({ employeeId: this.employeeId!, startDate: this.start, endDate: this.end, leaveType: this.type, comment: this.comment }).subscribe({
      next: () => {
        this.snack.open('Congé déclaré, assiduité recalculée.', 'OK', { duration: 4000 });
        this.start = this.end = this.comment = '';
        this.load();
      },
      error: () => this.snack.open('Déclaration impossible (dates invalides ?).', 'OK', { duration: 4000 }),
    });
  }

  remove(l: LeaveRow): void {
    this.api.deleteLeave(l.id).subscribe(() => this.load());
  }
}
