import { Component, inject, signal, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Category, SubCategory } from '../../core/models';
import { CategoryDialog } from './category-dialog';
import { SubCategoryDialog } from './subcategory-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-categories',
  imports: [MatCardModule, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule],
  templateUrl: './categories.html',
  styleUrl: './categories.scss'
})
export class Categories implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  categories = signal<Category[]>([]);
  subCategories = signal<SubCategory[]>([]);
  selected = signal<Category | null>(null);

  catColumns = ['name', 'description', 'subCount', 'actions'];
  subColumns = ['name', 'description', 'itemCount', 'actions'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  catPager = createPager(() => this.categories());
  subPager = createPager(() => this.subCategories());

  ngOnInit() { this.loadCategories(); }

  loadCategories() {
    this.api.getCategories().subscribe(list => {
      this.categories.set(list);
      const sel = this.selected();
      if (sel) {
        const fresh = list.find(c => c.id === sel.id) ?? null;
        this.selected.set(fresh);
        if (fresh) this.loadSubs(fresh.id);
      }
    });
  }

  select(c: Category) {
    this.selected.set(c);
    this.subPager.reset();
    this.loadSubs(c.id);
  }

  loadSubs(categoryId: number) {
    this.api.getSubCategories(categoryId).subscribe(list => this.subCategories.set(list));
  }

  addCategory() {
    this.dialog.open(CategoryDialog, { data: null }).afterClosed().subscribe(res => {
      if (res) this.api.createCategory(res).subscribe(() => { this.snack.open('Category added', 'Close', { duration: 2000 }); this.loadCategories(); });
    });
  }
  editCategory(c: Category, ev: Event) {
    ev.stopPropagation();
    this.dialog.open(CategoryDialog, { data: c }).afterClosed().subscribe(res => {
      if (res) this.api.updateCategory(c.id, res).subscribe(() => { this.snack.open('Category updated', 'Close', { duration: 2000 }); this.loadCategories(); });
    });
  }
  deleteCategory(c: Category, ev: Event) {
    ev.stopPropagation();
    this.confirm(`Delete category "${c.name}"? This removes its sub-categories and items.`).subscribe(ok => {
      if (ok) this.api.deleteCategory(c.id).subscribe(() => {
        if (this.selected()?.id === c.id) { this.selected.set(null); this.subCategories.set([]); }
        this.snack.open('Category deleted', 'Close', { duration: 2000 });
        this.loadCategories();
      });
    });
  }

  addSub() {
    const sel = this.selected();
    this.dialog.open(SubCategoryDialog, {
      data: { categories: this.categories(), subCategory: null, defaultCategoryId: sel?.id }
    }).afterClosed().subscribe(res => {
      if (res) this.api.createSubCategory(res).subscribe(() => { this.snack.open('Sub-category added', 'Close', { duration: 2000 }); this.loadCategories(); });
    });
  }
  editSub(s: SubCategory) {
    this.dialog.open(SubCategoryDialog, {
      data: { categories: this.categories(), subCategory: s }
    }).afterClosed().subscribe(res => {
      if (res) this.api.updateSubCategory(s.id, res).subscribe(() => { this.snack.open('Sub-category updated', 'Close', { duration: 2000 }); this.loadCategories(); });
    });
  }
  deleteSub(s: SubCategory) {
    this.confirm(`Delete sub-category "${s.name}"?`).subscribe(ok => {
      if (ok) this.api.deleteSubCategory(s.id).subscribe(() => { this.snack.open('Sub-category deleted', 'Close', { duration: 2000 }); this.loadCategories(); });
    });
  }

  private confirm(message: string) {
    return this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message } }).afterClosed();
  }
}
