import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent), title: 'Connexion — Assiduité' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', loadComponent: () => import('./pages/overview/overview.component').then((m) => m.OverviewComponent), title: "Vue d'ensemble — Assiduité" },
      { path: 'employees', loadComponent: () => import('./pages/employees/employees.component').then((m) => m.EmployeesComponent), title: 'Collaborateurs — Assiduité' },
      { path: 'employees/:id', loadComponent: () => import('./pages/employee-detail/employee-detail.component').then((m) => m.EmployeeDetailComponent), title: 'Fiche collaborateur — Assiduité' },
      { path: 'risks', loadComponent: () => import('./pages/risks/risks.component').then((m) => m.RisksComponent), title: 'Risques & prédictions — Assiduité' },
      { path: 'model', canActivate: [roleGuard('RH', 'ADMIN')], loadComponent: () => import('./pages/model-health/model-health.component').then((m) => m.ModelHealthComponent), title: 'Santé du modèle — Assiduité' },
      { path: 'admin', canActivate: [roleGuard('RH', 'ADMIN')], loadComponent: () => import('./pages/admin/admin.component').then((m) => m.AdminComponent), title: 'Administration — Assiduité' },
      { path: 'about', loadComponent: () => import('./pages/about/about.component').then((m) => m.AboutComponent), title: 'À propos du modèle — Assiduité' },
    ],
  },
  { path: '**', redirectTo: '' },
];
