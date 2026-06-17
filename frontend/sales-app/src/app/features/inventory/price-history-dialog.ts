import { Component, inject, signal, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { ApiService } from '../../core/api.service';
import { ItemPriceHistory, InventoryMovement } from '../../core/models';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-price-history-dialog',
  imports: [CurrencyPipe, DatePipe, MatDialogModule, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule],
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

        <h3>Batches (FIFO — oldest consumed first)</h3>
        <div class="table-scroll">
        <table mat-table [dataSource]="h.batches" class="full">
          <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Received</th><td mat-cell *matCellDef="let b">{{ b.createdAt | date:'dd-MM-yyyy' }}</td></ng-container>
          <ng-container matColumnDef="received"><th mat-header-cell *matHeaderCellDef>Received</th><td mat-cell *matCellDef="let b">{{ b.received }}</td></ng-container>
          <ng-container matColumnDef="remaining"><th mat-header-cell *matHeaderCellDef>Remaining</th><td mat-cell *matCellDef="let b">{{ b.remaining }}</td></ng-container>
          <ng-container matColumnDef="price"><th mat-header-cell *matHeaderCellDef>Purchase Price</th><td mat-cell *matCellDef="let b">{{ b.purchasePrice | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="value"><th mat-header-cell *matHeaderCellDef>Remaining Value</th><td mat-cell *matCellDef="let b">{{ (b.remaining * b.purchasePrice) | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="source"><th mat-header-cell *matHeaderCellDef>Source</th><td mat-cell *matCellDef="let b">{{ b.sourceRequestNumber || 'Opening' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        </div>
        @if (!h.batches.length) { <div class="empty">No batches yet — fulfil an inventory request to record price-tagged stock.</div> }
        @if (batchPager.total() > 0) {
          <mat-paginator [length]="batchPager.total()" [pageSize]="batchPager.pageSize()" [pageIndex]="batchPager.pageIndex()"
            [pageSizeOptions]="pageSizeOptions" (page)="batchPager.onPage($event)" showFirstLastButtons />
        }

        <h3>Stock Movements (audit)</h3>
        <div class="table-scroll">
        <table mat-table [dataSource]="movements()" class="full">
          <ng-container matColumnDef="mdate"><th mat-header-cell *matHeaderCellDef>Date</th><td mat-cell *matCellDef="let m">{{ m.createdAt | date:'dd-MM-yyyy' }}</td></ng-container>
          <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Type</th><td mat-cell *matCellDef="let m"><span [class]="m.type === 'IN' ? 'chip chip-ok' : 'chip chip-remaining'">{{ m.type }}</span>{{ m.reversed ? ' (reversed)' : '' }}</td></ng-container>
          <ng-container matColumnDef="mqty"><th mat-header-cell *matHeaderCellDef>Qty</th><td mat-cell *matCellDef="let m">{{ m.quantity }}</td></ng-container>
          <ng-container matColumnDef="mcost"><th mat-header-cell *matHeaderCellDef>Unit Cost</th><td mat-cell *matCellDef="let m">{{ m.unitCost | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="mtotal"><th mat-header-cell *matHeaderCellDef>Total</th><td mat-cell *matCellDef="let m">{{ m.totalCost | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="msource"><th mat-header-cell *matHeaderCellDef>Reference</th><td mat-cell *matCellDef="let m">{{ m.source || m.refType }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="moveColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: moveColumns"></tr>
        </table>
        </div>
        @if (!movements().length) { <div class="empty">No movements recorded yet.</div> }
        @if (movePager.total() > 0) {
          <mat-paginator [length]="movePager.total()" [pageSize]="movePager.pageSize()" [pageIndex]="movePager.pageIndex()"
            [pageSizeOptions]="pageSizeOptions" (page)="movePager.onPage($event)" showFirstLastButtons />
        }
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
    /* Keep dates and reference numbers on a single line; the table scrolls
       horizontally inside .table-scroll if it gets too wide for the dialog. */
    th.mat-mdc-header-cell, td.mat-mdc-cell { white-space: nowrap; padding-right: 16px; }
    .note { color: var(--mat-sys-on-surface-variant); font-size:0.85em; margin-top:8px; }
  `
})
export class PriceHistoryDialog implements OnInit {
  private api = inject(ApiService);
  data = inject<{ id: number; name: string }>(MAT_DIALOG_DATA);
  history = signal<ItemPriceHistory | null>(null);
  movements = signal<InventoryMovement[]>([]);
  columns = ['date', 'received', 'remaining', 'price', 'value', 'source'];
  moveColumns = ['mdate', 'type', 'mqty', 'mcost', 'mtotal', 'msource'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  batchPager = createServerPager(() => this.loadBatches());
  movePager = createServerPager(() => this.loadMovements());

  ngOnInit() {
    this.loadBatches();
    this.loadMovements();
  }

  loadBatches() {
    this.api.getItemPriceHistory(this.data.id, { page: this.batchPager.pageIndex() + 1, pageSize: this.batchPager.pageSize() })
      .subscribe(h => { this.history.set(h); this.batchPager.total.set(h.batchesTotal); });
  }

  loadMovements() {
    this.api.getItemMovements(this.data.id, { page: this.movePager.pageIndex() + 1, pageSize: this.movePager.pageSize() })
      .subscribe(res => { this.movements.set(res.items); this.movePager.total.set(res.total); });
  }
}
