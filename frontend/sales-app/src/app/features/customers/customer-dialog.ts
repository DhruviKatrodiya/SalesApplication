import { Component, ElementRef, inject, signal, computed, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Customer, Route } from '../../core/models';
import { DigitsOnlyDirective } from '../../shared/digits-only.directive';

interface DialogData { customer: Customer | null; routes: Route[]; }

@Component({
  selector: 'app-customer-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, DigitsOnlyDirective],
  template: `
    <h2 mat-dialog-title>{{ data.customer ? 'Edit' : 'Add' }} Customer</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="fill" class="full">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" required />
          @if (form.controls.name.hasError('required')) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" appDigitsOnly inputmode="numeric" maxlength="15" required />
          @if (form.controls.phone.hasError('required')) { <mat-error>Phone is required</mat-error> }
          @if (form.controls.phone.hasError('pattern')) { <mat-error>Enter a valid phone number (7–15 digits)</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" required />
          @if (form.controls.email.hasError('required')) { <mat-error>Email is required</mat-error> }
          @if (form.controls.email.hasError('email')) { <mat-error>Enter a valid email address</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Route</mat-label>
          <mat-select formControlName="routeId" (opened)="onRouteSelectOpened()" (closed)="routeSearch.set('')">
            <div class="select-search">
              <mat-icon class="select-search-icon">search</mat-icon>
              <input #routeSearchInput type="text" class="select-search-input" placeholder="Search route…"
                     [value]="routeSearch()"
                     (input)="routeSearch.set($any($event.target).value)"
                     (keydown)="$event.stopPropagation()"
                     (click)="$event.stopPropagation()" />
            </div>
            <mat-option [value]="null">— None —</mat-option>
            @for (r of filteredRoutes(); track r.id) {
              <mat-option [value]="r.id">{{ r.name }}</mat-option>
            }
            @if (!filteredRoutes().length) {
              <div class="select-empty">No routes found</div>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="fill" class="full">
          <mat-label>Address</mat-label>
          <textarea matInput formControlName="address" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: `
    .dialog-form { display:flex; flex-direction:column; min-width:360px; }
    .full{width:100%;}
    .select-search {
      position: sticky;
      top: 0;
      z-index: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: var(--mat-sys-surface-container);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .select-search-icon { color: var(--mat-sys-on-surface-variant); }
    .select-search-input {
      flex: 1;
      box-sizing: border-box;
      padding: 8px 10px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 6px;
      font: inherit;
      color: var(--mat-sys-on-surface);
      background: var(--mat-sys-surface);
      outline: none;
    }
    .select-search-input:focus { border-color: var(--mat-sys-primary); }
    .select-empty { padding: 12px 16px; color: var(--mat-sys-on-surface-variant); }
  `
})
export class CustomerDialog {
  private fb = inject(FormBuilder);
  ref = inject(MatDialogRef<CustomerDialog>);
  data = inject<DialogData>(MAT_DIALOG_DATA);

  private routeSearchInput = viewChild<ElementRef<HTMLInputElement>>('routeSearchInput');
  readonly routeSearch = signal('');
  readonly filteredRoutes = computed(() => {
    const q = this.routeSearch().toLowerCase().trim();
    const list = this.data.routes;
    return q ? list.filter(r => r.name.toLowerCase().includes(q)) : list;
  });

  onRouteSelectOpened() {
    setTimeout(() => this.routeSearchInput()?.nativeElement.focus());
  }

  form = this.fb.nonNullable.group({
    name: [this.data.customer?.name ?? '', Validators.required],
    phone: [this.data.customer?.phone ?? '', [Validators.required, Validators.pattern(/^\d{7,15}$/)]],
    email: [this.data.customer?.email ?? '', [Validators.required, Validators.email]],
    routeId: [this.data.customer?.routeId ?? null as number | null],
    address: [this.data.customer?.address ?? '']
  });

  save() {
    if (this.form.invalid) return;
    this.ref.close(this.form.getRawValue());
  }
}
