import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Truck } from '../../core/models';
import { TruckDialog } from './truck-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-trucks',
  imports: [
    FormsModule, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatPaginatorModule
  ],
  templateUrl: './trucks.html'
})
export class Trucks implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  trucks = signal<Truck[]>([]);
  search = signal('');
  columns = ['srNo', 'name', 'items', 'units', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  ngOnInit() { this.load(); }

  load() {
    this.api.getTrucks({
      search: this.search() || undefined,
      page: this.pager.pageIndex() + 1,
      pageSize: this.pager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.pager.pageSize()) - 1);
      if (this.pager.pageIndex() > maxIndex) { this.pager.pageIndex.set(maxIndex); this.load(); return; }
      this.trucks.set(res.items);
      this.pager.total.set(res.total);
    });
  }

  setSearch(v: string) { this.search.set(v); this.pager.reset(); this.load(); }

  add() {
    this.dialog.open(TruckDialog, { data: null }).afterClosed().subscribe(res => {
      if (res) this.api.createTruck(res.name).subscribe({
        next: () => { this.snack.open('Truck added', 'Close', { duration: 2000 }); this.load(); },
        error: (e) => this.snack.open(e?.error?.message ?? 'Could not add truck.', 'Close', { duration: 4000 })
      });
    });
  }

  edit(t: Truck) {
    this.dialog.open(TruckDialog, { data: t }).afterClosed().subscribe(res => {
      if (res) this.api.updateTruck(t.id, res.name).subscribe({
        next: () => { this.snack.open('Truck updated', 'Close', { duration: 2000 }); this.load(); },
        error: (e) => this.snack.open(e?.error?.message ?? 'Could not update truck.', 'Close', { duration: 4000 })
      });
    });
  }

  remove(t: Truck) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete truck "${t.name}"?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteTruck(t.id).subscribe({
          next: () => { this.snack.open('Truck deleted', 'Close', { duration: 2000 }); this.load(); },
          error: (e) => this.snack.open(e?.error?.message ?? 'Could not delete truck.', 'Close', { duration: 4000 })
        });
      });
  }
}
