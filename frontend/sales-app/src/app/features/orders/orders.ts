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
import { ApiService } from '../../core/api.service';
import {
  Order, Customer, Item, OrderStatus, PaymentStatus,
  OrderStatusLabels, PaymentStatusLabels
} from '../../core/models';
import { OrderDialog } from './order-dialog';
import { PaymentDialog } from './payment-dialog';
import { OrderItemsDialog } from './order-items-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-orders',
  imports: [
    FormsModule, DatePipe, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule,
    MatTooltipModule, MatPaginatorModule
  ],
  templateUrl: './orders.html',
  styleUrl: './orders.scss'
})
export class Orders implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  orders = signal<Order[]>([]);
  customers = signal<Customer[]>([]);
  items = signal<Item[]>([]);

  // ---- Filters ----
  customerFilter = signal<string>('');
  orderDateFilter = signal<Date | null>(null);
  deliveryStatusFilter = signal<OrderStatus | null>(null);
  paidStatusFilter = signal<PaymentStatus | null>(null);

  filtered = computed<Order[]>(() => {
    const name = this.customerFilter().trim().toLowerCase();
    const date = this.orderDateFilter();
    const delivery = this.deliveryStatusFilter();
    const paid = this.paidStatusFilter();
    return this.orders().filter(o => {
      if (name && !o.customerName.toLowerCase().includes(name)) return false;
      if (delivery != null && o.status !== delivery) return false;
      if (paid != null && o.paymentStatus !== paid) return false;
      if (date && !sameDay(new Date(o.orderDate), date)) return false;
      return true;
    });
  });

  hasFilters = computed(() =>
    !!this.customerFilter() || !!this.orderDateFilter() ||
    this.deliveryStatusFilter() != null || this.paidStatusFilter() != null);

  columns = ['orderNumber', 'customer', 'orderDate', 'delivery', 'status', 'payment', 'total', 'remaining', 'actions'];

  orderStatusLabel = OrderStatusLabels;
  paymentStatusLabel = PaymentStatusLabels;
  statusOptions = Object.entries(OrderStatusLabels).map(([v, l]) => ({ value: +v, label: l }));
  paymentOptions = Object.entries(PaymentStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createPager(() => this.filtered());

  // Filter setters (reset pagination to the first page on any change)
  setCustomerFilter(v: string) { this.customerFilter.set(v); this.pager.reset(); }
  setOrderDateFilter(v: Date | null) { this.orderDateFilter.set(v); this.pager.reset(); }
  setDeliveryStatusFilter(v: OrderStatus | null) { this.deliveryStatusFilter.set(v); this.pager.reset(); }
  setPaidStatusFilter(v: PaymentStatus | null) { this.paidStatusFilter.set(v); this.pager.reset(); }
  clearFilters() {
    this.customerFilter.set('');
    this.orderDateFilter.set(null);
    this.deliveryStatusFilter.set(null);
    this.paidStatusFilter.set(null);
    this.pager.reset();
  }

  ngOnInit() {
    forkJoin({
      customers: this.api.getAllCustomers(),
      items: this.api.getAllItems()
    }).subscribe(r => { this.customers.set(r.customers); this.items.set(r.items); });
    this.load();
  }

  load() {
    this.api.getOrders().subscribe(list => this.orders.set(list));
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

  remove(o: Order) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete order ${o.orderNumber}?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteOrder(o.id).subscribe(() => { this.snack.open('Order deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }

  private replace(updated: Order) {
    this.orders.update(list => list.map(o => o.id === updated.id ? updated : o));
  }

  statusChipClass(status: number): string { return 'chip chip-' + (this.orderStatusLabel[status] ?? '').toLowerCase(); }
  payChipClass(status: number): string { return 'chip chip-' + (this.paymentStatusLabel[status] ?? '').toLowerCase(); }
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();
}
