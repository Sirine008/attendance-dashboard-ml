import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { dateTime } from '../../core/format';
import { AuditEntry } from '../../core/models';

@Component({
  selector: 'app-audit-tab',
  template: `
    <p class="muted small">Traçabilité (loi organique n° 2004-63) : chaque consultation de fiche individuelle et chaque action d'administration est journalisée.</p>
    <div class="table-wrap">
      <table class="simple">
        <thead><tr><th>Date</th><th>Utilisateur</th><th>Action</th><th>Objet</th><th>Détail</th><th>IP</th></tr></thead>
        <tbody>
          @for (a of entries(); track a.id) {
            <tr><td class="small">{{ dt(a.createdAt) }}</td><td>{{ a.username }}</td><td><code>{{ a.action }}</code></td>
              <td class="small">{{ a.entityType }} {{ a.entityId }}</td><td class="small">{{ a.details }}</td><td class="small muted">{{ a.ipAddress }}</td></tr>
          } @empty { <tr><td colspan="6" class="muted">Aucune entrée.</td></tr> }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './admin-shared.scss',
})
export class AuditTabComponent {
  readonly entries = signal<AuditEntry[]>([]);
  readonly dt = dateTime;

  constructor() {
    inject(ApiService).audit().subscribe((e) => this.entries.set(e));
  }
}
