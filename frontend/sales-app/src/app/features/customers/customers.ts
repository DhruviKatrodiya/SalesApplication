import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Customer, CustomerSearchResult, OrderStatusLabels, PaymentStatusLabels } from '../../core/models';
import { CustomerDialog } from './customer-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-customers',
  imports: [
    FormsModule, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatExpansionModule, MatPaginatorModule
  ],
  templateUrl: './customers.html',
  styleUrl: './customers.scss'
})
export class Customers implements OnInit {
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  customers = signal<Customer[]>([]);
  query = signal('');
  detail = signal<CustomerSearchResult | null>(null);

  columns = ['name', 'phone', 'email', 'actions'];

  orderStatusLabel = OrderStatusLabels;
  paymentStatusLabel = PaymentStatusLabels;

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  pager = createPager(() => this.customers());

  ngOnInit() { this.load(); }

  load() { this.api.getCustomers(this.query()).subscribe(list => this.customers.set(list)); }

  onSearch() { this.pager.reset(); this.load(); }

  viewDetails(c: Customer) {
    this.api.getCustomerDetails(c.id).subscribe(d => this.detail.set(d));
  }
  closeDetails() { this.detail.set(null); }

  add() {
    this.dialog.open(CustomerDialog, { data: null }).afterClosed().subscribe(res => {
      if (res) this.api.createCustomer(res).subscribe(() => { this.snack.open('Customer added', 'Close', { duration: 2000 }); this.load(); });
    });
  }
  edit(c: Customer) {
    this.dialog.open(CustomerDialog, { data: c }).afterClosed().subscribe(res => {
      if (res) this.api.updateCustomer(c.id, res).subscribe(() => {
        this.snack.open('Customer updated', 'Close', { duration: 2000 });
        this.load();
        if (this.detail()?.customer.id === c.id) this.viewDetails(c);
      });
    });
  }
  remove(c: Customer) {
    this.dialog.open(ConfirmDialog, { data: { title: 'Confirm', message: `Delete customer "${c.name}"?` } })
      .afterClosed().subscribe(ok => {
        if (ok) this.api.deleteCustomer(c.id).subscribe({
          next: () => { this.snack.open('Customer deleted', 'Close', { duration: 2000 }); this.load(); if (this.detail()?.customer.id === c.id) this.closeDetails(); }
        });
      });
  }

  statusChipClass(status: number): string {
    return 'chip chip-' + (this.orderStatusLabel[status] ?? '').toLowerCase();
  }
  payChipClass(status: number): string {
    return 'chip chip-' + (this.paymentStatusLabel[status] ?? '').toLowerCase();
  }
}
