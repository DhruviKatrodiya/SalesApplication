import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Item, Dispatch as DispatchModel } from '../../core/models';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

interface CartLine { itemId: number; itemName: string; quantity: number; available: number; }

@Component({
  selector: 'app-dispatch',
  imports: [
    FormsModule, DatePipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule
  ],
  templateUrl: './dispatch.html'
})
export class Dispatch implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  items = signal<Item[]>([]);
  history = signal<DispatchModel[]>([]);
  cart = signal<CartLine[]>([]);

  truckLabel = signal('Truck-1');
  notes = signal('');
  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);

  cartColumns = ['item', 'available', 'qty', 'actions'];
  historyColumns = ['date', 'truck', 'items', 'notes'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  historyPager = createPager(() => this.history());

  ngOnInit() { this.loadItems(); this.loadHistory(); }

  loadItems() { this.api.getItems().subscribe(list => this.items.set(list)); }
  loadHistory() { this.api.getDispatches().subscribe(list => this.history.set(list)); }

  addToCart() {
    const id = this.selectedItemId();
    const q = Number(this.qty());
    if (!id || q <= 0) return;
    const item = this.items().find(i => i.id === id);
    if (!item) return;

    const existing = this.cart().find(c => c.itemId === id);
    if (existing) {
      this.cart.update(c => c.map(x => x.itemId === id ? { ...x, quantity: x.quantity + q } : x));
    } else {
      this.cart.update(c => [...c, { itemId: id, itemName: item.name, quantity: q, available: item.stockQuantity }]);
    }
    this.selectedItemId.set(null);
    this.qty.set(1);
  }

  removeLine(itemId: number) {
    this.cart.update(c => c.filter(x => x.itemId !== itemId));
  }

  hasStockIssue(): boolean {
    return this.cart().some(c => c.quantity > c.available);
  }

  dispatch() {
    if (!this.cart().length) { this.snack.open('Add at least one item.', 'Close', { duration: 2500 }); return; }
    if (this.hasStockIssue()) { this.snack.open('Some lines exceed available stock.', 'Close', { duration: 3000 }); return; }

    const body = {
      truckLabel: this.truckLabel(),
      notes: this.notes(),
      items: this.cart().map(c => ({ itemId: c.itemId, quantity: c.quantity }))
    };
    this.api.createDispatch(body).subscribe({
      next: () => {
        this.snack.open('Dispatch recorded. Stock updated.', 'Close', { duration: 3000 });
        this.cart.set([]);
        this.notes.set('');
        this.loadItems();
        this.loadHistory();
      }
    });
  }
}
