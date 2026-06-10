import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Item, Customer, Order, OrderStatus } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, CurrencyPipe, MatCardModule, MatIconModule, MatButtonModule],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  private api = inject(ApiService);

  items = signal<Item[]>([]);
  customers = signal<Customer[]>([]);
  orders = signal<Order[]>([]);

  lowStockCount = computed(() => this.items().filter(i => i.stockQuantity <= 10).length);
  pendingOrders = computed(() =>
    this.orders().filter(o => o.status !== OrderStatus.Delivered && o.status !== OrderStatus.Completed).length);
  totalRemaining = computed(() => this.orders().reduce((s, o) => s + o.remainingAmount, 0));
  totalSales = computed(() => this.orders().reduce((s, o) => s + o.totalAmount, 0));

  ngOnInit() {
    forkJoin({
      items: this.api.getAllItems(),
      customers: this.api.getAllCustomers(),
      orders: this.api.getOrders()
    }).subscribe(r => {
      this.items.set(r.items);
      this.customers.set(r.customers);
      this.orders.set(r.orders);
    });
  }
}
