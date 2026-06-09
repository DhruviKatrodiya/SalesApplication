import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './profile.html',
  styles: `
    .avatar-row { display:flex; align-items:center; gap:20px; margin: 4px 0 20px; flex-wrap:wrap; }
    .avatar {
      width:96px; height:96px; border-radius:50%; overflow:hidden;
      display:flex; align-items:center; justify-content:center;
      background: var(--mat-sys-surface-container-high);
      border: 1px solid var(--mat-sys-outline-variant); flex: 0 0 auto;
    }
    .avatar img { width:100%; height:100%; object-fit:cover; }
    .avatar mat-icon { font-size:56px; width:56px; height:56px; color: var(--mat-sys-on-surface-variant); }
    .avatar-actions { display:flex; flex-direction:column; gap:8px; align-items:flex-start; }
    .hint { color: var(--mat-sys-on-surface-variant); font-size: 12px; }
  `
})
export class Profile implements OnInit {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private snack = inject(MatSnackBar);

  saving = signal(false);
  uploading = signal(false);

  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    profileImagePath: ['']
  });

  ngOnInit() {
    this.api.getProfile().subscribe(u => {
      this.form.patchValue({ fullName: u.fullName, email: u.email, phone: u.phone ?? '', profileImagePath: u.profileImagePath ?? '' });
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.snack.open('Please select an image file.', 'Close', { duration: 3000 });
      input.value = '';
      return;
    }
    this.uploading.set(true);
    this.api.uploadProfileImage(file).subscribe({
      next: (res) => {
        this.form.controls.profileImagePath.setValue(res.url);
        this.form.controls.profileImagePath.markAsDirty();
        this.snack.open('Image uploaded. Click "Save Changes" to apply.', 'Close', { duration: 3500 });
        this.uploading.set(false);
        input.value = '';
      },
      error: () => { this.uploading.set(false); input.value = ''; }
    });
  }

  clearImage() {
    this.form.controls.profileImagePath.setValue('');
    this.form.controls.profileImagePath.markAsDirty();
  }

  save() {
    if (this.form.invalid) return;
    this.saving.set(true);
    const { fullName, email, phone, profileImagePath } = this.form.getRawValue();
    this.api.updateProfile({ fullName, email, phone, profileImagePath }).subscribe({
      next: (u) => {
        this.auth.setUser(u);
        this.snack.open('Profile updated.', 'Close', { duration: 3000 });
        this.saving.set(false);
      },
      error: () => this.saving.set(false)
    });
  }
}
