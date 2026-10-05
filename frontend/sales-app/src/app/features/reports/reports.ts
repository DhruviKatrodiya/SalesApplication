import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatPaginatorModule } from '@angular/material/paginator';
import { ApiService } from '../../core/api.service';
import { ReportSummary, CustomerReportRow } from '../../core/models';
import { DateInputDirective } from '../../shared/date-input.directive';
import { createServerPager, PAGE_SIZE_OPTIONS } from '../../shared/pager';

@Component({
  selector: 'app-reports',
  imports: [
    FormsModule, CurrencyPipe, MatCardModule, MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatDatepickerModule, DateInputDirective, MatPaginatorModule
  ],
  templateUrl: './reports.html'
})
export class Reports implements OnInit {
  private api = inject(ApiService);

  // Only a From/To date filter. Defaults to the current month so far.
  fromDate = signal<Date | null>(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  toDate = signal<Date | null>(new Date());

  summary = signal<ReportSummary | null>(null);
  byCustomer = signal<CustomerReportRow[]>([]);

  rowColumns = ['srNo', 'label', 'orders', 'total', 'paid', 'remaining'];
  custColumns = ['srNo', 'customer', 'orders', 'pending', 'delivered', 'total', 'paid', 'remaining'];

  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  rowsPager = createServerPager(() => this.run());             // breakdown rows paged on the server
  custPager = createServerPager(() => this.loadByCustomer());  // per-customer list paged on the server

  ngOnInit() { this.run(); this.loadByCustomer(); }

  // A filter change resets the breakdown to the first page, then reloads.
  private reloadReport() { this.rowsPager.reset(); this.run(); this.custPager.reset(); this.loadByCustomer(); }

  setFromDate(d: Date | null) { this.fromDate.set(d); this.reloadReport(); }
  setToDate(d: Date | null) { this.toDate.set(d); this.reloadReport(); }

  /** Local calendar date as yyyy-MM-dd (no timezone shift). */
  private ymd(d: Date) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  /** Reload callback for the breakdown pager — fetches the current breakdown page from the server. */
  run() {
    const page = this.rowsPager.pageIndex() + 1;
    const pageSize = this.rowsPager.pageSize();
    const from = this.fromDate(), to = this.toDate();
    if (!from || !to || to < from) { this.summary.set(null); this.rowsPager.total.set(0); return; }
    this.api.rangeReport(this.ymd(from), this.ymd(to), page, pageSize).subscribe(s => this.applyReport(s));
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

  /** The inclusive date window the filter describes (same period the summary cards cover). */
  private periodBounds(): { from: string; to: string } | null {
    const f = this.fromDate(), t = this.toDate();
    return f && t && t >= f ? { from: this.ymd(f), to: this.ymd(t) } : null;
  }

  loadByCustomer() {
    const bounds = this.periodBounds();
    if (!bounds) { this.byCustomer.set([]); this.custPager.total.set(0); return; }
    this.api.customerReport({
      ...bounds,
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
