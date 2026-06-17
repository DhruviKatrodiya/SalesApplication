import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { Dispatch } from '../../core/models';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-dispatch-items-dialog',
  imports: [DatePipe, MatDialogModule, MatTableModule, MatButtonModule, MatIconModule, MatPaginatorModule],
  template: `
    <h2 mat-dialog-title class="title-row">
      Dispatch Items
      <span class="spacer"></span>
      <button mat-icon-button mat-dialog-close aria-label="Close"><mat-icon>close</mat-icon></button>
    </h2>
    <mat-dialog-content>
      <p class="sub">{{ data.truckLabel }} — {{ data.dispatchDate | date:'dd-MM-yyyy' }}</p>
      @if (data.notes) { <p class="notes">Notes: {{ data.notes }}</p> }

      <table mat-table [dataSource]="pager.paged()" class="full">
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let it">{{ it.itemName }}</td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>Qty</th>
          <td mat-cell *matCellDef="let it">{{ it.quantity }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (!data.items.length) { <div class="empty">No items in this dispatch.</div> }
      <mat-paginator [length]="pager.total()" [pageSize]="pager.pageSize()" [pageIndex]="pager.pageIndex()"
        [pageSizeOptions]="pageSizeOptions" (page)="pager.onPage($event)" showFirstLastButtons />
    </mat-dialog-content>
  `,
  styles: `
    .title-row { display:flex; align-items:center; }
    .spacer { flex:1 1 auto; }
    .sub { color: var(--mat-sys-on-surface-variant); margin: 0 0 4px; font-weight: 600; }
    .notes { color: var(--mat-sys-on-surface-variant); margin: 0 0 8px; }
    table { width: 100%; }
    .empty { text-align:center; padding:16px; color: var(--mat-sys-on-surface-variant); }
  `
})
export class DispatchItemsDialog {
  data = inject<Dispatch>(MAT_DIALOG_DATA);
  columns = ['item', 'qty'];
  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createPager(() => this.data.items);
}
