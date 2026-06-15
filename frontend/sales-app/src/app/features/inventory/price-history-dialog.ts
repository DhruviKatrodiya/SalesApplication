import { Component, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/api.service';
import { ItemPriceHistory } from '../../core/models';

@Component({
  selector: 'app-price-history-dialog',
  imports: [CurrencyPipe, DatePipe, MatDialogModule, MatTableModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="title-row">
      Price History — {{ data.name }}
      <span class="spacer"></span>
      <button mat-icon-button mat-dialog-close aria-label="Close"><mat-icon>close</mat-icon></button>
    </h2>
    <mat-dialog-content>
      @if (history(); as h) {
        <div class="summary">
          <div><span class="lbl">Stock Qty</span><span class="val">{{ h.stockQuantity }}</span></div>
          <div><span class="lbl">Oldest Price</span><span class="val">{{ h.oldestPrice | currency:'INR' }}</span></div>
          <div><span class="lbl">Latest Price</span><span class="val">{{ h.latestPrice | currency:'INR' }}</span></div>
          <div><span class="lbl">Avg Cost</span><span class="val">{{ h.avgCost | currency:'INR' }}</span></div>
          <div><span class="lbl">Stock Value</span><span class="val">{{ h.stockValue | currency:'INR' }}</span></div>
        </div>

        <h3>Batches (newest first)</h3>
        <table mat-table [dataSource]="h.batches" class="full">
          <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Received</th><td mat-cell *matCellDef="let b">{{ b.createdAt | date:'dd-MM-yyyy' }}</td></ng-container>
          <ng-container matColumnDef="qty"><th mat-header-cell *matHeaderCellDef>Qty</th><td mat-cell *matCellDef="let b">{{ b.quantity }}</td></ng-container>
          <ng-container matColumnDef="price"><th mat-header-cell *matHeaderCellDef>Purchase Price</th><td mat-cell *matCellDef="let b">{{ b.purchasePrice | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="value"><th mat-header-cell *matHeaderCellDef>Batch Value</th><td mat-cell *matCellDef="let b">{{ (b.quantity * b.purchasePrice) | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="source"><th mat-header-cell *matHeaderCellDef>Source</th><td mat-cell *matCellDef="let b">{{ b.sourceRequestNumber || 'Opening' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!h.batches.length) { <div class="empty">No price history yet — fulfil an inventory request to record batches.</div> }
        <p class="note">Stock value uses weighted-average cost. Exact FIFO valuation arrives with consumption tracking (next phase).</p>
      } @else {
        <div class="empty">Loading…</div>
      }
    </mat-dialog-content>
  `,
  styles: `
    .title-row { display:flex; align-items:center; }
    .spacer { flex:1 1 auto; }
    .summary { display:flex; flex-wrap:wrap; gap:24px; margin:8px 0 16px; padding:12px 16px;
      background: var(--mat-sys-surface-container); border-radius:8px; }
    .summary > div { display:flex; flex-direction:column; }
    .summary .lbl { font-size:0.8em; color: var(--mat-sys-on-surface-variant); }
    .summary .val { font-weight:600; font-size:1.1em; }
    h3 { margin: 12px 0 8px; }
    table { width:100%; }
    .note { color: var(--mat-sys-on-surface-variant); font-size:0.85em; margin-top:8px; }
  `
})
export class PriceHistoryDialog implements OnInit {
  private api = inject(ApiService);
  data = inject<{ id: number; name: string }>(MAT_DIALOG_DATA);
  history = signal<ItemPriceHistory | null>(null);
  columns = ['date', 'qty', 'price', 'value', 'source'];

  ngOnInit() {
    this.api.getItemPriceHistory(this.data.id).subscribe(h => this.history.set(h));
  }
}
