import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { Item, SubCategory } from '../../core/models';

interface DialogData { subCategories: SubCategory[]; item: Item | null; }

@Component({
  selector: 'app-item-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.item ? 'Edit' : 'Add' }} Item</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="fill" class="full">
          <mat-label>Sub-Category</mat-label>
          <mat-select formControlName="subCategoryId" required>
            @for (s of data.subCategories; track s.id) {
              <mat-option [value]="s.id">{{ s.categoryName }} / {{ s.name }}</mat-option>
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
  `
})
export class ItemDialog {
  private fb = inject(FormBuilder);
  ref = inject(MatDialogRef<ItemDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

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
