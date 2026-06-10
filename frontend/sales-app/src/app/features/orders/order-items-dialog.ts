import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Order, ReceivedStatus, ReceivedStatusLabels } from '../../core/models';

@Component({
  selector: 'app-order-items-dialog',
  imports: [
    FormsModule, CurrencyPipe, MatDialogModule, MatTableModule,
    MatFormFieldModule, MatSelectModule, MatButtonModule, MatIconModule
  ],
  template: `
    <h2 mat-dialog-title class="title-row">
      Items — mark received status
      <span class="spacer"></span>
      <button mat-icon-button [mat-dialog-close]="changed" aria-label="Close"><mat-icon>close</mat-icon></button>
    </h2>
    <mat-dialog-content>
      <p class="sub">Order {{ order().orderNumber }} — {{ order().customerName }}</p>
      @if (order().notes) { <p class="notes">Notes: {{ order().notes }}</p> }

      <table mat-table [dataSource]="order().items" class="full">
        <ng-container matColumnDef="item"><th mat-header-cell *matHeaderCellDef>Item</th><td mat-cell *matCellDef="let it">{{ it.itemName }}</td></ng-container>
        <ng-container matColumnDef="qty"><th mat-header-cell *matHeaderCellDef>Qty</th><td mat-cell *matCellDef="let it">{{ it.quantity }}</td></ng-container>
        <ng-container matColumnDef="price"><th mat-header-cell *matHeaderCellDef>Unit Price</th><td mat-cell *matCellDef="let it">{{ it.unitPrice | currency:'INR' }}</td></ng-container>
        <ng-container matColumnDef="line"><th mat-header-cell *matHeaderCellDef>Line Total</th><td mat-cell *matCellDef="let it">{{ it.lineTotal | currency:'INR' }}</td></ng-container>
        <ng-container matColumnDef="received">
          <th mat-header-cell *matHeaderCellDef>Received</th>
          <td mat-cell *matCellDef="let it">
            <mat-form-field appearance="fill" subscriptSizing="dynamic" style="width:150px">
              <mat-select [ngModel]="it.receivedStatus" (ngModelChange)="changeReceived(it.id, $event)">
                @for (r of receivedOptions; track r.value) { <mat-option [value]="r.value">{{ r.label }}</mat-option> }
              </mat-select>
            </mat-form-field>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (!order().items.length) { <div class="empty">No items on this order.</div> }
    </mat-dialog-content>
  `,
  styles: `
    .title-row { display:flex; align-items:center; }
    .spacer { flex:1 1 auto; }
    .sub { color: var(--mat-sys-on-surface-variant); margin: 0 0 4px; font-weight: 600; }
    .notes { color: var(--mat-sys-on-surface-variant); margin: 0 0 8px; }
    table { width: 100%; }
  `
})
export class OrderItemsDialog {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);

  order = signal<Order>(inject<Order>(MAT_DIALOG_DATA));
  changed = false;

  columns = ['item', 'qty', 'price', 'line', 'received'];
  receivedOptions = Object.entries(ReceivedStatusLabels).map(([v, l]) => ({ value: +v, label: l }));

  changeReceived(orderItemId: number, status: ReceivedStatus) {
    this.api.updateReceivedStatus(this.order().id, orderItemId, status).subscribe(updated => {
      this.order.set(updated);          // reflect recomputed order
      this.changed = true;              // parent should refresh the list on close
      this.snack.open('Item status updated', 'Close', { duration: 1500 });
    });
  }
}
