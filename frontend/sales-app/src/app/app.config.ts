import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter, DateAdapter, MAT_DATE_FORMATS } from '@angular/material/core';
import { MAT_DIALOG_DEFAULT_OPTIONS } from '@angular/material/dialog';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { DdMmYyyyDateAdapter, DD_MM_YYYY_DATE_FORMATS } from './shared/date-format';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideNativeDateAdapter(),
    // Display & parse all datepicker inputs as DD-MM-YYYY app-wide.
    { provide: DateAdapter, useClass: DdMmYyyyDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: DD_MM_YYYY_DATE_FORMATS },
    // Keep dialogs open when tapping the backdrop or pressing Escape;
    // they can only be dismissed via Cancel / Save.
    { provide: MAT_DIALOG_DEFAULT_OPTIONS, useValue: { disableClose: true } }
  ]
};
