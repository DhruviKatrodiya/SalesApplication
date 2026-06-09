import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-reset-password',
  imports: [
    ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule
  ],
  templateUrl: './reset-password.html',
  styleUrl: './auth.scss'
})
export class ResetPassword {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);

  loading = signal(false);
  otpVerified = signal(false);
  hideNew = signal(true);
  hideConfirm = signal(true);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required]]
  }, { validators: matchPasswords });

  constructor() {
    const email = this.route.snapshot.queryParamMap.get('email');
    if (email) this.form.controls.email.setValue(email);
  }

  verify() {
    const { email, otp } = this.form.getRawValue();
    if (this.form.controls.email.invalid || this.form.controls.otp.invalid) return;
    this.loading.set(true);
    this.auth.verifyOtp(email, otp).subscribe({
      next: (res) => { this.otpVerified.set(true); this.snack.open(res.message, 'Close', { duration: 3000 }); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  submit() {
    if (this.form.invalid) return;
    this.loading.set(true);
    const { email, otp, newPassword } = this.form.getRawValue();
    this.auth.resetPassword(email, otp, newPassword).subscribe({
      next: (res) => {
        this.snack.open(res.message, 'Close', { duration: 4000 });
        this.router.navigate(['/login']);
      },
      error: () => this.loading.set(false)
    });
  }
}

function matchPasswords(c: AbstractControl) {
  const a = c.get('newPassword')?.value;
  const b = c.get('confirmPassword')?.value;
  return a === b ? null : { mismatch: true };
}
