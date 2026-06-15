import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Order, Payment, PaymentStatusLabels } from '../../core/models';

@Component({
  selector: 'app-payment-dialog',
  imports: [
    FormsModule, DatePipe, CurrencyPipe, MatDialogModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatTableModule
  ],
  templateUrl: './payment-dialog.html',
  styles: `
    .dialog-form { min-width: 480px; }
    .full { width: 100%; }
    .summary { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:12px; }
    .summary div span { font-weight:600; }
    .advance-banner {
      display:flex; align-items:center; gap:6px; flex-wrap:wrap;
      background:#cfe2ff; color:#084298; border-radius:8px;
      padding:8px 12px; margin-bottom:12px; font-weight:500;
    }
    .advance-banner span { font-weight:700; }
    .advance-banner mat-icon { color:#084298; }
    .advance-banner .apply-btn { margin-left:auto; }
    .advance-banner .apply-btn mat-icon { color:inherit; }
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-start; }
    .actions-row { display:flex; gap:16px; flex-wrap:wrap; align-items:center; margin: 8px 0 24px; }
    .actions-row button { min-width: 150px; }
  `
})
export class PaymentDialog implements OnInit {
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);
  ref = inject(MatDialogRef<PaymentDialog>);
  order = signal<Order>(inject<Order>(MAT_DIALOG_DATA));

  payments = signal<Payment[]>([]);
  advance = signal<number>(0);   // customer's available advance balance (shown, not auto-applied)
  amount = signal<number | null>(null);
  method = signal<string>('Cash');
  note = signal<string>('');
  changed = false;

  columns = ['srNo', 'date', 'amount', 'method', 'note', 'actions'];
  payLabel = PaymentStatusLabels;

  ngOnInit() { this.loadPayments(); this.loadAdvance(); }

  loadPayments() {
    this.api.getPaymentsByOrder(this.order().id).subscribe(p => this.payments.set(p));
  }

  loadAdvance() {
    this.api.getCustomerAdvance(this.order().customerId).subscribe(r => this.advance.set(r.advanceBalance));
  }

  addPayment() {
    const amt = Number(this.amount());
    if (!amt || amt <= 0) { this.snack.open('Enter a valid amount.', 'Close', { duration: 2500 }); return; }
    this.api.addPayment({ orderId: this.order().id, amount: amt, method: this.method(), note: this.note() })
      .subscribe(updated => {
        this.order.set(updated);
        this.changed = true;
        this.amount.set(null);
        this.note.set('');
        this.snack.open('Payment recorded', 'Close', { duration: 2000 });
        this.loadPayments();
        this.loadAdvance();
      });
  }

  applyAdvance() {
    this.api.applyAdvance(this.order().id).subscribe(updated => {
      this.order.set(updated);
      this.changed = true;
      this.snack.open('Advance applied to this order', 'Close', { duration: 2000 });
      this.loadPayments();
      this.loadAdvance();
    });
  }

  settle() {
    this.api.settleOrder(this.order().id).subscribe(updated => {
      this.order.set(updated);
      this.changed = true;
      this.snack.open('Order fully settled', 'Close', { duration: 2000 });
      this.loadPayments();
      this.loadAdvance();
    });
  }

  deletePayment(p: Payment) {
    this.api.deletePayment(p.id).subscribe(updated => {
      this.order.set(updated);
      this.changed = true;
      this.snack.open('Payment removed', 'Close', { duration: 2000 });
      this.loadPayments();
      this.loadAdvance();
    });
  }

  close() { this.ref.close(this.changed); }
}
