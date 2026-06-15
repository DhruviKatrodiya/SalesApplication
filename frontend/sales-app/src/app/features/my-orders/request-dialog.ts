import { Component, ElementRef, inject, signal, computed, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { Item, StockRequest } from '../../core/models';

interface DialogData { items: Item[]; request: StockRequest | null; }
interface Line { itemId: number; itemName: string; quantity: number; currentStock: number; unitPrice: number; }

@Component({
  selector: 'app-request-dialog',
  imports: [
    FormsModule, CurrencyPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatTableModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.request ? 'Edit' : 'New' }} Inventory Request</h2>
    <mat-dialog-content>
      <div class="dialog-form">
        <div class="row">
          <mat-form-field appearance="fill" style="flex:1; min-width:240px">
            <mat-label>Item</mat-label>
            <mat-select [ngModel]="selectedItemId()" (ngModelChange)="selectedItemId.set($event)"
                        (opened)="onOpen()" (closed)="search.set('')">
              <div class="select-search">
                <mat-icon class="select-search-icon">search</mat-icon>
                <input #searchInput type="text" class="select-search-input" placeholder="Search item…"
                       [value]="search()"
                       (input)="search.set($any($event.target).value)"
                       (keydown)="$event.stopPropagation()" (click)="$event.stopPropagation()" />
              </div>
              @for (i of filteredItems(); track i.id) {
                <mat-option [value]="i.id" [disabled]="i.stockQuantity <= 0">
                  {{ i.name }} (stock: {{ i.stockQuantity }}){{ i.stockQuantity <= 0 ? ' — out of stock' : '' }}
                </mat-option>
              }
              @if (!filteredItems().length) { <div class="select-empty">No items found</div> }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="fill" style="width:120px">
            <mat-label>Qty needed</mat-label>
            <input matInput type="number" min="1" [ngModel]="qty()" (ngModelChange)="qty.set($event)" />
          </mat-form-field>
          <button mat-stroked-button color="primary" (click)="addLine()"><mat-icon>add</mat-icon> Add</button>
        </div>

        <table mat-table [dataSource]="lines()" [trackBy]="trackByItemId" class="full">
          <ng-container matColumnDef="item"><th mat-header-cell *matHeaderCellDef>Item</th><td mat-cell *matCellDef="let l">{{ l.itemName }}</td></ng-container>
          <ng-container matColumnDef="stock"><th mat-header-cell *matHeaderCellDef>Stock</th><td mat-cell *matCellDef="let l">{{ l.currentStock }}</td></ng-container>
          <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let l">
              <input type="number" min="0" [ngModel]="l.unitPrice" (ngModelChange)="updatePrice(l.itemId, $event)" style="width:90px" />
            </td>
          </ng-container>
          <ng-container matColumnDef="qty"><th mat-header-cell *matHeaderCellDef>Qty Needed</th>
            <td mat-cell *matCellDef="let l">
              <input type="number" min="1" [ngModel]="l.quantity" (ngModelChange)="updateQty(l.itemId, $event)" style="width:70px" />
            </td>
          </ng-container>
          <ng-container matColumnDef="total"><th mat-header-cell *matHeaderCellDef>Total</th><td mat-cell *matCellDef="let l">{{ (l.quantity * l.unitPrice) | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let l"><button mat-icon-button (click)="removeLine(l.itemId)"><mat-icon>close</mat-icon></button></td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!lines().length) { <div class="req-msg">Add at least one item to request.</div> }
        @if (lines().length) { <div class="total-row">Total: {{ total() | currency:'INR' }}</div> }

        <mat-form-field appearance="fill" class="full mt-8">
          <mat-label>Notes</mat-label>
          <input matInput [ngModel]="notes()" (ngModelChange)="notes.set($event)" />
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!lines().length" (click)="save()">Save Request</button>
    </mat-dialog-actions>
  `,
  styles: `
    .dialog-form { width: 100%; box-sizing: border-box; }
    .full { width: 100%; }
    .mt-8 { margin-top: 8px; }
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-start; }
    .req-msg { color: var(--mat-sys-error); font-size: 0.85em; padding: 8px 4px; }
    .total-row { display:flex; justify-content:flex-end; font-weight:600; margin-top:8px; font-size:1.1em; }
    table { width: 100%; }
    .select-search { position: sticky; top: 0; z-index: 1; display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--mat-sys-surface-container); border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .select-search-icon { color: var(--mat-sys-on-surface-variant); }
    .select-search-input { flex: 1; box-sizing: border-box; padding: 8px 10px; border: 1px solid var(--mat-sys-outline-variant); border-radius: 6px; font: inherit; color: var(--mat-sys-on-surface); background: var(--mat-sys-surface); outline: none; }
    .select-search-input:focus { border-color: var(--mat-sys-primary); }
    .select-empty { padding: 12px 16px; color: var(--mat-sys-on-surface-variant); }
  `
})
export class RequestDialog {
  private ref = inject(MatDialogRef<RequestDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  notes = signal<string>(this.data.request?.notes ?? '');
  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);
  lines = signal<Line[]>(
    (this.data.request?.items ?? []).map(i => ({ itemId: i.itemId, itemName: i.itemName, quantity: i.quantity, currentStock: i.currentStock, unitPrice: i.unitPrice }))
  );

  columns = ['item', 'stock', 'amount', 'qty', 'total', 'actions'];
  total = computed(() => this.lines().reduce((s, l) => s + l.quantity * l.unitPrice, 0));

  // Keep table rows stable across edits so number inputs don't lose focus mid-typing.
  trackByItemId = (_: number, l: Line) => l.itemId;

  private searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
  readonly search = signal('');
  readonly filteredItems = computed(() => {
    const q = this.search().toLowerCase().trim();
    return q ? this.data.items.filter(i => i.name.toLowerCase().includes(q)) : this.data.items;
  });
  onOpen() { setTimeout(() => this.searchInput()?.nativeElement.focus()); }

  addLine() {
    const id = this.selectedItemId();
    const q = Number(this.qty());
    if (!id || q <= 0) return;
    const item = this.data.items.find(i => i.id === id);
    if (!item || item.stockQuantity <= 0) return;   // out-of-stock items can't be requested
    const existing = this.lines().find(l => l.itemId === id);
    if (existing) {
      this.lines.update(ls => ls.map(l => l.itemId === id ? { ...l, quantity: l.quantity + q } : l));
    } else {
      this.lines.update(ls => [...ls, { itemId: id, itemName: item.name, quantity: q, currentStock: item.stockQuantity, unitPrice: item.unitPrice }]);
    }
    this.selectedItemId.set(null);
    this.qty.set(1);
  }

  updateQty(itemId: number, q: number) {
    this.lines.update(ls => ls.map(l => l.itemId === itemId ? { ...l, quantity: Number(q) } : l));
  }
  updatePrice(itemId: number, price: number) {
    this.lines.update(ls => ls.map(l => l.itemId === itemId ? { ...l, unitPrice: Number(price) } : l));
  }
  removeLine(itemId: number) {
    this.lines.update(ls => ls.filter(l => l.itemId !== itemId));
  }

  save() {
    // Exclude any out-of-stock lines from the saved request.
    const items = this.lines().filter(l => l.currentStock > 0);
    if (!items.length) return;
    this.ref.close({
      notes: this.notes(),
      items: items.map(l => ({ itemId: l.itemId, quantity: l.quantity, unitPrice: l.unitPrice }))
    });
  }
}
