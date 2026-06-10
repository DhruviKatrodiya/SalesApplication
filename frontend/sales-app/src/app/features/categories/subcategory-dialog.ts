import { Component, ElementRef, inject, signal, computed, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Category, SubCategory } from '../../core/models';

interface DialogData { categories: Category[]; subCategory: SubCategory | null; defaultCategoryId?: number; }

@Component({
  selector: 'app-subcategory-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.subCategory ? 'Edit' : 'Add' }} Sub-Category</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="fill" class="full">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryId" required
                      (opened)="onSelectOpened()" (closed)="categorySearch.set('')">
            <div class="select-search">
              <mat-icon class="select-search-icon">search</mat-icon>
              <input #categorySearchInput type="text" class="select-search-input" placeholder="Search category…"
                     [value]="categorySearch()"
                     (input)="categorySearch.set($any($event.target).value)"
                     (keydown)="$event.stopPropagation()"
                     (click)="$event.stopPropagation()" />
            </div>
            @for (c of filteredCategories(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
            @if (!filteredCategories().length) {
              <div class="select-empty">No categories found</div>
            }
          </mat-select>
          @if (form.controls.categoryId.hasError('required')) { <mat-error>Category is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('required')) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `
    .dialog-form { display:flex; flex-direction:column; min-width:340px; }
    .full{width:100%;}
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
  `
})
export class SubCategoryDialog {
  private fb = inject(FormBuilder);
  ref = inject(MatDialogRef<SubCategoryDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  private categorySearchInput = viewChild<ElementRef<HTMLInputElement>>('categorySearchInput');
  readonly categorySearch = signal('');
  readonly filteredCategories = computed(() => {
    const q = this.categorySearch().toLowerCase().trim();
    const list = this.data.categories;
    return q ? list.filter(c => c.name.toLowerCase().includes(q)) : list;
  });

  form = this.fb.nonNullable.group({
    categoryId: [this.data.subCategory?.categoryId ?? this.data.defaultCategoryId ?? 0, Validators.required],
    name: [this.data.subCategory?.name ?? '', Validators.required],
    description: [this.data.subCategory?.description ?? '']
  });

  onSelectOpened() {
    // Focus the search box once the overlay panel has rendered.
    setTimeout(() => this.categorySearchInput()?.nativeElement.focus());
  }

  save() {
    if (this.form.invalid) return;
    this.ref.close(this.form.getRawValue());
  }
}
