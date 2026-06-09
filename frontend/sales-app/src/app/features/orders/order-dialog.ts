import { Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { Customer, Item, Order } from '../../core/models';

interface DialogData { customers: Customer[]; items: Item[]; order: Order | null; }
interface Line { itemId: number; itemName: string; quantity: number; unitPrice: number; }

@Component({
  selector: 'app-order-dialog',
  imports: [
    FormsModule, CurrencyPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatTableModule, MatDatepickerModule
  ],
  templateUrl: './order-dialog.html',
  styles: `
    .dialog-form { width: 100%; box-sizing: border-box; }
    .full { width: 100%; }
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-start; }
    .row > mat-form-field { flex: 1 1 220px; min-width: 0; }
    .total-row { display:flex; justify-content:flex-end; font-weight:600; margin-top:8px; font-size:1.1em; }
    .req-msg { color: var(--mat-sys-error); font-size: 0.85em; padding: 8px 4px; }
    table { width: 100%; }
  `
})
export class OrderDialog {
  private ref = inject(MatDialogRef<OrderDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  customerId = signal<number | null>(this.data.order?.customerId ?? null);
  deliveryDate = signal<Date | null>(this.data.order?.deliveryDate ? new Date(this.data.order.deliveryDate) : null);
  notes = signal<string>(this.data.order?.notes ?? '');

  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);

  lines = signal<Line[]>(
    (this.data.order?.items ?? []).map(i => ({ itemId: i.itemId, itemName: i.itemName, quantity: i.quantity, unitPrice: i.unitPrice }))
  );

  columns = ['item', 'qty', 'price', 'total', 'actions'];
  total = computed(() => this.lines().reduce((s, l) => s + l.quantity * l.unitPrice, 0));
  isEdit = !!this.data.order;

  addLine() {
    const id = this.selectedItemId();
    const q = Number(this.qty());
    if (!id || q <= 0) return;
    const item = this.data.items.find(i => i.id === id);
    if (!item) return;
    const existing = this.lines().find(l => l.itemId === id);
    if (existing) {
      this.lines.update(ls => ls.map(l => l.itemId === id ? { ...l, quantity: l.quantity + q } : l));
    } else {
      this.lines.update(ls => [...ls, { itemId: id, itemName: item.name, quantity: q, unitPrice: item.unitPrice }]);
    }
    this.selectedItemId.set(null);
    this.qty.set(1);
  }

  updatePrice(itemId: number, price: number) {
    this.lines.update(ls => ls.map(l => l.itemId === itemId ? { ...l, unitPrice: Number(price) } : l));
  }
  updateQty(itemId: number, q: number) {
    this.lines.update(ls => ls.map(l => l.itemId === itemId ? { ...l, quantity: Number(q) } : l));
  }
  removeLine(itemId: number) {
    this.lines.update(ls => ls.filter(l => l.itemId !== itemId));
  }

  canSave(): boolean {
    return !!this.customerId() && this.lines().length > 0;
  }

  save() {
    if (!this.canSave()) return;
    const dd = this.deliveryDate();
    this.ref.close({
      customerId: this.customerId(),
      deliveryDate: dd ? toLocalIso(dd) : null,
      notes: this.notes(),
      items: this.lines().map(l => ({ itemId: l.itemId, quantity: l.quantity, unitPrice: l.unitPrice }))
    });
  }
}

function toLocalIso(d: Date): string {
  // keep the chosen calendar date without timezone shifting
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
