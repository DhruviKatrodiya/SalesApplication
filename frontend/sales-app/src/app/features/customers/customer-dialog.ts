import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { Customer } from '../../core/models';

@Component({
  selector: 'app-customer-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Edit' : 'Add' }} Customer</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="fill" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('required')) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" />
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Address</mat-label>
          <textarea matInput formControlName="address" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `.dialog-form { display:flex; flex-direction:column; min-width:360px; } .full{width:100%;}`
})
export class CustomerDialog {
  private fb = inject(FormBuilder);
  ref = inject(MatDialogRef<CustomerDialog>);
  data = inject<Customer | null>(MAT_DIALOG_DATA);

  form = this.fb.nonNullable.group({
    name: [this.data?.name ?? '', Validators.required],
    phone: [this.data?.phone ?? ''],
    email: [this.data?.email ?? ''],
    address: [this.data?.address ?? '']
  });

  save() {
    if (this.form.invalid) return;
    this.ref.close(this.form.getRawValue());
  }
}
