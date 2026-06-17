import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Dispatch, Item, Truck } from '../../core/models';

interface EditLine { itemId: number; itemName: string; quantity: number; }

@Component({
  selector: 'app-dispatch-edit-dialog',
  imports: [
    FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatTableModule, MatButtonModule, MatIconModule
  ],
  template: `
    <h2 mat-dialog-title>Edit Dispatch</h2>
    <mat-dialog-content>
      <div class="row">
        <mat-form-field appearance="fill" style="min-width:200px">
          <mat-label>Truck</mat-label>
          <mat-select [ngModel]="truckLabel()" (ngModelChange)="truckLabel.set($event)">
            @for (t of data.trucks; track t.id) { <mat-option [value]="t.name">{{ t.name }}</mat-option> }
            @if (!trucksHasCurrent()) { <mat-option [value]="truckLabel()">{{ truckLabel() }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>

      <div class="row">
        <mat-form-field appearance="fill" style="flex:1; min-width:220px">
          <mat-label>Add item</mat-label>
          <mat-select [ngModel]="selectedItemId()" (ngModelChange)="selectedItemId.set($event)">
            @for (i of data.items; track i.id) {
              <mat-option [value]="i.id" [disabled]="i.stockQuantity <= 0 && !hasLine(i.id)">
                {{ i.name }} (stock: {{ i.stockQuantity }})
              </mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:110px">
          <mat-label>Qty</mat-label>
          <input matInput type="number" min="1" [ngModel]="qty()" (ngModelChange)="qty.set($event)" />
        </mat-form-field>
        <button mat-stroked-button color="primary" (click)="addLine()"><mat-icon>add</mat-icon> Add</button>
      </div>

      <table mat-table [dataSource]="lines()" class="full">
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let l">{{ l.itemName }}</td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>Qty</th>
          <td mat-cell *matCellDef="let l">
            <input class="qty-input" type="number" min="1" [ngModel]="l.quantity" (ngModelChange)="setQty(l, $event)" />
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let l"><button mat-icon-button (click)="removeLine(l)"><mat-icon>close</mat-icon></button></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
      @if (!lines().length) { <div class="empty">No items. Add at least one.</div> }

      <mat-form-field appearance="fill" class="full" style="margin-top:12px">
        <mat-label>Notes</mat-label>
        <input matInput [ngModel]="notes()" (ngModelChange)="notes.set($event)" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!canSave()" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:center; margin-bottom:8px; }
    .full { width:100%; }
    table { width:100%; }
    .qty-input { width:80px; padding:6px 8px; border:1px solid var(--mat-sys-outline-variant); border-radius:6px; font:inherit; }
    .empty { text-align:center; padding:16px; color: var(--mat-sys-on-surface-variant); }
  `
})
export class DispatchEditDialog {
  data = inject<{ dispatch: Dispatch; items: Item[]; trucks: Truck[] }>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<DispatchEditDialog>);

  truckLabel = signal(this.data.dispatch.truckLabel);
  notes = signal(this.data.dispatch.notes ?? '');
  lines = signal<EditLine[]>(this.data.dispatch.items.map(i => ({ itemId: i.itemId, itemName: i.itemName, quantity: i.quantity })));
  selectedItemId = signal<number | null>(null);
  qty = signal<number>(1);
  cols = ['item', 'qty', 'actions'];

  trucksHasCurrent = () => this.data.trucks.some(t => t.name === this.truckLabel());
  hasLine = (itemId: number) => this.lines().some(l => l.itemId === itemId);

  addLine() {
    const id = this.selectedItemId();
    const q = Number(this.qty());
    if (!id || q <= 0) return;
    const item = this.data.items.find(i => i.id === id);
    if (!item) return;
    const existing = this.lines().find(l => l.itemId === id);
    if (existing) this.lines.update(ls => ls.map(l => l.itemId === id ? { ...l, quantity: l.quantity + q } : l));
    else this.lines.update(ls => [...ls, { itemId: id, itemName: item.name, quantity: q }]);
    this.selectedItemId.set(null);
    this.qty.set(1);
  }
  setQty(line: EditLine, v: number) {
    const q = Number(v);
    this.lines.update(ls => ls.map(l => l.itemId === line.itemId ? { ...l, quantity: q } : l));
  }
  removeLine(line: EditLine) { this.lines.update(ls => ls.filter(l => l.itemId !== line.itemId)); }

  canSave = () => !!this.truckLabel() && this.lines().length > 0 && this.lines().every(l => l.quantity > 0);

  save() {
    this.ref.close({
      truckLabel: this.truckLabel(),
      notes: this.notes() || undefined,
      items: this.lines().map(l => ({ itemId: l.itemId, quantity: Number(l.quantity) }))
    });
  }
}
