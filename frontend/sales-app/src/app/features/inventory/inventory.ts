import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Item, SubCategory } from '../../core/models';
import { ItemDialog } from './item-dialog';
import { PriceHistoryDialog } from './price-history-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-inventory',
  imports: [
    FormsModule, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatCheckboxModule, MatSelectModule, MatPaginatorModule
  ],
  templateUrl: './inventory.html',
  styles: `
    .search-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .search-grid mat-form-field { width: 100%; }
  `
})
export class Inventory implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  items = signal<Item[]>([]);    // server-filtered list (current page is sliced client-side)
  subCategories = signal<SubCategory[]>([]);
  categoryFilter = signal('');   // category / sub-category name
  itemFilter = signal('');       // item name
  skuFilter = signal('');        // SKU
  lowOnly = signal(false);       // stock <= 10
  status = signal<'active' | 'inactive' | 'all'>('all');

  columns = ['srNo', 'name', 'category', 'sku', 'unit', 'stock', 'dispatchStock', 'price', 'status', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createServerPager(() => this.load());

  setCategoryFilter(v: string) { this.categoryFilter.set(v); this.reload(); }
  setItemFilter(v: string) { this.itemFilter.set(v); this.reload(); }
  setSkuFilter(v: string) { this.skuFilter.set(v); this.reload(); }
  setLowOnly(v: boolean) { this.lowOnly.set(v); this.reload(); }
  setStatus(v: 'active' | 'inactive' | 'all') { this.status.set(v); this.reload(); }
  hasFilters = () => !!this.categoryFilter() || !!this.itemFilter() || !!this.skuFilter() || this.lowOnly() || this.status() !== 'all';
  clearFilters() {
    this.categoryFilter.set('');
    this.itemFilter.set('');
    this.skuFilter.set('');
    this.lowOnly.set(false);
    this.status.set('all');
    this.reload();
  }

  private reload() { this.pager.reset(); this.load(); }

  ngOnInit() {
    this.load();
    this.api.getAllSubCategories().subscribe(s => this.subCategories.set(s));
  }

  load() {
    this.api.getItems({
      category: this.categoryFilter() || undefined,
      item: this.itemFilter() || undefined,
      sku: this.skuFilter() || undefined,
      lowStock: this.lowOnly() || undefined,
      active: this.status(),
      page: this.pager.pageIndex() + 1,
      pageSize: this.pager.pageSize(),
    }).subscribe(res => {
      // Step back if deletions/filters emptied the current page.
      const maxIndex = Math.max(0, Math.ceil(res.total / this.pager.pageSize()) - 1);
      if (this.pager.pageIndex() > maxIndex) {
        this.pager.pageIndex.set(maxIndex);
        this.load();
        return;
      }
      this.items.set(res.items);
      this.pager.total.set(res.total);
    });
  }

  add() {
    if (!this.subCategories().length) {
      this.snack.open('Create a category and sub-category first.', 'Close', { duration: 3000 });
      return;
    }
    this.dialog.open(ItemDialog, { data: { subCategories: this.subCategories(), item: null } })
      .afterClosed().subscribe(res => {
        if (res) this.api.createItem(res).subscribe(() => { this.snack.open('Item added', 'Close', { duration: 2000 }); this.load(); });
      });
  }

  edit(item: Item) {
    this.dialog.open(ItemDialog, { data: { subCategories: this.subCategories(), item } })
      .afterClosed().subscribe(res => {
        if (res) this.api.updateItem(item.id, res).subscribe(() => { this.snack.open('Item updated', 'Close', { duration: 2000 }); this.load(); });
      });
  }

  priceHistory(item: Item) {
    this.dialog.open(PriceHistoryDialog, { data: { id: item.id, name: item.name }, width: '640px', maxWidth: '95vw' });
  }

  activate(item: Item) {
    this.api.activateItem(item.id).subscribe(() => { this.snack.open('Item activated', 'Close', { duration: 2000 }); this.load(); });
  }

  remove(item: Item) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete item "${item.name}"?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteItem(item.id).subscribe(() => { this.snack.open('Item deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }
}
