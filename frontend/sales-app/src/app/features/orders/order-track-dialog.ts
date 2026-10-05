import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/api.service';
import { Order, OrderHistoryEntry, OrderStatus, OrderStatusLabels } from '../../core/models';

interface Step { status: OrderStatus; label: string; reachedAt: string | null; current: boolean; }

/** Order tracking: a progress bar of the normal flow, plus the full timeline of every change. */
@Component({
  selector: 'app-order-track-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Track Order — {{ order.orderNumber }}</h2>
    <mat-dialog-content class="content">
      @if (cancelled()) {
        <div class="cancelled"><mat-icon>cancel</mat-icon> This order was cancelled.</div>
      }
      <div class="steps" [class.dim]="cancelled()">
        @for (s of steps(); track s.status) {
          <div class="step" [class.done]="s.reachedAt" [class.current]="s.current">
            <div class="dot"><mat-icon>{{ s.reachedAt ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon></div>
            <div class="name">{{ s.label }}</div>
            <div class="when">{{ s.reachedAt ? (utc(s.reachedAt) | date:'dd-MM-yyyy') : '' }}</div>
          </div>
        }
      </div>

      @if (order.deliveryDate) {
        <div class="planned"><mat-icon>event</mat-icon> Planned delivery: {{ order.deliveryDate | date:'dd-MM-yyyy' }}</div>
      }

      <h3>History</h3>
      @if (loading()) { <div class="muted">Loading…</div> }
      <ol class="timeline">
        @for (h of history(); track h.id) {
          <li>
            <div class="head">
              @if (h.fromStatus !== h.toStatus) {
                <span [class]="chip(h.toStatus)">{{ label[h.toStatus] }}</span>
                @if (h.fromStatus !== null) { <span class="muted">from {{ label[h.fromStatus] }}</span> }
              } @else {
                <span class="muted">{{ label[h.toStatus] }}</span>
              }
            </div>
            @if (h.note) { <div class="note">{{ h.note }}</div> }
            <div class="muted small">{{ utc(h.changedAt) | date:'dd-MM-yyyy HH:mm' }}@if (h.changedByName) { · {{ h.changedByName }} }</div>
          </li>
        }
      </ol>
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-flat-button color="primary" mat-dialog-close>Close</button></mat-dialog-actions>
  `,
  styles: `
    .content { min-width: 380px; max-width: 520px; }
    .steps { display: flex; justify-content: space-between; margin: 8px 0 16px; }
    .steps.dim { opacity: .45; }
    .step { flex: 1; text-align: center; color: #888; }
    .step .dot mat-icon { font-size: 28px; width: 28px; height: 28px; }
    .step.done { color: #0f5132; }
    .step.current .name { font-weight: 700; }
    .name { font-size: 13px; }
    .when { font-size: 11px; min-height: 14px; }
    .cancelled { display: flex; align-items: center; gap: 6px; padding: 8px 12px; margin-bottom: 8px; border-radius: 8px; background: #f8d7da; color: #842029; font-weight: 600; }
    .planned { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; color: #444; }
    h3 { margin: 12px 0 8px; }
    .timeline { list-style: none; margin: 0; padding: 0 0 0 14px; border-left: 2px solid #ddd; }
    .timeline li { position: relative; padding: 0 0 14px 14px; }
    .timeline li::before { content: ''; position: absolute; left: -21px; top: 4px; width: 10px; height: 10px; border-radius: 50%; background: #1565c0; }
    .head { display: flex; align-items: center; gap: 8px; }
    .note { margin-top: 2px; }
    .muted { color: #666; }
    .small { font-size: 12px; margin-top: 2px; }
  `
})
export class OrderTrackDialog {
  private api = inject(ApiService);
  order = inject<Order>(MAT_DIALOG_DATA);

  readonly label = OrderStatusLabels;
  history = signal<OrderHistoryEntry[]>([]);
  loading = signal(true);

  /** The normal delivery flow. "Remaining" and "Cancelled" are side states shown in the history only. */
  private flow = [OrderStatus.Pending, OrderStatus.Dispatched, OrderStatus.Delivered, OrderStatus.Completed];

  constructor() {
    this.api.orderHistory(this.order.id).subscribe({
      next: (h) => { this.history.set(h); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  cancelled() { return this.order.status === OrderStatus.Cancelled; }

  steps(): Step[] {
    const h = this.history();
    return this.flow.map(status => ({
      status, label: OrderStatusLabels[status],
      // The first time the order reached this status.
      reachedAt: h.find(x => x.toStatus === status && x.fromStatus !== x.toStatus)?.changedAt ?? null,
      current: this.order.status === status
    }));
  }

  chip(status: OrderStatus) { return 'chip chip-' + OrderStatusLabels[status].toLowerCase(); }

  /** The API sends UTC timestamps without a zone marker; mark them so they display in local time. */
  utc(s: string) { return /Z$|[+-]\d\d:\d\d$/.test(s) ? s : s + 'Z'; }
}
