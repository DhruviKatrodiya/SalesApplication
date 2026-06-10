import { Component, ElementRef, inject, signal, computed, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Item, SubCategory } from '../../core/models';

interface DialogData { subCategories: SubCategory[]; item: Item | null; }

@Component({
  selector: 'app-item-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.item ? 'Edit' : 'Add' }} Item</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="fill" class="full">
          <mat-label>Sub-Category</mat-label>
          <mat-select formControlName="subCategoryId" required
                      (opened)="onSelectOpened()" (closed)="subCategorySearch.set('')">
            <div class="select-search">
              <mat-icon class="select-search-icon">search</mat-icon>
              <input #subCategorySearchInput type="text" class="select-search-input" placeholder="Search sub-category…"
                     [value]="subCategorySearch()"
                     (input)="subCategorySearch.set($any($event.target).value)"
                     (keydown)="$event.stopPropagation()"
                     (click)="$event.stopPropagation()" />
            </div>
            @for (s of filteredSubCategories(); track s.id) {
              <mat-option [value]="s.id">{{ s.categoryName }} / {{ s.name }}</mat-option>
            }
            @if (!filteredSubCategories().length) {
              <div class="select-empty">No sub-categories found</div>
            }
          </mat-select>
          @if (form.controls.subCategoryId.hasError('required')) { <mat-error>Sub-category is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('required')) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <div class="grid2">
          <mat-form-field appearance="fill">
            <mat-label>SKU</mat-label>
            <input matInput formControlName="sku" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>Unit</mat-label>
            <input matInput formControlName="unit" placeholder="pcs, box, kg" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>Stock Quantity</mat-label>
            <input matInput type="number" formControlName="stockQuantity" required />
            @if (form.controls.stockQuantity.hasError('required')) { <mat-error>Required</mat-error> }
            @if (form.controls.stockQuantity.hasError('min')) { <mat-error>Cannot be negative</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>Unit Price</mat-label>
            <input matInput type="number" formControlName="unitPrice" required />
            @if (form.controls.unitPrice.hasError('required')) { <mat-error>Required</mat-error> }
            @if (form.controls.unitPrice.hasError('min')) { <mat-error>Cannot be negative</mat-error> }
          </mat-form-field>
        </div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `
    .dialog-form { display:flex; flex-direction:column; min-width:380px; }
    .full{width:100%;}
    .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
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
export class ItemDialog {
  private fb = inject(FormBuilder);
  ref = inject(MatDialogRef<ItemDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  private subCategorySearchInput = viewChild<ElementRef<HTMLInputElement>>('subCategorySearchInput');
  readonly subCategorySearch = signal('');
  readonly filteredSubCategories = computed(() => {
    const q = this.subCategorySearch().toLowerCase().trim();
    const list = this.data.subCategories;
    return q
      ? list.filter(s => `${s.categoryName} / ${s.name}`.toLowerCase().includes(q))
      : list;
  });

  onSelectOpened() {
    setTimeout(() => this.subCategorySearchInput()?.nativeElement.focus());
  }

  form = this.fb.nonNullable.group({
    subCategoryId: [this.data.item?.subCategoryId ?? 0, Validators.required],
    name: [this.data.item?.name ?? '', Validators.required],
    sku: [this.data.item?.sku ?? ''],
    unit: [this.data.item?.unit ?? ''],
    stockQuantity: [this.data.item?.stockQuantity ?? 0, [Validators.required, Validators.min(0)]],
    unitPrice: [this.data.item?.unitPrice ?? 0, [Validators.required, Validators.min(0)]]
  });

  save() {
    if (this.form.invalid) return;
    this.ref.close(this.form.getRawValue());
  }
}
