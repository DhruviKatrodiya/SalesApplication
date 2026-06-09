import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-forgot-password',
  imports: [
    ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule
  ],
  templateUrl: './forgot-password.html',
  styleUrl: './auth.scss'
})
export class ForgotPassword {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  loading = signal(false);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  submit() {
    if (this.form.invalid) return;
    this.loading.set(true);
    const email = this.form.getRawValue().email;
    this.auth.forgotPassword(email).subscribe({
      next: (res) => {
        this.snack.open(res.message, 'Close', { duration: 4000 });
        this.router.navigate(['/reset-password'], { queryParams: { email } });
      },
      error: () => this.loading.set(false),
      complete: () => this.loading.set(false)
    });
  }
}
