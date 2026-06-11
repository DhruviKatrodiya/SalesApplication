import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { CurrencyPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Item, StockRequest, StockRequestStatus, StockRequestStatusLabels, PaymentStatusLabels } from '../../core/models';
import { RequestDialog } from './request-dialog';
import { RequestPaymentDialog } from './request-payment-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-my-orders',
  imports: [
    FormsModule, DatePipe, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatSelectModule, MatTooltipModule, MatPaginatorModule
  ],
  templateUrl: './my-orders.html',
  styles: `
    .mat-column-requestNumber { min-width: 140px; white-space: nowrap; }
    .mat-column-date { min-width: 120px; white-space: nowrap; }
  `
})
export class MyOrders implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  requests = signal<StockRequest[]>([]);
  items = signal<Item[]>([]);
  statusFilter = signal<StockRequestStatus | null>(null);

  readonly Status = StockRequestStatus;
  statusLabel = StockRequestStatusLabels;
  paymentLabel = PaymentStatusLabels;
  statusOptions = Object.entries(StockRequestStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  columns = ['requestNumber', 'date', 'items', 'status', 'total', 'remaining', 'payment', 'notes', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  ngOnInit() {
    this.load();
    this.api.getAllItems().subscribe(list => this.items.set(list));
  }

  load() {
    this.api.getStockRequests({
      status: this.statusFilter() ?? undefined,
      page: this.pager.pageIndex() + 1,
      pageSize: this.pager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.pager.pageSize()) - 1);
      if (this.pager.pageIndex() > maxIndex) { this.pager.pageIndex.set(maxIndex); this.load(); return; }
      this.requests.set(res.items);
      this.pager.total.set(res.total);
    });
  }

  setStatusFilter(v: StockRequestStatus | null) { this.statusFilter.set(v); this.pager.reset(); this.load(); }

  statusChipClass(s: StockRequestStatus): string {
    return s === StockRequestStatus.Done ? 'chip chip-paid'
      : s === StockRequestStatus.Fulfilled ? 'chip chip-dispatched'
      : s === StockRequestStatus.Cancelled ? 'chip chip-remaining'
      : 'chip chip-pending';
  }

  markDone(r: StockRequest) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Mark done', message: `Mark ${r.requestNumber} as done?`, confirmText: 'Mark Done' } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.doneStockRequest(r.id).subscribe(() => { this.snack.open('Request marked done', 'Close', { duration: 2000 }); this.load(); });
      });
  }
  payChipClass(s: number): string { return 'chip chip-' + (this.paymentLabel[s] ?? '').toLowerCase(); }

  managePayments(r: StockRequest) {
    this.dialog.open(RequestPaymentDialog, { data: r }).afterClosed().subscribe(changed => {
      if (changed) this.load();
    });
  }

  create() {
    if (!this.items().length) { this.snack.open('Add some items first.', 'Close', { duration: 3000 }); return; }
    this.dialog.open(RequestDialog, { data: { items: this.items(), request: null }, width: '720px', maxWidth: '95vw' })
      .afterClosed().subscribe(res => {
        if (res) this.api.createStockRequest(res).subscribe(() => { this.snack.open('Request created', 'Close', { duration: 2000 }); this.load(); });
      });
  }

  edit(r: StockRequest) {
    this.dialog.open(RequestDialog, { data: { items: this.items(), request: r }, width: '720px', maxWidth: '95vw' })
      .afterClosed().subscribe(res => {
        if (res) this.api.updateStockRequest(r.id, res).subscribe({
          next: () => { this.snack.open('Request updated', 'Close', { duration: 2000 }); this.load(); },
          error: (e) => this.snack.open(e?.error?.message ?? 'Could not update.', 'Close', { duration: 4000 })
        });
      });
  }

  fulfill(r: StockRequest) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Fulfill request', message: `Mark ${r.requestNumber} fulfilled? The requested quantities will be added to inventory stock.`, confirmText: 'Fulfill' } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.fulfillStockRequest(r.id).subscribe(() => { this.snack.open('Request fulfilled — inventory updated', 'Close', { duration: 2500 }); this.load(); });
      });
  }

  remove(r: StockRequest) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete ${r.requestNumber}?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteStockRequest(r.id).subscribe(() => { this.snack.open('Request deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }
}
