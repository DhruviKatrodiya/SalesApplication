import { Component, ElementRef, inject, signal, computed, viewChild, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { forkJoin, from } from 'rxjs';
import { concatMap } from 'rxjs/operators';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Item, Dispatch as DispatchModel, DispatchDraftItem, Truck } from '../../core/models';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

interface CartLine { itemId: number; itemName: string; quantity: number; available: number; truckLabel: string; }

@Component({
  selector: 'app-dispatch',
  imports: [
    FormsModule, DatePipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule, MatPaginatorModule
  ],
  templateUrl: './dispatch.html',
  styles: `
    .search-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .search-grid mat-form-field { width: 100%; }
    .select-search {
      position: sticky;
      top: 0;
      z-index: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: var(--mat-sys-surface-container);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .select-search-icon { color: var(--mat-sys-on-surface-variant); }
    .select-search-input {
      flex: 1;
      box-sizing: border-box;
      padding: 8px 10px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 6px;
      font: inherit;
      color: var(--mat-sys-on-surface);
      background: var(--mat-sys-surface);
      outline: none;
    }
    .select-search-input:focus { border-color: var(--mat-sys-primary); }
    .select-empty { padding: 12px 16px; color: var(--mat-sys-on-surface-variant); }
    .muted { color: var(--mat-sys-on-surface-variant); font-weight: 400; font-size: 0.9em; }
  `
})
export class Dispatch implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  items = signal<Item[]>([]);
  history = signal<DispatchModel[]>([]);
  cart = signal<CartLine[]>([]);
  trucks = signal<Truck[]>([]);                 // managed trucks (to load onto)
  historyTruckLabels = signal<string[]>([]);    // distinct truck labels in history (to filter by)

  // Searchable truck dropdown
  private truckPickSearchInput = viewChild<ElementRef<HTMLInputElement>>('truckPickSearchInput');
  readonly truckPickSearch = signal('');
  readonly filteredTruckOptions = computed(() => {
    const q = this.truckPickSearch().toLowerCase().trim();
    return q ? this.trucks().filter(t => t.name.toLowerCase().includes(q)) : this.trucks();
  });
  onTruckSelectOpened() { setTimeout(() => this.truckPickSearchInput()?.nativeElement.focus()); }

  /** All items are listed; out-of-stock ones are shown but disabled (can't be dispatched). */
  readonly dispatchableItems = computed(() => this.items());

  // In-dropdown item search
  private itemSearchInput = viewChild<ElementRef<HTMLInputElement>>('itemSearchInput');
  readonly itemSearch = signal('');
  readonly filteredDispatchItems = computed(() => {
    const q = this.itemSearch().toLowerCase().trim();
    const list = this.dispatchableItems();
    return q ? list.filter(i => i.name.toLowerCase().includes(q)) : list;
  });

  onItemSelectOpened() {
    setTimeout(() => this.itemSearchInput()?.nativeElement.focus());
  }

  truckLabel = signal('');   // entered manually; not pre-filled
  notes = signal('');
  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);

  // Dispatch-history search filters (server-side)
  truckSearch = signal('');
  dateSearch = signal<Date | null>(null);

  cartColumns = ['srNo', 'truck', 'item', 'available', 'qty', 'actions'];
  historyColumns = ['srNo', 'date', 'truck', 'items', 'notes'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  historyPager = createServerPager(() => this.loadHistory());

  private saveTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.loadHistory();
    this.loadTrucks();
    // Load items and the saved draft together, then rebuild the cart so a
    // previously selected (unsaved) dispatch reappears after navigating away.
    forkJoin({ items: this.api.getAllItems(), draft: this.api.getDispatchDraft() })
      .subscribe(({ items, draft }) => {
        this.items.set(items);
        if (draft.truckLabel) { this.truckLabel.set(draft.truckLabel); this.truckSearch.set(draft.truckLabel); }
        this.notes.set(draft.notes ?? '');
        this.cart.set(this.buildCart(draft.items, items));
      });
  }

  loadItems() { this.api.getAllItems().subscribe(list => this.items.set(list)); }
  loadTrucks() {
    this.api.getAllTrucks().subscribe(list => this.trucks.set(list));
    this.api.getDispatchTruckLabels().subscribe(labels => this.historyTruckLabels.set(labels));
  }

  /** Pick the truck the new dispatch loads onto. */
  selectTruck(name: string) {
    this.truckLabel.set(name);
    this.persistDraft();
  }

  loadHistory() {
    const d = this.dateSearch();
    this.api.getDispatches({
      truck: this.truckSearch() || undefined,
      date: d ? this.toIsoDate(d) : undefined,
      page: this.historyPager.pageIndex() + 1,
      pageSize: this.historyPager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.historyPager.pageSize()) - 1);
      if (this.historyPager.pageIndex() > maxIndex) {
        this.historyPager.pageIndex.set(maxIndex);
        this.loadHistory();
        return;
      }
      this.history.set(res.items);
      this.historyPager.total.set(res.total);
    });
  }

  setTruckSearch(v: string) { this.truckSearch.set(v); this.historyPager.reset(); this.loadHistory(); }
  setDateSearch(d: Date | null) { this.dateSearch.set(d); this.historyPager.reset(); this.loadHistory(); }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /** Rebuild cart lines from the saved draft, resolving current name/stock; drop items that no longer exist. */
  private buildCart(draftItems: DispatchDraftItem[], items: Item[]): CartLine[] {
    return draftItems.reduce<CartLine[]>((acc, di) => {
      const it = items.find(i => i.id === di.itemId);
      if (it) acc.push({
        itemId: it.id, itemName: it.name, quantity: di.quantity,
        available: it.stockQuantity, truckLabel: (di.truckLabel || 'Truck-1').trim()
      });
      return acc;
    }, []);
  }

  private persistDraft() {
    this.api.saveDispatchDraft({
      truckLabel: this.truckLabel(),
      notes: this.notes(),
      items: this.cart().map(c => ({ itemId: c.itemId, quantity: c.quantity, truckLabel: c.truckLabel }))
    }).subscribe();
  }

  /** Debounced save, for high-frequency typing (truck label, notes). */
  private scheduleSave() {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.persistDraft(), 500);
  }

  setTruckLabel(v: string) { this.truckLabel.set(v); this.scheduleSave(); }
  setNotes(v: string) { this.notes.set(v); this.scheduleSave(); }

  addToCart() {
    const id = this.selectedItemId();
    const q = Number(this.qty());
    if (!id || q <= 0) return;
    const item = this.items().find(i => i.id === id);
    if (!item || item.stockQuantity <= 0) return;   // out-of-stock items can't be dispatched
    const truck = this.truckLabel().trim();
    if (!truck) { this.snack.open('Select a truck first.', 'Close', { duration: 2500 }); return; }

    // Merge only when the same item is added to the same truck.
    const existing = this.cart().find(c => c.itemId === id && c.truckLabel === truck);
    if (existing) {
      this.cart.update(c => c.map(x =>
        x.itemId === id && x.truckLabel === truck ? { ...x, quantity: x.quantity + q } : x));
    } else {
      this.cart.update(c => [...c, { itemId: id, itemName: item.name, quantity: q, available: item.stockQuantity, truckLabel: truck }]);
    }
    this.selectedItemId.set(null);
    this.qty.set(1);
    this.persistDraft();
  }

  removeLine(line: CartLine) {
    this.cart.update(c => c.filter(x => !(x.itemId === line.itemId && x.truckLabel === line.truckLabel)));
    this.persistDraft();
  }

  /** Total quantity requested for an item across all trucks (for stock validation). */
  private totalFor(itemId: number): number {
    return this.cart().filter(c => c.itemId === itemId).reduce((s, c) => s + c.quantity, 0);
  }

  /** A line is over-stock if the item's combined quantity across trucks exceeds available. */
  isLineOverStock(line: CartLine): boolean {
    return this.totalFor(line.itemId) > line.available;
  }

  hasStockIssue(): boolean {
    return this.cart().some(c => this.isLineOverStock(c));
  }

  /** Group cart lines by truck → one dispatch payload per truck. */
  private groupByTruck() {
    const map = new Map<string, { itemId: number; quantity: number }[]>();
    for (const c of this.cart()) {
      const key = c.truckLabel || 'Truck-1';
      (map.get(key) ?? map.set(key, []).get(key)!).push({ itemId: c.itemId, quantity: c.quantity });
    }
    return [...map.entries()].map(([truckLabel, items]) => ({ truckLabel, items }));
  }

  dispatch() {
    if (!this.cart().length) { this.snack.open('Add at least one item.', 'Close', { duration: 2500 }); return; }
    if (this.hasStockIssue()) { this.snack.open('Some lines exceed available stock.', 'Close', { duration: 3000 }); return; }

    const groups = this.groupByTruck();
    const notes = this.notes();

    // Record one dispatch per truck, sequentially so stock is validated against fresh data.
    from(groups).pipe(
      concatMap(g => this.api.createDispatch({ truckLabel: g.truckLabel, notes, items: g.items }))
    ).subscribe({
      complete: () => {
        const msg = groups.length > 1
          ? `Recorded ${groups.length} dispatches (one per truck). Stock updated.`
          : 'Dispatch recorded. Stock updated.';
        this.snack.open(msg, 'Close', { duration: 3000 });
        this.cart.set([]);
        this.notes.set('');
        this.api.clearDispatchDraft().subscribe();
        this.loadItems();
        this.loadTrucks();   // truck unit totals changed
        this.historyPager.reset();
        this.loadHistory();
      },
      error: () => {
        this.snack.open('Some dispatches failed (e.g. insufficient stock). Refreshing.', 'Close', { duration: 4000 });
        this.loadItems();
        this.loadHistory();
      }
    });
  }
}
