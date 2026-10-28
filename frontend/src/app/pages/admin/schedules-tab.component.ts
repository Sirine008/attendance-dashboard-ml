import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { ScheduleRow } from '../../core/models';

@Component({
  selector: 'app-schedules-tab',
  imports: [FormsModule, MatButtonModule, MatIconModule],
  template: `
    <p class="muted small">Par défaut, l'horaire de référence est déduit des données (médiane de la 1re entrée sur les 60 premiers jours,
      arrondie au quart d'heure). Une saisie RH le remplace et relance le calcul des retards.</p>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Collaborateur</th><th>Équipe</th><th>Début</th><th>Tolérance (min)</th><th>Source</th><th></th></tr></thead>
        <tbody>
          @for (r of rows(); track r.employeeId) {
            <tr>
              <td>{{ r.displayName }} <span class="faint small">{{ r.code }}</span>@if (r.synthetic) { <span class="synthetic-badge">Synthétique</span> }</td>
              <td class="small muted">{{ r.teamName }}</td>
              @if (editing() === r.employeeId) {
                <td><input type="time" class="inline-input" [(ngModel)]="draftStart" aria-label="Heure de début" /></td>
                <td><input type="number" min="0" max="120" class="inline-input" [(ngModel)]="draftTol" aria-label="Tolérance" /></td>
                <td></td>
                <td><button mat-button (click)="save(r)">Enregistrer</button><button mat-button (click)="editing.set(null)">Annuler</button></td>
              } @else {
                <td class="tabular">{{ r.startTime?.slice(0, 5) ?? '—' }}</td>
                <td class="tabular">{{ r.toleranceMinutes }}</td>
                <td><span class="tag" [class.info]="r.source === 'AUTO'">{{ r.source === 'AUTO' ? 'Déduit' : r.source === 'RH' ? 'Saisi RH' : '—' }}</span></td>
                <td><button mat-icon-button (click)="edit(r)" [attr.aria-label]="'Modifier l\\'horaire de ' + r.displayName"><mat-icon>edit</mat-icon></button></td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
})
export class SchedulesTabComponent {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  readonly rows = signal<ScheduleRow[]>([]);
  readonly editing = signal<number | null>(null);
  draftStart = '08:00';
  draftTol = 10;

  constructor() {
    this.load();
  }

  load(): void {
    this.api.schedules().subscribe((r) => this.rows.set(r));
  }

  edit(r: ScheduleRow): void {
    this.draftStart = r.startTime?.slice(0, 5) ?? '08:00';
    this.draftTol = r.toleranceMinutes;
    this.editing.set(r.employeeId);
  }

  save(r: ScheduleRow): void {
    this.api.updateSchedule(r.employeeId, { startTime: this.draftStart, endTime: r.endTime, toleranceMinutes: Number(this.draftTol) }).subscribe({
      next: () => {
        this.editing.set(null);
        this.snack.open('Horaire enregistré, retards recalculés.', 'OK', { duration: 4000 });
        this.load();
      },
      error: () => this.snack.open('Enregistrement impossible.', 'OK', { duration: 4000 }),
    });
  }
}
