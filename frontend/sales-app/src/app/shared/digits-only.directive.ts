import { Directive, ElementRef, HostListener, inject, Optional } from '@angular/core';
import { NgControl } from '@angular/forms';

/**
 * Restricts an input to digits only — strips letters, spaces and special characters
 * as the user types or pastes. Works with both reactive forms and ngModel.
 */
@Directive({
  selector: '[appDigitsOnly]',
  standalone: true
})
export class DigitsOnlyDirective {
  private el = inject<ElementRef<HTMLInputElement>>(ElementRef);
  constructor(@Optional() private control: NgControl) {}

  @HostListener('input')
  onInput() {
    const input = this.el.nativeElement;
    const cleaned = input.value.replace(/\D/g, '');
    if (cleaned !== input.value) {
      input.value = cleaned;
      if (this.control?.control) this.control.control.setValue(cleaned);
      else input.dispatchEvent(new Event('input'));
    }
  }
}
