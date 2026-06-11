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
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

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

  mode = signal<'daily' | 'monthly' | 'yearly'>('monthly');
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

  rowColumns = ['srNo', 'label', 'orders', 'total', 'paid', 'remaining'];
  custColumns = ['srNo', 'customer', 'orders', 'pending', 'delivered', 'total', 'paid', 'remaining'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  rowsPager = createServerPager(() => this.run());             // breakdown rows paged on the server
  custPager = createServerPager(() => this.loadByCustomer());  // per-customer list paged on the server

  ngOnInit() { this.run(); this.loadByCustomer(); }

  // A filter change resets the breakdown to the first page, then reloads.
  private reloadReport() { this.rowsPager.reset(); this.run(); }

  setMode(m: 'daily' | 'monthly' | 'yearly') {
    this.mode.set(m);
    // Daily defaults to the current month and year.
    if (m === 'daily') {
      const now = new Date();
      this.year.set(now.getFullYear());
      this.month.set(now.getMonth() + 1);
    }
    this.reloadReport();
  }

  setYear(y: number) { this.year.set(y); this.reloadReport(); }
  setMonth(m: number | null) { this.month.set(m); this.reloadReport(); }

  /** Reload callback for the breakdown pager — fetches the current breakdown page from the server. */
  run() {
    const page = this.rowsPager.pageIndex() + 1;
    const pageSize = this.rowsPager.pageSize();
    if (this.mode() === 'monthly') {
      this.api.monthlyReport(this.year(), this.month() ?? undefined, page, pageSize).subscribe(s => this.applyReport(s));
    } else if (this.mode() === 'daily') {
      // Daily needs a specific month — default to the current one if none chosen.
      const m = this.month() ?? (new Date().getMonth() + 1);
      if (this.month() == null) this.month.set(m);
      this.api.dailyReport(this.year(), m, page, pageSize).subscribe(s => this.applyReport(s));
    } else {
      this.api.yearlyReport(this.year(), page, pageSize).subscribe(s => this.applyReport(s));
    }
  }

  private applyReport(s: ReportSummary) {
    const maxIndex = Math.max(0, Math.ceil(s.rowsTotal / this.rowsPager.pageSize()) - 1);
    if (this.rowsPager.pageIndex() > maxIndex) {
      this.rowsPager.pageIndex.set(maxIndex);
      this.run();
      return;
    }
    this.summary.set(s);
    this.rowsPager.total.set(s.rowsTotal);
  }

  loadByCustomer() {
    this.api.customerReport({
      page: this.custPager.pageIndex() + 1,
      pageSize: this.custPager.pageSize()
    }).subscribe(res => {
      const maxIndex = Math.max(0, Math.ceil(res.total / this.custPager.pageSize()) - 1);
      if (this.custPager.pageIndex() > maxIndex) {
        this.custPager.pageIndex.set(maxIndex);
        this.loadByCustomer();
        return;
      }
      this.byCustomer.set(res.items);
      this.custPager.total.set(res.total);
    });
  }
}
