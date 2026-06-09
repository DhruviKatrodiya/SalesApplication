import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
import {
  Category, SubCategory, Item, Customer, CustomerSearchResult,
  Order, OrderStatus, Payment, Dispatch, ReceivedStatus,
  ReportSummary, CustomerReportRow, User
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly base = environment.apiBaseUrl;
  constructor(private http: HttpClient) {}

  // ---- Profile ----
  getProfile() { return this.http.get<User>(`${this.base}/profile/me`); }
  updateProfile(body: { fullName: string; email: string; phone?: string; profileImagePath?: string }) {
    return this.http.put<User>(`${this.base}/profile`, body);
  }
  changePassword(body: { currentPassword: string; newPassword: string }) {
    return this.http.post<{ message: string }>(`${this.base}/profile/change-password`, body);
  }
  uploadProfileImage(file: File) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<{ url: string }>(`${this.base}/profile/upload-image`, fd);
  }

  // ---- Categories ----
  getCategories() { return this.http.get<Category[]>(`${this.base}/categories`); }
  createCategory(b: { name: string; description?: string }) { return this.http.post<Category>(`${this.base}/categories`, b); }
  updateCategory(id: number, b: { name: string; description?: string }) { return this.http.put<Category>(`${this.base}/categories/${id}`, b); }
  deleteCategory(id: number) { return this.http.delete(`${this.base}/categories/${id}`); }

  // ---- SubCategories ----
  getSubCategories(categoryId?: number) {
    let p = new HttpParams();
    if (categoryId) p = p.set('categoryId', categoryId);
    return this.http.get<SubCategory[]>(`${this.base}/subcategories`, { params: p });
  }
  createSubCategory(b: { categoryId: number; name: string; description?: string }) { return this.http.post<SubCategory>(`${this.base}/subcategories`, b); }
  updateSubCategory(id: number, b: { categoryId: number; name: string; description?: string }) { return this.http.put<SubCategory>(`${this.base}/subcategories/${id}`, b); }
  deleteSubCategory(id: number) { return this.http.delete(`${this.base}/subcategories/${id}`); }

  // ---- Items / Inventory ----
  getItems(opts?: { subCategoryId?: number; lowStock?: boolean; threshold?: number }) {
    let p = new HttpParams();
    if (opts?.subCategoryId) p = p.set('subCategoryId', opts.subCategoryId);
    if (opts?.lowStock) p = p.set('lowStock', true);
    if (opts?.threshold != null) p = p.set('threshold', opts.threshold);
    return this.http.get<Item[]>(`${this.base}/items`, { params: p });
  }
  createItem(b: any) { return this.http.post<Item>(`${this.base}/items`, b); }
  updateItem(id: number, b: any) { return this.http.put<Item>(`${this.base}/items/${id}`, b); }
  deleteItem(id: number) { return this.http.delete(`${this.base}/items/${id}`); }

  // ---- Dispatch ----
  getDispatches() { return this.http.get<Dispatch[]>(`${this.base}/dispatch`); }
  createDispatch(b: { truckLabel?: string; notes?: string; items: { itemId: number; quantity: number }[] }) {
    return this.http.post<Dispatch>(`${this.base}/dispatch`, b);
  }

  // ---- Customers ----
  getCustomers(query?: string) {
    let p = new HttpParams();
    if (query) p = p.set('query', query);
    return this.http.get<Customer[]>(`${this.base}/customers`, { params: p });
  }
  getCustomerDetails(id: number) { return this.http.get<CustomerSearchResult>(`${this.base}/customers/${id}/details`); }
  searchCustomers(query: string) {
    const p = new HttpParams().set('query', query);
    return this.http.get<CustomerSearchResult[]>(`${this.base}/customers/search`, { params: p });
  }
  createCustomer(b: any) { return this.http.post<Customer>(`${this.base}/customers`, b); }
  updateCustomer(id: number, b: any) { return this.http.put<Customer>(`${this.base}/customers/${id}`, b); }
  deleteCustomer(id: number) { return this.http.delete(`${this.base}/customers/${id}`); }

  // ---- Orders ----
  getOrders(opts?: { customerId?: number; status?: OrderStatus }) {
    let p = new HttpParams();
    if (opts?.customerId) p = p.set('customerId', opts.customerId);
    if (opts?.status != null) p = p.set('status', opts.status);
    return this.http.get<Order[]>(`${this.base}/orders`, { params: p });
  }
  getOrder(id: number) { return this.http.get<Order>(`${this.base}/orders/${id}`); }
  createOrder(b: any) { return this.http.post<Order>(`${this.base}/orders`, b); }
  updateOrder(id: number, b: any) { return this.http.put<Order>(`${this.base}/orders/${id}`, b); }
  updateOrderStatus(id: number, status: OrderStatus) { return this.http.put<Order>(`${this.base}/orders/${id}/status`, { status }); }
  updateDeliveryDate(id: number, deliveryDate: string | null) { return this.http.put<Order>(`${this.base}/orders/${id}/delivery-date`, { deliveryDate }); }
  updateReceivedStatus(orderId: number, orderItemId: number, receivedStatus: ReceivedStatus) {
    return this.http.put<Order>(`${this.base}/orders/${orderId}/items/${orderItemId}/received-status`, { receivedStatus });
  }
  deleteOrder(id: number) { return this.http.delete(`${this.base}/orders/${id}`); }

  // ---- Payments ----
  getPaymentsByOrder(orderId: number) { return this.http.get<Payment[]>(`${this.base}/payments/by-order/${orderId}`); }
  addPayment(b: { orderId: number; amount: number; paymentDate?: string; method?: string; note?: string }) {
    return this.http.post<Order>(`${this.base}/payments`, b);
  }
  settleOrder(orderId: number) { return this.http.post<Order>(`${this.base}/payments/settle/${orderId}`, {}); }
  deletePayment(id: number) { return this.http.delete<Order>(`${this.base}/payments/${id}`); }

  // ---- Reports ----
  monthlyReport(year: number, month?: number) {
    let p = new HttpParams().set('year', year);
    if (month) p = p.set('month', month);
    return this.http.get<ReportSummary>(`${this.base}/reports/monthly`, { params: p });
  }
  yearlyReport(year: number) {
    const p = new HttpParams().set('year', year);
    return this.http.get<ReportSummary>(`${this.base}/reports/yearly`, { params: p });
  }
  customerReport() { return this.http.get<CustomerReportRow[]>(`${this.base}/reports/by-customer`); }
}
