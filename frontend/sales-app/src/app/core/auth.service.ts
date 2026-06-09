import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginResponse, User } from './models';

const TOKEN_KEY = 'sales_app_token';
const USER_KEY = 'sales_app_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly base = `${environment.apiBaseUrl}/auth`;

  readonly currentUser = signal<User | null>(this.readUser());
  readonly isAuthenticated = computed(() => !!this.currentUser() && !!this.token);

  constructor(private http: HttpClient) {}

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/login`, { email, password }).pipe(
      tap(res => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(USER_KEY, JSON.stringify(res.user));
        this.currentUser.set(res.user);
      })
    );
  }

  forgotPassword(email: string) {
    return this.http.post<{ message: string }>(`${this.base}/forgot-password`, { email });
  }
  verifyOtp(email: string, otp: string) {
    return this.http.post<{ message: string }>(`${this.base}/verify-otp`, { email, otp });
  }
  resetPassword(email: string, otp: string, newPassword: string) {
    return this.http.post<{ message: string }>(`${this.base}/reset-password`, { email, otp, newPassword });
  }

  setUser(user: User) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
  }

  private readUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) as User : null;
  }
}
