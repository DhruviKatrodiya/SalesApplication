import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../core/auth.service';

interface NavItem { label: string; icon: string; path: string; }

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule, MatMenuModule
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell {
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly user = this.auth.currentUser;
  readonly opened = signal(true);

  readonly nav: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
    { label: 'Categories', icon: 'category', path: '/categories' },
    { label: 'Inventory', icon: 'inventory_2', path: '/inventory' },
    { label: 'Dispatch (Truck)', icon: 'local_shipping', path: '/dispatch' },
    { label: 'Customers', icon: 'people', path: '/customers' },
    { label: 'Orders', icon: 'receipt_long', path: '/orders' },
    { label: 'Reports', icon: 'bar_chart', path: '/reports' },
  ];

  toggle() { this.opened.update(v => !v); }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
