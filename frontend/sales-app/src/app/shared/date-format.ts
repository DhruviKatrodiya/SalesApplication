import { NativeDateAdapter } from '@angular/material/core';
import { MatDateFormats } from '@angular/material/core';

/**
 * Date adapter that displays and parses dates as DD-MM-YYYY in all Material
 * datepicker inputs across the app. (The wire/API format stays ISO yyyy-MM-dd.)
 */
export class DdMmYyyyDateAdapter extends NativeDateAdapter {
  override parse(value: unknown): Date | null {
    if (typeof value === 'string' && value.trim()) {
      const m = value.trim().match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
      if (m) {
        const day = +m[1], month = +m[2], year = +m[3];
        if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return new Date(year, month - 1, day);
        return null;
      }
    }
    const ts = typeof value === 'number' ? value : Date.parse(value as string);
    return isNaN(ts) ? null : new Date(ts);
  }

  override format(date: Date, _displayFormat: object): string {
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    return `${day}-${month}-${date.getFullYear()}`;
  }
}

export const DD_MM_YYYY_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: 'dd-MM-yyyy' },
  display: {
    dateInput: 'dd-MM-yyyy',
    monthYearLabel: 'MMM yyyy',
    dateA11yLabel: 'dd-MM-yyyy',
    monthYearA11yLabel: 'MMMM yyyy'
  }
};
