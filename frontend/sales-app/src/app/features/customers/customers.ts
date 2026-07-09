import { Component, ElementRef, inject, signal, computed, viewChild, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Customer, Route } from '../../core/models';
import { CustomerDialog } from './customer-dialog';
import { CustomerDetailsDialog } from './customer-details-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-customers',
  imports: [
    FormsModule, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule
  ],
  templateUrl: './customers.html',
  styleUrl: './customers.scss'
})
export class Customers implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  customers = signal<Customer[]>([]);
  routes = signal<Route[]>([]);
  search = signal('');               // matches customer name or phone
  routeFilter = signal<number>(0);   // 0 = "All routes" (shown selected by default)
  status = signal<'active' | 'inactive' | 'all'>('all');

  // Searchable route filter (same in-panel search pattern as the other dropdowns)
  private routeSearchInput = viewChild<ElementRef<HTMLInputElement>>('routeSearchInput');
  readonly routeSearch = signal('');
  readonly filteredRoutes = computed(() => {
    const q = this.routeSearch().toLowerCase().trim();
    return q ? this.routes().filter(r => r.name.toLowerCase().includes(q)) : this.routes();
  });
  onRouteOpened() { setTimeout(() => this.routeSearchInput()?.nativeElement.focus()); }

  columns = ['srNo', 'name', 'phone', 'email', 'route', 'status', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  ngOnInit() { this.load(); this.loadRoutes(); }

  load() {
    this.api.getCustomers({
      search: this.search() || undefined,
      routeId: this.routeFilter() || undefined,
      active: this.status(),
      page: this.pager.pageIndex() + 1,
      pageSize: this.pager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.pager.pageSize()) - 1);
      if (this.pager.pageIndex() > maxIndex) {
        this.pager.pageIndex.set(maxIndex);
        this.load();
        return;
      }
      this.customers.set(res.items);
      this.pager.total.set(res.total);
    });
  }
  loadRoutes() { this.api.getAllRoutes().subscribe(list => this.routes.set(list)); }

  private reload() { this.pager.reset(); this.load(); }
  setSearch(v: string) { this.search.set(v); this.reload(); }
  setRouteFilter(v: number) { this.routeFilter.set(v); this.reload(); }
  setStatus(v: 'active' | 'inactive' | 'all') { this.status.set(v); this.reload(); }
  hasFilters = () => !!this.search() || this.routeFilter() > 0 || this.status() !== 'all';
  clearFilters() {
    this.search.set('');
    this.routeFilter.set(0);
    this.status.set('all');
    this.reload();
  }

  viewDetails(c: Customer) {
    this.api.getCustomerDetails(c.id).subscribe(d => {
      this.dialog.open(CustomerDetailsDialog, { data: d, width: '900px', maxWidth: '96vw' });
    });
  }

  add() {
    this.dialog.open(CustomerDialog, { data: { customer: null, routes: this.routes() } }).afterClosed().subscribe(res => {
      if (res) this.api.createCustomer(res).subscribe(() => { this.snack.open('Customer added', 'Close', { duration: 2000 }); this.load(); });
    });
  }
  edit(c: Customer) {
    this.dialog.open(CustomerDialog, { data: { customer: c, routes: this.routes() } }).afterClosed().subscribe(res => {
      if (res) this.api.updateCustomer(c.id, res).subscribe(() => { this.snack.open('Customer updated', 'Close', { duration: 2000 }); this.load(); });
    });
  }
  activate(c: Customer) {
    this.api.activateCustomer(c.id).subscribe(() => { this.snack.open('Customer activated', 'Close', { duration: 2000 }); this.load(); });
  }
  remove(c: Customer) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete customer "${c.name}"?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteCustomer(c.id).subscribe({
          next: () => { this.snack.open('Customer deleted', 'Close', { duration: 2000 }); this.load(); }
        });
      });
  }
}
