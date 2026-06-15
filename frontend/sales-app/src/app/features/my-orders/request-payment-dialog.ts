import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/api.service';
import { StockRequest, StockRequestPayment, PaymentStatusLabels } from '../../core/models';

@Component({
  selector: 'app-request-payment-dialog',
  imports: [
    FormsModule, CurrencyPipe, DatePipe, MatDialogModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatTableModule
  ],
  template: `
    <h2 mat-dialog-title>Payments — {{ request().requestNumber }}</h2>
    <mat-dialog-content>
      <div class="dialog-form">
        <div class="summary">
          <div>Total: <span>{{ request().totalAmount | currency:'INR' }}</span></div>
          <div>Paid: <span>{{ request().paidAmount | currency:'INR' }}</span></div>
          <div>Remaining: <span>{{ request().remainingAmount | currency:'INR' }}</span></div>
          <div>Status:
            <span class="chip"
              [class.chip-paid]="request().paymentStatus === 2"
              [class.chip-advance]="request().paymentStatus === 1"
              [class.chip-pending]="request().paymentStatus === 0">{{ payLabel[request().paymentStatus] }}</span>
          </div>
        </div>

        <div class="row">
          <mat-form-field appearance="fill" subscriptSizing="dynamic" style="width:170px">
            <mat-label>Amount</mat-label>
            <input matInput type="number" min="1" [ngModel]="amount()" (ngModelChange)="amount.set($event)" #amtCtrl="ngModel" required />
            @if (amtCtrl.touched && amtCtrl.hasError('required')) { <mat-error>Amount is required</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="fill" style="width:140px">
            <mat-label>Method</mat-label>
            <mat-select [ngModel]="method()" (ngModelChange)="method.set($event)">
              <mat-option value="Cash">Cash</mat-option>
              <mat-option value="UPI">UPI</mat-option>
              <mat-option value="Bank">Bank</mat-option>
              <mat-option value="Cheque">Cheque</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="fill" style="flex:1; min-width:140px">
            <mat-label>Note</mat-label>
            <input matInput [ngModel]="note()" (ngModelChange)="note.set($event)" />
          </mat-form-field>
        </div>

        @if (advance() > 0) {
          <div class="advance-banner">
            <mat-icon>account_balance_wallet</mat-icon>
            Advance balance available: <span>{{ advance() | currency:'INR' }}</span>
            @if (request().remainingAmount > 0) {
              <button mat-flat-button color="primary" class="apply-btn" (click)="applyAdvance()">
                <mat-icon>redeem</mat-icon> Apply to this request
              </button>
            }
          </div>
        }

        <div class="actions-row">
          <button mat-flat-button color="primary" (click)="addPayment()"><mat-icon>add</mat-icon> Add Payment</button>
          <button mat-stroked-button color="primary" (click)="settle()" [disabled]="request().remainingAmount <= 0">
            <mat-icon>done_all</mat-icon> Mark Fully Settled
          </button>
        </div>

        <table mat-table [dataSource]="payments()" class="full">
          <ng-container matColumnDef="srNo"><th mat-header-cell *matHeaderCellDef>Sr. No.</th><td mat-cell *matCellDef="let p; let i = index">{{ i + 1 }}</td></ng-container>
          <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Date</th><td mat-cell *matCellDef="let p">{{ p.paymentDate | date:'dd-MM-yyyy' }}</td></ng-container>
          <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let p">{{ p.amount | currency:'INR' }}</td></ng-container>
          <ng-container matColumnDef="method"><th mat-header-cell *matHeaderCellDef>Method</th><td mat-cell *matCellDef="let p">{{ p.method }}</td></ng-container>
          <ng-container matColumnDef="note"><th mat-header-cell *matHeaderCellDef>Note</th><td mat-cell *matCellDef="let p">{{ p.note }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let p">@if (p.method !== 'Advance' && p.method !== 'AdvanceTransfer') { <button mat-icon-button (click)="deletePayment(p)"><mat-icon>delete</mat-icon></button> }</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!payments().length) { <div class="empty">No payments recorded yet.</div> }
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" (click)="close()">Done</button>
    </mat-dialog-actions>
  `,
  styles: `
    .dialog-form { min-width: 520px; }
    .full { width: 100%; }
    .summary { display:flex; flex-wrap:wrap; gap:16px; margin-bottom:12px; }
    .summary span { font-weight:600; }
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-start; }
    .actions-row { display:flex; gap:12px; margin:8px 0 12px; flex-wrap:wrap; }
    .advance-banner {
      display:flex; align-items:center; gap:6px; flex-wrap:wrap;
      background:#cfe2ff; color:#084298; border-radius:8px;
      padding:8px 12px; margin-bottom:12px; font-weight:500;
    }
    .advance-banner span { font-weight:700; }
    .advance-banner mat-icon { color:#084298; }
    .advance-banner .apply-btn { margin-left:auto; }
    .advance-banner .apply-btn mat-icon { color:inherit; }
  `
})
export class RequestPaymentDialog implements OnInit {
  private api = inject(ApiService);
  private ref = inject(MatDialogRef<RequestPaymentDialog>);

  request = signal<StockRequest>(inject<StockRequest>(MAT_DIALOG_DATA));
  payments = signal<StockRequestPayment[]>([]);
  advance = signal<number>(0);   // salesperson's available advance balance
  changed = false;

  amount = signal<number>(0);
  method = signal<string>('Cash');
  note = signal<string>('');

  columns = ['srNo', 'date', 'amount', 'method', 'note', 'actions'];
  payLabel = PaymentStatusLabels;

  ngOnInit() { this.loadPayments(); this.loadAdvance(); }

  loadPayments() {
    this.api.getRequestPayments(this.request().id).subscribe(list => this.payments.set(list));
  }

  loadAdvance() {
    this.api.getRequestAdvance().subscribe(a => this.advance.set(a.advanceBalance));
  }

  addPayment() {
    const amt = Number(this.amount());
    if (amt <= 0) return;
    this.api.addRequestPayment({ stockRequestId: this.request().id, amount: amt, method: this.method(), note: this.note() })
      .subscribe(updated => { this.request.set(updated); this.changed = true; this.amount.set(0); this.note.set(''); this.loadPayments(); this.loadAdvance(); });
  }

  applyAdvance() {
    this.api.applyRequestAdvance(this.request().id)
      .subscribe(updated => { this.request.set(updated); this.changed = true; this.loadPayments(); this.loadAdvance(); });
  }

  settle() {
    this.api.settleRequest(this.request().id)
      .subscribe(updated => { this.request.set(updated); this.changed = true; this.loadPayments(); this.loadAdvance(); });
  }

  deletePayment(p: StockRequestPayment) {
    this.api.deleteRequestPayment(p.id)
      .subscribe(updated => { this.request.set(updated); this.changed = true; this.loadPayments(); this.loadAdvance(); });
  }

  close() { this.ref.close(this.changed); }
}
