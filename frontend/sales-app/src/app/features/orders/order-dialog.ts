import { Component, ElementRef, inject, signal, computed, viewChild, OnInit } from '@angular/core';
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
import { ApiService } from '../../core/api.service';
import { Customer, Item, Order, OrderStatus, OrderStatusLabels, OrderSource, Truck } from '../../core/models';

interface DialogData { customers: Customer[]; items: Item[]; order: Order | null; }
interface Line { itemId: number; itemName: string; quantity: number; unitPrice: number; }
interface PickerOption { id: number; name: string; unitPrice: number; available: number; }

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
    .over { color: var(--mat-sys-error); }
    .avail-hint { color: var(--mat-sys-on-surface-variant); font-size: 0.85em; }
    table { width: 100%; }
  `
})
export class OrderDialog implements OnInit {
  private ref = inject(MatDialogRef<OrderDialog>);
  private api = inject(ApiService);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  customerId = signal<number | null>(this.data.order?.customerId ?? null);
  deliveryDate = signal<Date | null>(
    this.data.order
      ? (this.data.order.deliveryDate ? new Date(this.data.order.deliveryDate) : null)
      : new Date()
  );
  notes = signal<string>(this.data.order?.notes ?? '');
  status = signal<OrderStatus>(this.data.order?.status ?? OrderStatus.Pending);
  statusOptions = Object.entries(OrderStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  // Stock handling: Inventory consumes godown stock; Dispatch consumes a specific truck's stock.
  readonly OrderSource = OrderSource;
  source = signal<OrderSource>(this.data.order?.source ?? OrderSource.Inventory);

  // ---- Trucks (only relevant when source = Dispatch) ----
  trucks = signal<Truck[]>([]);
  truckId = signal<number | null>(this.data.order?.truckId ?? null);
  truckStock = signal<PickerOption[]>([]);   // the selected truck's items + available qty
  private truckSearchInput = viewChild<ElementRef<HTMLInputElement>>('truckSearchInput');
  readonly truckSearch = signal('');
  readonly filteredTrucks = computed(() => {
    const q = this.truckSearch().toLowerCase().trim();
    return q ? this.trucks().filter(t => t.name.toLowerCase().includes(q)) : this.trucks();
  });
  onTruckSelectOpened() { setTimeout(() => this.truckSearchInput()?.nativeElement.focus()); }

  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);

  // The item picker switches between the full catalog (Inventory) and the truck's stock (Dispatch).
  readonly pickerOptions = computed<PickerOption[]>(() =>
    this.source() === OrderSource.Dispatch
      ? this.truckStock()
      : this.data.items.map(i => ({ id: i.id, name: i.name, unitPrice: i.unitPrice, available: i.stockQuantity }))
  );

  private itemSearchInput = viewChild<ElementRef<HTMLInputElement>>('itemSearchInput');
  readonly itemSearch = signal('');
  readonly filteredItems = computed(() => {
    const q = this.itemSearch().toLowerCase().trim();
    const list = this.pickerOptions();
    return q ? list.filter(i => i.name.toLowerCase().includes(q)) : list;
  });
  onItemSelectOpened() { setTimeout(() => this.itemSearchInput()?.nativeElement.focus()); }

  lines = signal<Line[]>(
    (this.data.order?.items ?? []).map(i => ({ itemId: i.itemId, itemName: i.itemName, quantity: i.quantity, unitPrice: i.unitPrice }))
  );

  // For edit mode: the order's own quantities are still "available" to it (server restores them on update).
  private readonly originalQty = new Map<number, number>(
    (this.data.order?.items ?? []).map(i => [i.itemId, i.quantity] as [number, number])
  );

  columns = ['srNo', 'item', 'qty', 'price', 'total', 'actions'];
  trackByItemId = (_: number, l: Line) => l.itemId;
  total = computed(() => this.lines().reduce((s, l) => s + l.quantity * l.unitPrice, 0));
  isEdit = !!this.data.order;

  ngOnInit() {
    this.loadTrucks();
    // Editing an existing dispatch order: load its truck's stock so availability is known.
    if (this.source() === OrderSource.Dispatch && this.truckId()) this.loadTruckStock(this.truckId()!);
  }

  private loadTrucks() {
    this.api.getAllTrucks({ withStock: true }).subscribe(list => this.trucks.set(list));
  }

  private loadTruckStock(truckId: number) {
    this.api.getTruckStock(truckId).subscribe(stock =>
      this.truckStock.set(stock.map(s => ({ id: s.itemId, name: s.name, unitPrice: s.unitPrice, available: s.quantity }))));
  }

  onSourceChange(src: OrderSource) {
    if (src === this.source()) return;
    this.source.set(src);
    // Switching the stock source invalidates the current lines (different bucket/prices).
    this.lines.set([]);
    this.selectedItemId.set(null);
    this.itemSearch.set('');
    if (src === OrderSource.Dispatch) { this.loadTrucks(); }
    else { this.truckId.set(null); this.truckStock.set([]); }
  }

  onTruckChange(id: number | null) {
    this.truckId.set(id);
    this.truckSearch.set('');
    this.lines.set([]);            // a new truck means new stock — start fresh
    this.selectedItemId.set(null);
    this.truckStock.set([]);
    if (id) this.loadTruckStock(id);
  }

  /** Available quantity for an item in the current bucket (adds back this order's own qty when editing). */
  availableFor(itemId: number): number {
    const opt = this.pickerOptions().find(o => o.id === itemId);
    let avail = opt ? opt.available : 0;
    if (this.isEdit) {
      const orig = this.originalQty.get(itemId);
      const sameBucket = this.source() === OrderSource.Dispatch
        ? (this.data.order!.source === OrderSource.Dispatch && (this.data.order!.truckId ?? null) === this.truckId())
        : (this.data.order!.source !== OrderSource.Dispatch);
      if (orig && sameBucket) avail += orig;
    }
    return avail;
  }

  isLineOverStock(l: Line): boolean { return l.quantity > this.availableFor(l.itemId); }
  hasStockIssue(): boolean { return this.lines().some(l => this.isLineOverStock(l)); }

  addLine() {
    const id = this.selectedItemId();
    const q = Number(this.qty());
    if (!id || q <= 0) return;
    const opt = this.pickerOptions().find(i => i.id === id);
    if (!opt || opt.available <= 0) return;   // out-of-stock items can't be added
    const existing = this.lines().find(l => l.itemId === id);
    if (existing) {
      this.lines.update(ls => ls.map(l => l.itemId === id ? { ...l, quantity: l.quantity + q } : l));
    } else {
      this.lines.update(ls => [...ls, { itemId: id, itemName: opt.name, quantity: q, unitPrice: opt.unitPrice }]);
    }
    this.selectedItemId.set(null);
    this.qty.set(1);
  }

  updateQty(itemId: number, q: number) {
    this.lines.update(ls => ls.map(l => l.itemId === itemId ? { ...l, quantity: Number(q) } : l));
  }
  removeLine(itemId: number) {
    this.lines.update(ls => ls.filter(l => l.itemId !== itemId));
  }

  canSave(): boolean {
    if (!this.customerId() || this.lines().length === 0) return false;
    if (this.source() === OrderSource.Dispatch && !this.truckId()) return false;
    if (this.hasStockIssue()) return false;
    return true;
  }

  save() {
    if (!this.canSave()) return;
    const dd = this.deliveryDate();
    this.ref.close({
      customerId: this.customerId(),
      deliveryDate: dd ? toLocalIso(dd) : null,
      notes: this.notes(),
      status: this.isEdit ? this.status() : undefined,
      source: this.source(),
      truckId: this.source() === OrderSource.Dispatch ? this.truckId() : null,
      items: this.lines().map(l => ({ itemId: l.itemId, quantity: l.quantity, unitPrice: l.unitPrice }))
    });
  }
}

function toLocalIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
