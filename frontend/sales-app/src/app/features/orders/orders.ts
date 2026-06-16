import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
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
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import {
  Order, Customer, Item, OrderStatus, PaymentStatus,
  OrderStatusLabels, PaymentStatusLabels
} from '../../core/models';
import { OrderDialog } from './order-dialog';
import { PaymentDialog } from './payment-dialog';
import { OrderItemsDialog } from './order-items-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';
import { DateInputDirective } from '../../shared/date-input.directive';

@Component({
  selector: 'app-orders',
  imports: [
    FormsModule, DatePipe, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule,
    MatTooltipModule, MatPaginatorModule, DateInputDirective
  ],
  templateUrl: './orders.html',
  styleUrl: './orders.scss'
})
export class Orders implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private route = inject(ActivatedRoute);

  // Route data: the "My Orders" route sets mine=true; the Orders route shows all.
  readonly mine = (this.route.snapshot.data['mine'] as boolean) ?? false;
  readonly pageTitle = (this.route.snapshot.data['title'] as string) ?? 'Orders';

  orders = signal<Order[]>([]);
  customers = signal<Customer[]>([]);
  items = signal<Item[]>([]);

  // ---- Filters ----
  orderNumberFilter = signal<string>('');
  customerFilter = signal<string>('');
  orderDateFilter = signal<Date | null>(null);
  deliveryStatusFilter = signal<number>(-1);   // -1 = "All" (shown selected by default)
  paidStatusFilter = signal<number>(-1);       // -1 = "All"
  activeFilter = signal<'active' | 'inactive' | 'all'>('all');

  hasFilters = computed(() =>
    !!this.orderNumberFilter() || !!this.customerFilter() || !!this.orderDateFilter() ||
    this.deliveryStatusFilter() >= 0 || this.paidStatusFilter() >= 0 || this.activeFilter() !== 'all');

  columns = ['orderNumber', 'customer', 'orderDate', 'delivery', 'status', 'payment', 'total', 'remaining', 'active', 'actions'];

  orderStatusLabel = OrderStatusLabels;
  paymentStatusLabel = PaymentStatusLabels;
  statusOptions = Object.entries(OrderStatusLabels).map(([v, l]) => ({ value: +v, label: l }));
  paymentOptions = Object.entries(PaymentStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  // Filter setters (reset to the first page and reload from the server on any change)
  private reload() { this.pager.reset(); this.load(); }
  setOrderNumberFilter(v: string) { this.orderNumberFilter.set(v); this.reload(); }
  setCustomerFilter(v: string) { this.customerFilter.set(v); this.reload(); }
  setOrderDateFilter(v: Date | null) { this.orderDateFilter.set(v); this.reload(); }
  setDeliveryStatusFilter(v: number) { this.deliveryStatusFilter.set(v); this.reload(); }
  setPaidStatusFilter(v: number) { this.paidStatusFilter.set(v); this.reload(); }
  setActiveFilter(v: 'active' | 'inactive' | 'all') { this.activeFilter.set(v); this.reload(); }
  clearFilters() {
    this.orderNumberFilter.set('');
    this.customerFilter.set('');
    this.orderDateFilter.set(null);
    this.deliveryStatusFilter.set(-1);
    this.paidStatusFilter.set(-1);
    this.activeFilter.set('all');
    this.reload();
  }

  ngOnInit() {
    forkJoin({
      customers: this.api.getAllCustomers(),
      items: this.api.getAllItems()
    }).subscribe(r => { this.customers.set(r.customers); this.items.set(r.items); });
    this.load();
  }

  load() {
    const d = this.orderDateFilter();
    this.api.getOrders({
      orderNumber: this.orderNumberFilter() || undefined,
      customer: this.customerFilter() || undefined,
      orderDate: d ? this.toIsoDate(d) : undefined,
      status: this.deliveryStatusFilter() >= 0 ? (this.deliveryStatusFilter() as OrderStatus) : undefined,
      paymentStatus: this.paidStatusFilter() >= 0 ? this.paidStatusFilter() : undefined,
      active: this.activeFilter(),
      mine: this.mine || undefined,
      page: this.pager.pageIndex() + 1,
      pageSize: this.pager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.pager.pageSize()) - 1);
      if (this.pager.pageIndex() > maxIndex) {
        this.pager.pageIndex.set(maxIndex);
        this.load();
        return;
      }
      this.orders.set(res.items);
      this.pager.total.set(res.total);
    });
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  create() {
    if (!this.customers().length || !this.items().length) {
      this.snack.open('Add a customer and at least one item first.', 'Close', { duration: 3000 });
      return;
    }
    this.dialog.open(OrderDialog, {
      width: '760px', maxWidth: '95vw',
      data: { customers: this.customers(), items: this.items(), order: null }
    })
      .afterClosed().subscribe(res => {
        if (res) this.api.createOrder(res).subscribe({
          next: () => { this.snack.open('Order created', 'Close', { duration: 2000 }); this.load(); },
          error: (e) => this.snack.open(e?.error?.message ?? 'Could not create order.', 'Close', { duration: 4000 })
        });
      });
  }

  edit(o: Order) {
    this.dialog.open(OrderDialog, {
      width: '760px', maxWidth: '95vw',
      data: { customers: this.customers(), items: this.items(), order: o }
    })
      .afterClosed().subscribe(res => {
        if (res) this.api.updateOrder(o.id, res).subscribe({
          next: () => { this.snack.open('Order updated', 'Close', { duration: 2000 }); this.load(); },
          error: (e) => this.snack.open(e?.error?.message ?? 'Could not update order.', 'Close', { duration: 4000 })
        });
      });
  }

  /** An order is locked (no further editing) once it is Completed. */
  isLocked(o: Order): boolean { return o.status === OrderStatus.Completed; }

  changeStatus(o: Order, status: OrderStatus) {
    this.api.updateOrderStatus(o.id, status).subscribe(updated => {
      this.snack.open('Status updated', 'Close', { duration: 1500 });
      this.replace(updated);
    });
  }

  viewItems(o: Order) {
    this.dialog.open(OrderItemsDialog, { data: o, width: '760px', maxWidth: '95vw' })
      .afterClosed().subscribe(changed => { if (changed) this.load(); });
  }

  managePayments(o: Order) {
    this.dialog.open(PaymentDialog, { data: o }).afterClosed().subscribe(changed => {
      if (changed) this.load();
    });
  }

  activate(o: Order) {
    this.api.activateOrder(o.id).subscribe(() => { this.snack.open('Order activated', 'Close', { duration: 2000 }); this.load(); });
  }
  remove(o: Order) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete order ${o.orderNumber}?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteOrder(o.id).subscribe(() => { this.snack.open('Order deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }

  downloadInvoice(o: Order) {
    this.api.downloadInvoice(o.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Invoice-${o.orderNumber}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.snack.open('Could not generate the invoice PDF.', 'Close', { duration: 4000 })
    });
  }

  private replace(updated: Order) {
    this.orders.update(list => list.map(o => o.id === updated.id ? updated : o));
  }

  statusChipClass(status: number): string { return 'chip chip-' + (this.orderStatusLabel[status] ?? '').toLowerCase(); }
  payChipClass(status: number): string { return 'chip chip-' + (this.paymentStatusLabel[status] ?? '').toLowerCase(); }
}
