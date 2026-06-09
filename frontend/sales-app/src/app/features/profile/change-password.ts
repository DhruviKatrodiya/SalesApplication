import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './change-password.html'
})
export class ChangePassword {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  saving = signal(false);
  hideCurrent = signal(true);
  hideNew = signal(true);
  hideConfirm = signal(true);

  form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', Validators.required]
  }, { validators: match });

  save() {
    if (this.form.invalid) return;
    this.saving.set(true);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.api.changePassword({ currentPassword, newPassword }).subscribe({
      next: (res) => {
        this.snack.open(res.message, 'Close', { duration: 3000 });
        this.router.navigate(['/dashboard']);
      },
      error: () => this.saving.set(false)
    });
  }
}

function match(c: AbstractControl) {
  return c.get('newPassword')?.value === c.get('confirmPassword')?.value ? null : { mismatch: true };
}
