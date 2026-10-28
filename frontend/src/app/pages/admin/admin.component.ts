import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { AuthService } from '../../core/auth.service';
import { AuditTabComponent } from './audit-tab.component';
import { CalendarTabComponent } from './calendar-tab.component';
import { ImportTabComponent } from './import-tab.component';
import { LeavesTabComponent } from './leaves-tab.component';
import { SchedulesTabComponent } from './schedules-tab.component';
import { SettingsTabComponent } from './settings-tab.component';
import { UsersTabComponent } from './users-tab.component';

@Component({
  selector: 'app-admin',
  imports: [MatTabsModule, ImportTabComponent, SchedulesTabComponent, CalendarTabComponent, LeavesTabComponent, SettingsTabComponent,
    UsersTabComponent, AuditTabComponent],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Administration</h1>
          <p>Import des pointages, règles métier et référentiels. Chaque modification recalcule l'assiduité et est tracée.</p>
        </div>
      </header>
      <mat-tab-group mat-stretch-tabs="false" animationDuration="150ms" class="card tabs">
        <mat-tab label="Import"><ng-template matTabContent><app-import-tab /></ng-template></mat-tab>
        <mat-tab label="Horaires de référence"><ng-template matTabContent><app-schedules-tab /></ng-template></mat-tab>
        <mat-tab label="Fériés & périodes"><ng-template matTabContent><app-calendar-tab /></ng-template></mat-tab>
        <mat-tab label="Congés"><ng-template matTabContent><app-leaves-tab /></ng-template></mat-tab>
        <mat-tab label="Terminaux & paramètres"><ng-template matTabContent><app-settings-tab /></ng-template></mat-tab>
        @if (auth.isAdmin()) {
          <mat-tab label="Utilisateurs"><ng-template matTabContent><app-users-tab /></ng-template></mat-tab>
          <mat-tab label="Journal d'audit"><ng-template matTabContent><app-audit-tab /></ng-template></mat-tab>
        }
      </mat-tab-group>
    </div>
  `,
  styles: `.tabs { padding: 0 0 8px; } :host ::ng-deep .mat-mdc-tab-body-content { padding: 20px; }
    @media (max-width: 600px) { :host ::ng-deep .mat-mdc-tab-body-content { padding: 12px; } }`,
})
export class AdminComponent {
  protected readonly auth = inject(AuthService);
}
