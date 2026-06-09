import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmData { title: string; message: string; confirmText?: string; }

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">
        {{ data.confirmText || 'Delete' }}
      </button>
    </mat-dialog-actions>
  `
})
export class ConfirmDialog {
  data = inject<ConfirmData>(MAT_DIALOG_DATA);
  ref = inject(MatDialogRef<ConfirmDialog>);
}
