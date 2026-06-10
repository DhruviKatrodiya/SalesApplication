import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { CustomerSearchResult, OrderStatusLabels, PaymentStatusLabels } from '../../core/models';

@Component({
  selector: 'app-customer-details-dialog',
  imports: [
    CurrencyPipe, MatDialogModule, MatCardModule, MatTableModule, MatButtonModule, MatIconModule, MatExpansionModule
  ],
  template: `
    <h2 mat-dialog-title class="title-row">
      {{ d.customer.name }}
      <span class="spacer"></span>
      <button mat-icon-button mat-dialog-close aria-label="Close"><mat-icon>close</mat-icon></button>
    </h2>
    <mat-dialog-content>
      <div class="info-grid">
        <div><span class="k">Phone:</span> {{ d.customer.phone || '-' }}</div>
        <div><span class="k">Email:</span> {{ d.customer.email || '-' }}</div>
        <div><span class="k">Address:</span> {{ d.customer.address || '-' }}</div>
        <div><span class="k">Route:</span> {{ d.customer.routeName || '-' }}</div>
        <div><span class="k">Overall Payment:</span>
          <span class="chip"
            [class.chip-paid]="d.overallPaymentStatus === 'Paid'"
            [class.chip-advance]="d.overallPaymentStatus === 'Advance'"
            [class.chip-pending]="d.overallPaymentStatus === 'Pending'">{{ d.overallPaymentStatus }}</span>
        </div>
      </div>

      <div class="cards-grid mt-16">
        <mat-card class="stat-card"><mat-card-content><div class="value">{{ d.totalOrdered | currency:'INR':'symbol':'1.0-0' }}</div><div class="label">Total Ordered</div></mat-card-content></mat-card>
        <mat-card class="stat-card"><mat-card-content><div class="value">{{ d.totalPaid | currency:'INR':'symbol':'1.0-0' }}</div><div class="label">Total Paid</div></mat-card-content></mat-card>
        <mat-card class="stat-card"><mat-card-content><div class="value">{{ d.totalRemaining | currency:'INR':'symbol':'1.0-0' }}</div><div class="label">Remaining</div></mat-card-content></mat-card>
        <mat-card class="stat-card"><mat-card-content><div class="value">{{ d.pendingOrders }}</div><div class="label">Pending Orders</div></mat-card-content></mat-card>
        <mat-card class="stat-card"><mat-card-content><div class="value">{{ d.deliveredOrders }}</div><div class="label">Delivered Orders</div></mat-card-content></mat-card>
      </div>

      <h3 class="mt-16">Orders &amp; Items</h3>
      <mat-accordion>
        @for (o of d.orders; track o.id) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>{{ o.orderNumber }}</mat-panel-title>
              <mat-panel-description>
                <span [class]="statusChipClass(o.status)">{{ orderStatusLabel[o.status] }}</span>
                <span [class]="payChipClass(o.paymentStatus)" style="margin-left:8px">{{ paymentStatusLabel[o.paymentStatus] }}</span>
                <span style="margin-left:auto">{{ o.totalAmount | currency:'INR' }}</span>
              </mat-panel-description>
            </mat-expansion-panel-header>
            <table mat-table [dataSource]="o.items" class="full">
              <ng-container matColumnDef="item"><th mat-header-cell *matHeaderCellDef>Item</th><td mat-cell *matCellDef="let it">{{ it.itemName }}</td></ng-container>
              <ng-container matColumnDef="qty"><th mat-header-cell *matHeaderCellDef>Qty</th><td mat-cell *matCellDef="let it">{{ it.quantity }}</td></ng-container>
              <ng-container matColumnDef="price"><th mat-header-cell *matHeaderCellDef>Price</th><td mat-cell *matCellDef="let it">{{ it.unitPrice | currency:'INR' }}</td></ng-container>
              <ng-container matColumnDef="line"><th mat-header-cell *matHeaderCellDef>Total</th><td mat-cell *matCellDef="let it">{{ it.lineTotal | currency:'INR' }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="['item','qty','price','line']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['item','qty','price','line']"></tr>
            </table>
          </mat-expansion-panel>
        }
      </mat-accordion>
      @if (!d.orders.length) { <div class="empty">No orders for this customer.</div> }
    </mat-dialog-content>
  `,
  styles: `
    .title-row { display: flex; align-items: center; }
    .spacer { flex: 1 1 auto; }
    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 8px;
      margin-top: 8px;
    }
    .info-grid .k { color: var(--mat-sys-on-surface-variant); font-weight: 600; margin-right: 4px; }
    h3 { margin: 16px 0 8px; }
    table { width: 100%; }
  `
})
export class CustomerDetailsDialog {
  d = inject<CustomerSearchResult>(MAT_DIALOG_DATA);

  orderStatusLabel = OrderStatusLabels;
  paymentStatusLabel = PaymentStatusLabels;

  statusChipClass(status: number): string {
    return 'chip chip-' + (this.orderStatusLabel[status] ?? '').toLowerCase();
  }
  payChipClass(status: number): string {
    return 'chip chip-' + (this.paymentStatusLabel[status] ?? '').toLowerCase();
  }
}
