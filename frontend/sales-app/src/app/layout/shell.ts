import { Component, inject, signal, effect } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { BreakpointObserver } from '@angular/cdk/layout';
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
  private breakpoints = inject(BreakpointObserver);

  readonly user = this.auth.currentUser;
  readonly opened = signal(true);

  /** True on phone/tablet-portrait widths; drives drawer mode + bottom nav. */
  readonly isMobile = toSignal(
    this.breakpoints.observe('(max-width: 768px)').pipe(map(r => r.matches)),
    { initialValue: false }
  );

  readonly nav: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
    { label: 'Categories', icon: 'category', path: '/categories' },
    { label: 'Inventory', icon: 'inventory_2', path: '/inventory' },
    { label: 'Dispatch (Truck)', icon: 'local_shipping', path: '/dispatch' },
    { label: 'Customers', icon: 'people', path: '/customers' },
    { label: 'Routes', icon: 'route', path: '/routes' },
    { label: 'Customer Orders', icon: 'receipt_long', path: '/orders' },
    { label: 'My Orders', icon: 'assignment_ind', path: '/my-orders' },
    { label: 'Reports', icon: 'bar_chart', path: '/reports' },
  ];

  /** Primary destinations surfaced in the mobile bottom tab bar. */
  readonly bottomNav: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
    { label: 'Inventory', icon: 'inventory_2', path: '/inventory' },
    { label: 'Customers', icon: 'people', path: '/customers' },
    { label: 'Orders', icon: 'receipt_long', path: '/orders' },
  ];

  constructor() {
    // Drawer stays pinned open on desktop, and collapses into an overlay on mobile.
    effect(() => this.opened.set(!this.isMobile()));
  }

  toggle() { this.opened.update(v => !v); }

  /** On mobile the overlay drawer should dismiss itself after a destination is chosen. */
  onNavClick() {
    if (this.isMobile()) this.opened.set(false);
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
