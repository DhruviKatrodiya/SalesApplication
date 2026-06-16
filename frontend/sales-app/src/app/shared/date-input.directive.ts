import { Directive, ElementRef, HostListener, inject } from '@angular/core';

/**
 * Enforces a strict DD-MM-YYYY mask on a date input: the user types digits and the
 * hyphens are inserted automatically (2 digits day, '-', 2 digits month, '-', 4 digits year).
 * Letters and other characters are blocked, so a date can only be entered as dd-mm-yyyy
 * (the calendar picker still works).
 */
@Directive({
  selector: '[appDateInput]',
  standalone: true
})
export class DateInputDirective {
  private el = inject<ElementRef<HTMLInputElement>>(ElementRef);
  private reformatting = false;

  @HostListener('keydown', ['$event'])
  onKeydown(e: KeyboardEvent) {
    if (e.ctrlKey || e.metaKey || e.altKey) return;   // allow shortcuts (copy/paste/select-all)
    const control = ['Backspace', 'Delete', 'Tab', 'Escape', 'Enter',
      'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'];
    if (control.includes(e.key)) return;
    if (/^[0-9]$/.test(e.key)) return;                // digits only (hyphens auto-inserted)
    e.preventDefault();
  }

  @HostListener('input')
  onInput() {
    if (this.reformatting) return;
    const input = this.el.nativeElement;
    const masked = DateInputDirective.mask(input.value);
    if (masked !== input.value) {
      input.value = masked;
      // Re-dispatch so the datepicker/ngModel re-reads (and re-parses) the masked value.
      this.reformatting = true;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      this.reformatting = false;
    }
  }

  @HostListener('paste', ['$event'])
  onPaste(e: ClipboardEvent) {
    e.preventDefault();
    const input = this.el.nativeElement;
    input.value = DateInputDirective.mask(e.clipboardData?.getData('text') ?? '');
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  /** Strip to digits (max 8: ddmmyyyy) and insert hyphens as dd-mm-yyyy. */
  private static mask(value: string): string {
    const d = value.replace(/\D/g, '').slice(0, 8);
    if (d.length > 4) return `${d.slice(0, 2)}-${d.slice(2, 4)}-${d.slice(4)}`;
    if (d.length > 2) return `${d.slice(0, 2)}-${d.slice(2)}`;
    return d;
  }
}
