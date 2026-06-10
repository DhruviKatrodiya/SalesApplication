import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Category, SubCategory } from '../../core/models';
import { CategoryDialog } from './category-dialog';
import { SubCategoryDialog } from './subcategory-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-categories',
  imports: [FormsModule, MatCardModule, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatPaginatorModule],
  templateUrl: './categories.html',
  styleUrl: './categories.scss'
})
export class Categories implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  categories = signal<Category[]>([]);        // current page of categories
  subCategories = signal<SubCategory[]>([]);  // current page of sub-categories
  allCategories = signal<Category[]>([]);     // full list, for the sub-category dialog dropdown
  selected = signal<Category | null>(null);

  categorySearch = signal('');
  subCategorySearch = signal('');

  catColumns = ['srNo', 'name', 'description', 'subCount', 'actions'];
  subColumns = ['srNo', 'name', 'description', 'itemCount', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  catPager = createServerPager(() => this.loadCategories());
  subPager = createServerPager(() => this.loadSubs());

  ngOnInit() {
    this.loadCategories();
    this.loadAllCategories();
  }

  /** Full category list kept in sync for the dialog dropdown. */
  loadAllCategories() {
    this.api.getAllCategories().subscribe(list => this.allCategories.set(list));
  }

  loadCategories() {
    this.api.getCategories({
      search: this.categorySearch(),
      page: this.catPager.pageIndex() + 1,
      pageSize: this.catPager.pageSize()
    }).subscribe(res => {
      // If deletions emptied the current page, step back to the last valid page.
      const maxIndex = Math.max(0, Math.ceil(res.total / this.catPager.pageSize()) - 1);
      if (this.catPager.pageIndex() > maxIndex) {
        this.catPager.pageIndex.set(maxIndex);
        this.loadCategories();
        return;
      }
      this.categories.set(res.items);
      this.catPager.total.set(res.total);
      const sel = this.selected();
      if (sel) {
        // Keep the selection (and its sub-categories) even if filtered off this page.
        this.selected.set(res.items.find(c => c.id === sel.id) ?? sel);
      }
    });
  }

  setCategorySearch(v: string) {
    this.categorySearch.set(v);
    this.catPager.reset();
    this.loadCategories();
  }

  select(c: Category) {
    this.selected.set(c);
    this.subCategorySearch.set('');
    this.subPager.reset();
    this.loadSubs();
  }

  loadSubs() {
    const sel = this.selected();
    if (!sel) { this.subCategories.set([]); this.subPager.total.set(0); return; }
    this.api.getSubCategories({
      categoryId: sel.id,
      search: this.subCategorySearch(),
      page: this.subPager.pageIndex() + 1,
      pageSize: this.subPager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.subPager.pageSize()) - 1);
      if (this.subPager.pageIndex() > maxIndex) {
        this.subPager.pageIndex.set(maxIndex);
        this.loadSubs();
        return;
      }
      this.subCategories.set(res.items);
      this.subPager.total.set(res.total);
    });
  }

  setSubCategorySearch(v: string) {
    this.subCategorySearch.set(v);
    this.subPager.reset();
    this.loadSubs();
  }

  /** Refresh after a category mutation (table page + dropdown list). */
  private afterCategoryChange() {
    this.loadCategories();
    this.loadAllCategories();
  }

  addCategory() {
    this.dialog.open(CategoryDialog, { data: null }).afterClosed().subscribe(res => {
      if (res) this.api.createCategory(res).subscribe(() => { this.snack.open('Category added', 'Close', { duration: 2000 }); this.afterCategoryChange(); });
    });
  }
  editCategory(c: Category, ev: Event) {
    ev.stopPropagation();
    this.dialog.open(CategoryDialog, { data: c }).afterClosed().subscribe(res => {
      if (res) this.api.updateCategory(c.id, res).subscribe(() => { this.snack.open('Category updated', 'Close', { duration: 2000 }); this.afterCategoryChange(); });
    });
  }
  deleteCategory(c: Category, ev: Event) {
    ev.stopPropagation();
    this.confirm(`Delete category "${c.name}"? This removes its sub-categories and items.`).subscribe(ok => {
      if (ok) this.api.deleteCategory(c.id).subscribe(() => {
        if (this.selected()?.id === c.id) { this.selected.set(null); this.subCategories.set([]); this.subPager.total.set(0); }
        this.snack.open('Category deleted', 'Close', { duration: 2000 });
        this.afterCategoryChange();
      });
    });
  }

  addSub() {
    const sel = this.selected();
    this.dialog.open(SubCategoryDialog, {
      data: { categories: this.allCategories(), subCategory: null, defaultCategoryId: sel?.id }
    }).afterClosed().subscribe(res => {
      if (res) this.api.createSubCategory(res).subscribe(() => { this.snack.open('Sub-category added', 'Close', { duration: 2000 }); this.loadCategories(); this.loadSubs(); });
    });
  }
  editSub(s: SubCategory) {
    this.dialog.open(SubCategoryDialog, {
      data: { categories: this.allCategories(), subCategory: s }
    }).afterClosed().subscribe(res => {
      if (res) this.api.updateSubCategory(s.id, res).subscribe(() => { this.snack.open('Sub-category updated', 'Close', { duration: 2000 }); this.loadCategories(); this.loadSubs(); });
    });
  }
  deleteSub(s: SubCategory) {
    this.confirm(`Delete sub-category "${s.name}"?`).subscribe(ok => {
      if (ok) this.api.deleteSubCategory(s.id).subscribe(() => { this.snack.open('Sub-category deleted', 'Close', { duration: 2000 }); this.loadCategories(); this.loadSubs(); });
    });
  }

  private confirm(message: string) {
    return this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message } }).afterClosed();
  }
}
