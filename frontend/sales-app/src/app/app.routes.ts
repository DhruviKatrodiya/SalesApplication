import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login').then(m => m.Login) },
  { path: 'forgot-password', loadComponent: () => import('./features/auth/forgot-password').then(m => m.ForgotPassword) },
  { path: 'reset-password', loadComponent: () => import('./features/auth/reset-password').then(m => m.ResetPassword) },

  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard) },
      { path: 'categories', loadComponent: () => import('./features/categories/categories').then(m => m.Categories) },
      { path: 'inventory', loadComponent: () => import('./features/inventory/inventory').then(m => m.Inventory) },
      { path: 'dispatch', loadComponent: () => import('./features/dispatch/dispatch').then(m => m.Dispatch) },
      { path: 'customers', loadComponent: () => import('./features/customers/customers').then(m => m.Customers) },
      { path: 'orders', loadComponent: () => import('./features/orders/orders').then(m => m.Orders) },
      { path: 'reports', loadComponent: () => import('./features/reports/reports').then(m => m.Reports) },
      { path: 'profile', loadComponent: () => import('./features/profile/profile').then(m => m.Profile) },
      { path: 'change-password', loadComponent: () => import('./features/profile/change-password').then(m => m.ChangePassword) },
    ]
  },

  { path: '**', redirectTo: '' }
];
