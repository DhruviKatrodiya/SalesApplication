import { Component, ElementRef, inject, signal, computed, viewChild } from '@angular/core';
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
import { MatRadioModule } from '@angular/material/radio';
import { Customer, Item, Order, OrderStatus, OrderStatusLabels, OrderSource } from '../../core/models';

interface DialogData { customers: Customer[]; items: Item[]; order: Order | null; }
interface Line { itemId: number; itemName: string; quantity: number; unitPrice: number; }

@Component({
  selector: 'app-order-dialog',
  imports: [
    FormsModule, CurrencyPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatTableModule, MatDatepickerModule, MatRadioModule
  ],
  templateUrl: './order-dialog.html',
  styles: `
    .dialog-form { width: 100%; box-sizing: border-box; }
    .full { width: 100%; }
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-start; }
    .row > mat-form-field { flex: 1 1 220px; min-width: 0; }
    .select-search {
      position: sticky; top: 0; z-index: 1;
      display: flex; align-items: center; gap: 8px;
      padding: 8px 12px;
      background: var(--mat-sys-surface-container);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .select-search-icon { color: var(--mat-sys-on-surface-variant); }
    .select-search-input {
      flex: 1; box-sizing: border-box; padding: 8px 10px;
      border: 1px solid var(--mat-sys-outline-variant); border-radius: 6px;
      font: inherit; color: var(--mat-sys-on-surface); background: var(--mat-sys-surface); outline: none;
    }
    .select-search-input:focus { border-color: var(--mat-sys-primary); }
    .select-empty { padding: 12px 16px; color: var(--mat-sys-on-surface-variant); }
    .source-group { display:flex; align-items:center; gap:16px; margin-bottom:8px; flex-wrap:wrap; }
    .source-label { color: var(--mat-sys-on-surface-variant); font-weight:600; }
    .total-row { display:flex; justify-content:flex-end; font-weight:600; margin-top:8px; font-size:1.1em; }
    .req-msg { color: var(--mat-sys-error); font-size: 0.85em; padding: 8px 4px; }
    table { width: 100%; }
  `
})
export class OrderDialog {
  private ref = inject(MatDialogRef<OrderDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  customerId = signal<number | null>(this.data.order?.customerId ?? null);
  // Edit: keep the order's existing delivery date (may be unset). New order: default to today (still editable).
  deliveryDate = signal<Date | null>(
    this.data.order
      ? (this.data.order.deliveryDate ? new Date(this.data.order.deliveryDate) : null)
      : new Date()
  );
  notes = signal<string>(this.data.order?.notes ?? '');
  status = signal<OrderStatus>(this.data.order?.status ?? OrderStatus.Pending);
  statusOptions = Object.entries(OrderStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  // Stock handling: Inventory consumes godown stock, Dispatch consumes truck stock.
  readonly OrderSource = OrderSource;
  source = signal<OrderSource>(this.data.order?.source ?? OrderSource.Inventory);

  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);

  // In-dropdown item search
  private itemSearchInput = viewChild<ElementRef<HTMLInputElement>>('itemSearchInput');
  readonly itemSearch = signal('');
  readonly filteredItems = computed(() => {
    const q = this.itemSearch().toLowerCase().trim();
    return q ? this.data.items.filter(i => i.name.toLowerCase().includes(q)) : this.data.items;
  });
  onItemSelectOpened() {
    setTimeout(() => this.itemSearchInput()?.nativeElement.focus());
  }

  lines = signal<Line[]>(
    (this.data.order?.items ?? []).map(i => ({ itemId: i.itemId, itemName: i.itemName, quantity: i.quantity, unitPrice: i.unitPrice }))
  );

  columns = ['srNo', 'item', 'qty', 'price', 'total', 'actions'];
  // Keep rows stable across edits so number inputs don't lose focus mid-typing.
  trackByItemId = (_: number, l: Line) => l.itemId;
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
      // Status only applies when editing; new orders always start Pending (server-enforced).
      status: this.isEdit ? this.status() : undefined,
      // Stock source controls which bucket (inventory vs truck) the order draws from.
      source: this.source(),
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
