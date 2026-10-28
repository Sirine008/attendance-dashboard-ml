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
import { frDate } from '../../core/format';
import { Holiday, SpecialPeriod } from '../../core/models';

@Component({
  selector: 'app-calendar-tab',
  imports: [FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule],
  template: `
    <h3>Jours fériés</h3>
    <form class="form" (ngSubmit)="addHoliday()">
      <mat-form-field subscriptSizing="dynamic"><mat-label>Date</mat-label><input matInput type="date" [(ngModel)]="h.holidayDate" name="hd" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic" class="wide"><mat-label>Libellé</mat-label><input matInput [(ngModel)]="h.label" name="hl" required /></mat-form-field>
      <mat-checkbox [(ngModel)]="h.religious" name="hr">Religieux</mat-checkbox>
      <mat-checkbox [(ngModel)]="h.toVerify" name="hv">À vérifier</mat-checkbox>
      <button mat-flat-button type="submit" [disabled]="!h.holidayDate || !h.label"><mat-icon>add</mat-icon>Ajouter</button>
    </form>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Date</th><th>Libellé</th><th>Type</th><th></th></tr></thead>
        <tbody>
          @for (x of holidays(); track x.id) {
            <tr><td>{{ fd(x.holidayDate) }}</td><td>{{ x.label }}</td>
              <td>{{ x.religious ? 'Religieux' : 'Fixe' }} @if (x.toVerify) { <span class="tag">à vérifier</span> }</td>
              <td><button mat-icon-button (click)="removeHoliday(x)" [attr.aria-label]="'Supprimer ' + x.label"><mat-icon>delete</mat-icon></button></td></tr>
          }
        </tbody>
      </table>
    </div>

    <h3>Périodes d'horaire spécial</h3>
    <p class="muted small">Sans horaire imposé, l'horaire personnel reste la référence (l'analyse des données ne montre pas de décalage des arrivées).
      Un horaire saisi ici remplace l'horaire personnel pendant la période.</p>
    <form class="form" (ngSubmit)="addPeriod()">
      <mat-form-field subscriptSizing="dynamic"><mat-label>Type</mat-label>
        <mat-select [(ngModel)]="p.kind" name="pk"><mat-option value="SEANCE_UNIQUE">Séance unique</mat-option><mat-option value="RAMADAN">Ramadan</mat-option><mat-option value="AUTRE">Autre</mat-option></mat-select></mat-form-field>
      <mat-form-field subscriptSizing="dynamic" class="wide"><mat-label>Libellé</mat-label><input matInput [(ngModel)]="p.label" name="pl" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Début</mat-label><input matInput type="date" [(ngModel)]="p.startDate" name="ps" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Fin</mat-label><input matInput type="date" [(ngModel)]="p.endDate" name="pe" required /></mat-form-field>
      <mat-form-field subscriptSizing="dynamic"><mat-label>Horaire imposé (option.)</mat-label><input matInput type="time" [(ngModel)]="p.refStartTime" name="pt" /></mat-form-field>
      <button mat-flat-button type="submit" [disabled]="!p.label || !p.startDate || !p.endDate"><mat-icon>add</mat-icon>Ajouter</button>
    </form>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Période</th><th>Du</th><th>Au</th><th>Horaire imposé</th><th></th></tr></thead>
        <tbody>
          @for (x of periods(); track x.id) {
            <tr><td>{{ x.label }}</td><td>{{ fd(x.startDate) }}</td><td>{{ fd(x.endDate) }}</td><td>{{ x.refStartTime?.slice(0, 5) ?? 'Horaire personnel' }}</td>
              <td><button mat-icon-button (click)="removePeriod(x)" [attr.aria-label]="'Supprimer ' + x.label"><mat-icon>delete</mat-icon></button></td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
})
export class CalendarTabComponent {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  readonly holidays = signal<Holiday[]>([]);
  readonly periods = signal<SpecialPeriod[]>([]);
  readonly fd = frDate;
  h: Partial<Holiday> = { religious: false, toVerify: false };
  p: Partial<SpecialPeriod> = { kind: 'SEANCE_UNIQUE', refStartTime: null };

  constructor() {
    this.load();
  }

  load(): void {
    this.api.holidays().subscribe((x) => this.holidays.set(x));
    this.api.periods().subscribe((x) => this.periods.set(x));
  }

  private done(msg: string): void {
    this.snack.open(msg, 'OK', { duration: 4000 });
    this.load();
  }

  addHoliday(): void {
    this.api.saveHoliday(this.h).subscribe({ next: () => { this.h = { religious: false, toVerify: false }; this.done('Jour férié ajouté, assiduité recalculée.'); },
      error: () => this.snack.open('Ajout impossible (date déjà présente ?).', 'OK', { duration: 4000 }) });
  }

  removeHoliday(x: Holiday): void {
    this.api.deleteHoliday(x.id).subscribe(() => this.done('Jour férié supprimé.'));
  }

  addPeriod(): void {
    const body = { ...this.p, refStartTime: this.p.refStartTime || null };
    this.api.savePeriod(body).subscribe({ next: () => { this.p = { kind: 'SEANCE_UNIQUE', refStartTime: null }; this.done('Période ajoutée.'); },
      error: () => this.snack.open('Ajout impossible (dates invalides ?).', 'OK', { duration: 4000 }) });
  }

  removePeriod(x: SpecialPeriod): void {
    this.api.deletePeriod(x.id).subscribe(() => this.done('Période supprimée.'));
  }
}
