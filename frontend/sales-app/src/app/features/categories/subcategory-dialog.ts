import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { Category, SubCategory } from '../../core/models';

interface DialogData { categories: Category[]; subCategory: SubCategory | null; defaultCategoryId?: number; }

@Component({
  selector: 'app-subcategory-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.subCategory ? 'Edit' : 'Add' }} Sub-Category</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="fill" class="full">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryId" required>
            @for (c of data.categories; track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
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
  styles: `.dialog-form { display:flex; flex-direction:column; min-width:340px; } .full{width:100%;}`
})
export class SubCategoryDialog {
  private fb = inject(FormBuilder);
  ref = inject(MatDialogRef<SubCategoryDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  form = this.fb.nonNullable.group({
    categoryId: [this.data.subCategory?.categoryId ?? this.data.defaultCategoryId ?? 0, Validators.required],
    name: [this.data.subCategory?.name ?? '', Validators.required],
    description: [this.data.subCategory?.description ?? '']
  });

  save() {
    if (this.form.invalid) return;
    this.ref.close(this.form.getRawValue());
  }
}
