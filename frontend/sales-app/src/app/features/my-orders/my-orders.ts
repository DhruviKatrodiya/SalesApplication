import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { CurrencyPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Item, StockRequest, StockRequestStatus, StockRequestStatusLabels, PaymentStatusLabels, StockRequestAdvance } from '../../core/models';
import { RequestDialog } from './request-dialog';
import { RequestPaymentDialog } from './request-payment-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';
import { DateInputDirective } from '../../shared/date-input.directive';

@Component({
  selector: 'app-my-orders',
  imports: [
    FormsModule, DatePipe, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatTooltipModule, MatPaginatorModule, DateInputDirective
  ],
  templateUrl: './my-orders.html',
  styles: `
    .mat-column-requestNumber { min-width: 140px; white-space: nowrap; }
    .mat-column-date { min-width: 120px; white-space: nowrap; }
    .adv-summary { display:flex; flex-wrap:wrap; gap:24px; margin-bottom:16px; padding:12px 16px;
      background: var(--mat-sys-surface-container); border-radius:8px; }
    .adv-summary > div { display:flex; flex-direction:column; }
    .adv-summary .lbl { font-size:0.8em; color: var(--mat-sys-on-surface-variant); }
    .adv-summary .val { font-weight:600; font-size:1.1em; }
    .adv-summary .val.adv { color:#084298; }
  `
})
export class MyOrders implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  requests = signal<StockRequest[]>([]);
  items = signal<Item[]>([]);
  advance = signal<StockRequestAdvance | null>(null);

  // ---- Filters ----
  requestNumberFilter = signal<string>('');
  dateFilter = signal<Date | null>(null);
  itemFilter = signal<string>('');
  statusFilter = signal<number>(-1);    // -1 = "All" (shown selected by default)
  paymentFilter = signal<number>(-1);   // -1 = "All"
  activeFilter = signal<'active' | 'inactive' | 'all'>('all');

  hasFilters = () =>
    !!this.requestNumberFilter() || !!this.dateFilter() || !!this.itemFilter() ||
    this.statusFilter() >= 0 || this.paymentFilter() >= 0 || this.activeFilter() !== 'all';

  readonly Status = StockRequestStatus;
  statusLabel = StockRequestStatusLabels;
  paymentLabel = PaymentStatusLabels;
  statusOptions = Object.entries(StockRequestStatusLabels).map(([v, l]) => ({ value: +v, label: l }));
  paymentOptions = Object.entries(PaymentStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  columns = ['requestNumber', 'date', 'items', 'status', 'total', 'remaining', 'payment', 'active', 'notes', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  ngOnInit() {
    this.load();
    this.loadAdvance();
    this.api.getAllItems().subscribe(list => this.items.set(list));
  }

  loadAdvance() { this.api.getRequestAdvance().subscribe(a => this.advance.set(a)); }

  load() {
    const d = this.dateFilter();
    this.api.getStockRequests({
      requestNumber: this.requestNumberFilter() || undefined,
      date: d ? this.toIsoDate(d) : undefined,
      item: this.itemFilter() || undefined,
      status: this.statusFilter() >= 0 ? this.statusFilter() : undefined,
      paymentStatus: this.paymentFilter() >= 0 ? this.paymentFilter() : undefined,
      active: this.activeFilter(),
      page: this.pager.pageIndex() + 1,
      pageSize: this.pager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.pager.pageSize()) - 1);
      if (this.pager.pageIndex() > maxIndex) { this.pager.pageIndex.set(maxIndex); this.load(); return; }
      this.requests.set(res.items);
      this.pager.total.set(res.total);
    });
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private reload() { this.pager.reset(); this.load(); }
  setRequestNumberFilter(v: string) { this.requestNumberFilter.set(v); this.reload(); }
  setDateFilter(v: Date | null) { this.dateFilter.set(v); this.reload(); }
  setItemFilter(v: string) { this.itemFilter.set(v); this.reload(); }
  setStatusFilter(v: number) { this.statusFilter.set(v); this.reload(); }
  setPaymentFilter(v: number) { this.paymentFilter.set(v); this.reload(); }
  setActiveFilter(v: 'active' | 'inactive' | 'all') { this.activeFilter.set(v); this.reload(); }
  clearFilters() {
    this.requestNumberFilter.set('');
    this.dateFilter.set(null);
    this.itemFilter.set('');
    this.statusFilter.set(-1);
    this.paymentFilter.set(-1);
    this.activeFilter.set('all');
    this.reload();
  }

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
      if (changed) { this.load(); this.loadAdvance(); }
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

  activate(r: StockRequest) {
    this.api.activateStockRequest(r.id).subscribe(() => { this.snack.open('Request activated', 'Close', { duration: 2000 }); this.load(); });
  }

  remove(r: StockRequest) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete ${r.requestNumber}?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteStockRequest(r.id).subscribe(() => { this.snack.open('Request deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }
}
