import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { dateTime } from '../../core/format';
import { ImportBatch } from '../../core/models';

@Component({
  selector: 'app-import-tab',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <h3>Importer un fichier de pointage</h3>
    <p class="muted small">Formats .xls ou .xlsx, une feuille par collaborateur (colonnes _Time, User_ID, User_Name, Terminal_Name).
      L'import est idempotent : un pointage déjà présent n'est jamais dupliqué.</p>
    <div class="form">
      <input #file type="file" accept=".xls,.xlsx" (change)="pick(file.files)" hidden />
      <button mat-stroked-button (click)="file.click()" [disabled]="busy()"><mat-icon>upload_file</mat-icon>Choisir un fichier</button>
      @if (selected(); as s) { <span class="small">{{ s.name }}</span>
        <button mat-flat-button (click)="upload()" [disabled]="busy()"><mat-icon>publish</mat-icon>Importer</button> }
    </div>
    @if (busy()) { <mat-progress-bar mode="indeterminate" aria-label="Import en cours" /> }
    @if (error()) { <div class="notice danger" role="alert"><mat-icon aria-hidden="true">error</mat-icon>{{ error() }}</div> }

    @if (report(); as r) {
      <section class="card report" aria-live="polite">
        <h3>Rapport d'import — {{ r.sourceFile }}</h3>
        <div class="stats">
          <span><strong class="tabular">{{ r.rowsRead }}</strong> lignes lues</span>
          <span><strong class="tabular">{{ r.rowsInserted }}</strong> insérées</span>
          <span><strong class="tabular">{{ r.rowsAlreadyKnown }}</strong> doublons ignorés (déjà présents)</span>
          <span><strong class="tabular">{{ r.rowsRejected }}</strong> rejetées</span>
          <span><strong class="tabular">{{ r.nearDuplicates }}</strong> doublons proches marqués</span>
          <span><strong class="tabular">{{ r.report?.journeesIncompletes ?? 0 }}</strong> journées incomplètes</span>
          <span><strong class="tabular">{{ r.report?.journeesNonOuvreesTravaillees ?? 0 }}</strong> jours non ouvrés travaillés</span>
        </div>
        @if (r.report?.nouveauxCollaborateurs?.length) {
          <p class="small">Nouveaux collaborateurs : {{ r.report!.nouveauxCollaborateurs!.join(', ') }}</p>
        }
        @if (r.report?.anomalies?.length) {
          <details><summary class="small">{{ r.report!.anomalies!.length }} anomalie(s)</summary>
            <ul class="small">@for (a of r.report!.anomalies!; track $index) { <li>{{ a }}</li> }</ul></details>
        }
      </section>
    }

    <h3>Historique des imports</h3>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Date</th><th>Fichier</th><th>Par</th><th>Lues</th><th>Insérées</th><th>Ignorées</th><th>Statut</th></tr></thead>
        <tbody>
          @for (b of history(); track b.id) {
            <tr><td>{{ dt(b.startedAt) }}</td><td>{{ b.sourceFile }}</td><td>{{ b.triggeredBy }}</td><td>{{ b.rowsRead }}</td>
              <td>{{ b.rowsInserted }}</td><td>{{ b.rowsAlreadyKnown }}</td>
              <td><span class="tag" [class.ok]="b.status === 'SUCCESS'">{{ b.status === 'SUCCESS' ? 'Réussi' : b.status }}</span></td></tr>
          } @empty { <tr><td colspan="7" class="muted">Aucun import.</td></tr> }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
  styles: `.report { display: flex; flex-direction: column; gap: 8px; border-left: 4px solid var(--brand-yellow); }
    .stats { display: flex; flex-wrap: wrap; gap: 6px 20px; font-size: .9rem; }`,
})
export class ImportTabComponent {
  private readonly api = inject(ApiService);
  readonly selected = signal<File | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly report = signal<ImportBatch | null>(null);
  readonly history = signal<ImportBatch[]>([]);
  readonly dt = dateTime;

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.api.imports().subscribe((h) => this.history.set(h));
  }

  pick(files: FileList | null): void {
    this.selected.set(files?.[0] ?? null);
    this.error.set(null);
  }

  upload(): void {
    const f = this.selected();
    if (!f) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.importFile(f).subscribe({
      next: (r) => {
        this.report.set(r);
        this.busy.set(false);
        this.selected.set(null);
        this.refresh();
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(e.error?.detail ?? 'Import impossible.');
      },
    });
  }
}
