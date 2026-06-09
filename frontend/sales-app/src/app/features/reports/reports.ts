import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatPaginatorModule } from '@angular/material/paginator';
import { ApiService } from '../../core/api.service';
import { ReportSummary, CustomerReportRow } from '../../core/models';
import { createPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-reports',
  imports: [
    FormsModule, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatSelectModule, MatButtonToggleModule, MatPaginatorModule
  ],
  templateUrl: './reports.html'
})
export class Reports implements OnInit {
  private api = inject(ApiService);

  mode = signal<'monthly' | 'yearly'>('monthly');
  year = signal<number>(new Date().getFullYear());
  month = signal<number | null>(null);

  summary = signal<ReportSummary | null>(null);
  byCustomer = signal<CustomerReportRow[]>([]);

  years = Array.from({ length: 6 }, (_, i) => new Date().getFullYear() - i);
  months = [
    { v: 1, n: 'January' }, { v: 2, n: 'February' }, { v: 3, n: 'March' }, { v: 4, n: 'April' },
    { v: 5, n: 'May' }, { v: 6, n: 'June' }, { v: 7, n: 'July' }, { v: 8, n: 'August' },
    { v: 9, n: 'September' }, { v: 10, n: 'October' }, { v: 11, n: 'November' }, { v: 12, n: 'December' }
  ];

  rowColumns = ['label', 'orders', 'total', 'paid', 'remaining'];
  custColumns = ['customer', 'orders', 'pending', 'delivered', 'total', 'paid', 'remaining'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  rowsPager = createPager(() => this.summary()?.rows ?? []);
  custPager = createPager(() => this.byCustomer());

  ngOnInit() { this.run(); this.loadByCustomer(); }

  run() {
    this.rowsPager.reset();
    if (this.mode() === 'monthly') {
      this.api.monthlyReport(this.year(), this.month() ?? undefined).subscribe(s => this.summary.set(s));
    } else {
      this.api.yearlyReport(this.year()).subscribe(s => this.summary.set(s));
    }
  }

  loadByCustomer() {
    this.api.customerReport().subscribe(r => this.byCustomer.set(r));
  }
}
