import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { OrderStatus, OrderStatusLabels } from '../../core/models';

interface DialogData { orderNumber: string; status: OrderStatus; }

@Component({
  selector: 'app-order-status-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Update Status — {{ data.orderNumber }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="fill" class="full">
        <mat-label>Delivery Status</mat-label>
        <mat-select [ngModel]="status()" (ngModelChange)="status.set($event)">
          @for (s of options; track s.value) { <mat-option [value]="s.value">{{ s.label }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="fill" class="full">
        <mat-label>Note (optional)</mat-label>
        <input matInput maxlength="500" [(ngModel)]="note" placeholder="e.g. left with security guard" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `.full { width: 100%; min-width: 280px; }`
})
export class OrderStatusDialog {
  private ref = inject(MatDialogRef<OrderStatusDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  status = signal<OrderStatus>(this.data.status);
  options = Object.entries(OrderStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  note = '';

  save() { this.ref.close({ status: this.status(), note: this.note.trim() || undefined }); }
}
