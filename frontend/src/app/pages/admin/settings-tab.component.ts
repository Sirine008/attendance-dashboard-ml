import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { AppSetting, Terminal } from '../../core/models';

@Component({
  selector: 'app-settings-tab',
  imports: [FormsModule, MatButtonModule, MatIconModule, MatSlideToggleModule],
  template: `
    <h3>Terminaux (portes)</h3>
    <p class="muted small">Seuls les terminaux « frontière du bâtiment » comptent pour la présence et les heures. Les portes de sous-sol (SS) sont des zones internes.</p>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Code</th><th>Libellé</th><th>Frontière du bâtiment</th></tr></thead>
        <tbody>
          @for (t of terminals(); track t.id) {
            <tr><td><strong>{{ t.code }}</strong></td><td>{{ t.label }}</td>
              <td><mat-slide-toggle [checked]="t.buildingBoundary" (change)="toggle(t, $event.checked)"
                                    [attr.aria-label]="'Frontière : ' + t.code">{{ t.buildingBoundary ? 'Oui' : 'Non (interne)' }}</mat-slide-toggle></td></tr>
          }
        </tbody>
      </table>
    </div>

    <h3>Paramètres de calcul</h3>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Paramètre</th><th>Valeur</th><th></th></tr></thead>
        <tbody>
          @for (s of settings(); track s.key) {
            <tr><td>{{ s.description }} <span class="faint small">({{ s.key }})</span></td>
              <td><input type="number" class="inline-input" [ngModel]="s.value" (ngModelChange)="drafts[s.key] = $event" [attr.aria-label]="s.description" /></td>
              <td><button mat-button (click)="save(s)" [disabled]="drafts[s.key] === undefined || drafts[s.key] + '' === s.value">Enregistrer</button></td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
})
export class SettingsTabComponent {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  readonly terminals = signal<Terminal[]>([]);
  readonly settings = signal<AppSetting[]>([]);
  drafts: Record<string, string> = {};

  constructor() {
    this.load();
  }

  load(): void {
    this.api.terminals().subscribe((t) => this.terminals.set(t));
    this.api.settings().subscribe((s) => this.settings.set(s));
  }

  toggle(t: Terminal, value: boolean): void {
    this.api.updateTerminal({ ...t, buildingBoundary: value }).subscribe(() => {
      this.snack.open(`Terminal ${t.code} mis à jour, assiduité recalculée.`, 'OK', { duration: 4000 });
      this.load();
    });
  }

  save(s: AppSetting): void {
    this.api.updateSetting(s.key, String(this.drafts[s.key])).subscribe({
      next: () => {
        delete this.drafts[s.key];
        this.snack.open('Paramètre enregistré, assiduité recalculée.', 'OK', { duration: 4000 });
        this.load();
      },
      error: () => this.snack.open('Valeur invalide.', 'OK', { duration: 4000 }),
    });
  }
}
