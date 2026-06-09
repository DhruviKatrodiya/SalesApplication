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
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Item, SubCategory } from '../../core/models';
import { ItemDialog } from './item-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-inventory',
  imports: [
    FormsModule, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatCheckboxModule, MatPaginatorModule
  ],
  templateUrl: './inventory.html'
})
export class Inventory implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  allItems = signal<Item[]>([]);
  subCategories = signal<SubCategory[]>([]);
  filter = signal('');
  lowOnly = signal(false);

  columns = ['name', 'category', 'sku', 'unit', 'stock', 'price', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createPager(() => this.filtered());

  setFilter(v: string) { this.filter.set(v); this.pager.reset(); }
  setLowOnly(v: boolean) { this.lowOnly.set(v); this.pager.reset(); }

  ngOnInit() {
    this.load();
    this.api.getSubCategories().subscribe(s => this.subCategories.set(s));
  }

  load() { this.api.getItems().subscribe(list => this.allItems.set(list)); }

  filtered(): Item[] {
    const term = this.filter().trim().toLowerCase();
    return this.allItems().filter(i => {
      if (this.lowOnly() && i.stockQuantity > 10) return false;
      if (!term) return true;
      return i.name.toLowerCase().includes(term)
        || (i.sku ?? '').toLowerCase().includes(term)
        || i.categoryName.toLowerCase().includes(term)
        || i.subCategoryName.toLowerCase().includes(term);
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

  remove(item: Item) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete item "${item.name}"?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteItem(item.id).subscribe(() => { this.snack.open('Item deleted', 'Close', { duration: 2000 }); this.load(); });
      });
  }
}
