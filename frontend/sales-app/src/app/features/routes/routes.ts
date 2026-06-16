import { Component, inject, signal, OnInit } from '@angular/core';
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
import { Route } from '../../core/models';
import { RouteDialog } from './route-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-routes',
  imports: [
    FormsModule, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule
  ],
  templateUrl: './routes.html'
})
export class RoutesPage implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  routes = signal<Route[]>([]);
  search = signal('');
  status = signal<'active' | 'inactive' | 'all'>('all');

  columns = ['srNo', 'name', 'description', 'status', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  ngOnInit() { this.load(); }

  load() {
    this.api.getRoutes({
      search: this.search() || undefined,
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
      this.routes.set(res.items);
      this.pager.total.set(res.total);
    });
  }

  setSearch(v: string) { this.search.set(v); this.pager.reset(); this.load(); }
  setStatus(v: 'active' | 'inactive' | 'all') { this.status.set(v); this.pager.reset(); this.load(); }
  hasFilters = () => !!this.search() || this.status() !== 'all';
  clearFilters() { this.search.set(''); this.status.set('all'); this.pager.reset(); this.load(); }

  add() {
    this.dialog.open(RouteDialog, { data: null }).afterClosed().subscribe(res => {
      if (res) this.api.createRoute(res).subscribe(() => { this.snack.open('Route added', 'Close', { duration: 2000 }); this.load(); });
    });
  }
  edit(r: Route) {
    this.dialog.open(RouteDialog, { data: r }).afterClosed().subscribe(res => {
      if (res) this.api.updateRoute(r.id, res).subscribe(() => { this.snack.open('Route updated', 'Close', { duration: 2000 }); this.load(); });
    });
  }
  activate(r: Route) {
    this.api.activateRoute(r.id).subscribe(() => { this.snack.open('Route activated', 'Close', { duration: 2000 }); this.load(); });
  }
  remove(r: Route) {
    const msg = r.customerCount > 0
      ? `Delete route "${r.name}"? ${r.customerCount} customer(s) will be unassigned.`
      : `Delete route "${r.name}"?`;
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: msg } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteRoute(r.id).subscribe(() => { this.snack.open('Route deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }
}
