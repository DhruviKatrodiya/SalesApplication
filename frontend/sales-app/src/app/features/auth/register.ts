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
import { DigitsOnlyDirective } from '../../shared/digits-only.directive';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule, DigitsOnlyDirective
  ],
  templateUrl: './register.html',
  styleUrl: './auth.scss'
})
export class Register {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  loading = signal(false);
  hidePassword = signal(true);

  form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.pattern(/^\d{7,15}$/)],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  submit() {
    if (this.form.invalid) return;
    this.loading.set(true);
    const { fullName, email, phone, password } = this.form.getRawValue();
    this.auth.register({ fullName, email, phone: phone || undefined, password }).subscribe({
      next: () => {
        this.snack.open('Account created. Please sign in.', 'Close', { duration: 4000 });
        this.router.navigate(['/login']);
      },
      error: (e) => {
        this.snack.open(e.error?.message ?? 'Registration failed.', 'Close', { duration: 4000 });
        this.loading.set(false);
      }
    });
  }
}
