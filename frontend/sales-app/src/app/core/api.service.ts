import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Category, SubCategory, Item, Customer, CustomerSearchResult, CustomerAdvance,
  Order, OrderStatus, Payment, Dispatch, ReceivedStatus,
  ReportSummary, CustomerReportRow, User, PagedResult, DispatchDraft, Route, StockRequest, StockRequestPayment, StockRequestAdvance,
  Truck, TruckStockItem, ItemPriceHistory
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
  getCategories(opts?: { search?: string; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.search) p = p.set('search', opts.search);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<Category>>(`${this.base}/categories`, { params: p });
  }
  /** Full list (e.g. to populate a dropdown), independent of table paging. */
  getAllCategories(search?: string) {
    return this.getCategories({ search, page: 1, pageSize: 1000 }).pipe(map(r => r.items));
  }
  createCategory(b: { name: string; description?: string }) { return this.http.post<Category>(`${this.base}/categories`, b); }
  updateCategory(id: number, b: { name: string; description?: string }) { return this.http.put<Category>(`${this.base}/categories/${id}`, b); }
  deleteCategory(id: number) { return this.http.delete(`${this.base}/categories/${id}`); }

  // ---- SubCategories ----
  getSubCategories(opts?: { categoryId?: number; search?: string; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.categoryId) p = p.set('categoryId', opts.categoryId);
    if (opts?.search) p = p.set('search', opts.search);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<SubCategory>>(`${this.base}/subcategories`, { params: p });
  }
  /** Full list (e.g. to populate the item dialog's sub-category dropdown). */
  getAllSubCategories(categoryId?: number) {
    return this.getSubCategories({ categoryId, page: 1, pageSize: 1000 }).pipe(map(r => r.items));
  }
  createSubCategory(b: { categoryId: number; name: string; description?: string }) { return this.http.post<SubCategory>(`${this.base}/subcategories`, b); }
  updateSubCategory(id: number, b: { categoryId: number; name: string; description?: string }) { return this.http.put<SubCategory>(`${this.base}/subcategories/${id}`, b); }
  deleteSubCategory(id: number) { return this.http.delete(`${this.base}/subcategories/${id}`); }

  // ---- Items / Inventory ----
  getItems(opts?: { subCategoryId?: number; lowStock?: boolean; threshold?: number; category?: string; item?: string; sku?: string; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.subCategoryId) p = p.set('subCategoryId', opts.subCategoryId);
    if (opts?.lowStock) p = p.set('lowStock', true);
    if (opts?.threshold != null) p = p.set('threshold', opts.threshold);
    if (opts?.category) p = p.set('category', opts.category);
    if (opts?.item) p = p.set('item', opts.item);
    if (opts?.sku) p = p.set('sku', opts.sku);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<Item>>(`${this.base}/items`, { params: p });
  }
  /** Full item list (e.g. dashboard, order/dispatch dropdowns), independent of table paging. */
  getAllItems(opts?: { subCategoryId?: number; lowStock?: boolean; threshold?: number; category?: string; item?: string; sku?: string }) {
    return this.getItems({ ...opts, page: 1, pageSize: 1000 }).pipe(map(r => r.items));
  }
  createItem(b: any) { return this.http.post<Item>(`${this.base}/items`, b); }
  updateItem(id: number, b: any) { return this.http.put<Item>(`${this.base}/items/${id}`, b); }
  deleteItem(id: number) { return this.http.delete(`${this.base}/items/${id}`); }
  getItemPriceHistory(id: number) { return this.http.get<ItemPriceHistory>(`${this.base}/items/${id}/price-history`); }

  // ---- Dispatch ----
  getDispatches(opts?: { truck?: string; date?: string; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.truck) p = p.set('truck', opts.truck);
    if (opts?.date) p = p.set('date', opts.date);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<Dispatch>>(`${this.base}/dispatch`, { params: p });
  }
  createDispatch(b: { truckLabel?: string; notes?: string; items: { itemId: number; quantity: number }[] }) {
    return this.http.post<Dispatch>(`${this.base}/dispatch`, b);
  }
  // Unsaved dispatch cart, persisted per user (each line carries its truck)
  getDispatchTruckLabels() { return this.http.get<string[]>(`${this.base}/dispatch/trucks`); }
  getDispatchDraft() { return this.http.get<DispatchDraft>(`${this.base}/dispatch/draft`); }
  saveDispatchDraft(b: DispatchDraft) { return this.http.put<DispatchDraft>(`${this.base}/dispatch/draft`, b); }
  clearDispatchDraft() { return this.http.delete(`${this.base}/dispatch/draft`); }

  // ---- Routes ----
  getRoutes(opts?: { search?: string; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.search) p = p.set('search', opts.search);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<Route>>(`${this.base}/routes`, { params: p });
  }
  /** Full route list (e.g. the customer dialog dropdown), independent of table paging. */
  getAllRoutes(search?: string) {
    return this.getRoutes({ search, page: 1, pageSize: 1000 }).pipe(map(r => r.items));
  }
  createRoute(b: { name: string; description?: string }) { return this.http.post<Route>(`${this.base}/routes`, b); }
  updateRoute(id: number, b: { name: string; description?: string }) { return this.http.put<Route>(`${this.base}/routes/${id}`, b); }
  deleteRoute(id: number) { return this.http.delete(`${this.base}/routes/${id}`); }

  // ---- Customers ----
  getCustomers(opts?: { name?: string; phone?: string; routeId?: number; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.name) p = p.set('name', opts.name);
    if (opts?.phone) p = p.set('phone', opts.phone);
    if (opts?.routeId) p = p.set('routeId', opts.routeId);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<Customer>>(`${this.base}/customers`, { params: p });
  }
  /** Full customer list (e.g. order dialog dropdown, dashboard), independent of table paging. */
  getAllCustomers(opts?: { name?: string; phone?: string; routeId?: number }) {
    return this.getCustomers({ ...opts, page: 1, pageSize: 1000 }).pipe(map(r => r.items));
  }
  getCustomerDetails(id: number) { return this.http.get<CustomerSearchResult>(`${this.base}/customers/${id}/details`); }
  getCustomerAdvance(id: number) { return this.http.get<CustomerAdvance>(`${this.base}/customers/${id}/advance`); }
  searchCustomers(query: string) {
    const p = new HttpParams().set('query', query);
    return this.http.get<CustomerSearchResult[]>(`${this.base}/customers/search`, { params: p });
  }
  createCustomer(b: any) { return this.http.post<Customer>(`${this.base}/customers`, b); }
  updateCustomer(id: number, b: any) { return this.http.put<Customer>(`${this.base}/customers/${id}`, b); }
  deleteCustomer(id: number) { return this.http.delete(`${this.base}/customers/${id}`); }

  // ---- Trucks ----
  getTrucks(opts?: { search?: string; withStock?: boolean }) {
    let p = new HttpParams();
    if (opts?.search) p = p.set('search', opts.search);
    if (opts?.withStock) p = p.set('withStock', true);
    return this.http.get<Truck[]>(`${this.base}/trucks`, { params: p });
  }
  getTruckStock(truckId: number) { return this.http.get<TruckStockItem[]>(`${this.base}/trucks/${truckId}/stock`); }
  createTruck(name: string) { return this.http.post<Truck>(`${this.base}/trucks`, { name }); }
  updateTruck(id: number, name: string) { return this.http.put<Truck>(`${this.base}/trucks/${id}`, { name }); }
  deleteTruck(id: number) { return this.http.delete(`${this.base}/trucks/${id}`); }

  // ---- Orders ----
  getOrders(opts?: { orderNumber?: string; customer?: string; orderDate?: string; status?: OrderStatus; paymentStatus?: number; mine?: boolean; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.orderNumber) p = p.set('orderNumber', opts.orderNumber);
    if (opts?.customer) p = p.set('customer', opts.customer);
    if (opts?.orderDate) p = p.set('orderDate', opts.orderDate);
    if (opts?.status != null) p = p.set('status', opts.status);
    if (opts?.paymentStatus != null) p = p.set('paymentStatus', opts.paymentStatus);
    if (opts?.mine) p = p.set('mine', true);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<Order>>(`${this.base}/orders`, { params: p });
  }
  /** Full order list (e.g. dashboard stats), independent of table paging/filters. */
  getAllOrders() {
    return this.getOrders({ page: 1, pageSize: 10000 }).pipe(map(r => r.items));
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
  applyAdvance(orderId: number, amount?: number) {
    return this.http.post<Order>(`${this.base}/payments/apply-advance/${orderId}`, { amount: amount ?? null });
  }
  downloadInvoice(orderId: number) {
    return this.http.get(`${this.base}/orders/${orderId}/invoice`, { responseType: 'blob' });
  }
  emailInvoice(orderId: number) {
    return this.http.post<{ message: string }>(`${this.base}/orders/${orderId}/invoice/email`, {});
  }
  deletePayment(id: number) { return this.http.delete<Order>(`${this.base}/payments/${id}`); }

  // ---- Stock requests ("My Orders") ----
  getStockRequests(opts?: { requestNumber?: string; date?: string; item?: string; status?: number; paymentStatus?: number; page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.requestNumber) p = p.set('requestNumber', opts.requestNumber);
    if (opts?.date) p = p.set('date', opts.date);
    if (opts?.item) p = p.set('item', opts.item);
    if (opts?.status != null) p = p.set('status', opts.status);
    if (opts?.paymentStatus != null) p = p.set('paymentStatus', opts.paymentStatus);
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<StockRequest>>(`${this.base}/stock-requests`, { params: p });
  }
  createStockRequest(b: { notes?: string; items: { itemId: number; quantity: number; unitPrice?: number }[] }) {
    return this.http.post<StockRequest>(`${this.base}/stock-requests`, b);
  }
  updateStockRequest(id: number, b: { notes?: string; items: { itemId: number; quantity: number; unitPrice?: number }[] }) {
    return this.http.put<StockRequest>(`${this.base}/stock-requests/${id}`, b);
  }
  fulfillStockRequest(id: number) { return this.http.put<StockRequest>(`${this.base}/stock-requests/${id}/fulfill`, {}); }
  doneStockRequest(id: number) { return this.http.put<StockRequest>(`${this.base}/stock-requests/${id}/done`, {}); }
  cancelStockRequest(id: number) { return this.http.put<StockRequest>(`${this.base}/stock-requests/${id}/cancel`, {}); }
  deleteStockRequest(id: number) { return this.http.delete(`${this.base}/stock-requests/${id}`); }

  // Stock-request payments
  getRequestPayments(requestId: number) { return this.http.get<StockRequestPayment[]>(`${this.base}/stock-request-payments/by-request/${requestId}`); }
  addRequestPayment(b: { stockRequestId: number; amount: number; paymentDate?: string; method?: string; note?: string }) {
    return this.http.post<StockRequest>(`${this.base}/stock-request-payments`, b);
  }
  settleRequest(requestId: number) { return this.http.post<StockRequest>(`${this.base}/stock-request-payments/settle/${requestId}`, {}); }
  deleteRequestPayment(id: number) { return this.http.delete<StockRequest>(`${this.base}/stock-request-payments/${id}`); }
  getRequestAdvance() { return this.http.get<StockRequestAdvance>(`${this.base}/stock-request-payments/advance`); }
  applyRequestAdvance(requestId: number, amount?: number) {
    return this.http.post<StockRequest>(`${this.base}/stock-request-payments/apply-advance/${requestId}`, { amount: amount ?? null });
  }

  // ---- Reports ----
  monthlyReport(year: number, month: number | undefined, page: number, pageSize: number) {
    let p = new HttpParams().set('year', year).set('page', page).set('pageSize', pageSize);
    if (month) p = p.set('month', month);
    return this.http.get<ReportSummary>(`${this.base}/reports/monthly`, { params: p });
  }
  dailyReport(year: number, month: number, page: number, pageSize: number) {
    const p = new HttpParams().set('year', year).set('month', month).set('page', page).set('pageSize', pageSize);
    return this.http.get<ReportSummary>(`${this.base}/reports/daily`, { params: p });
  }
  yearlyReport(year: number, page: number, pageSize: number) {
    const p = new HttpParams().set('year', year).set('page', page).set('pageSize', pageSize);
    return this.http.get<ReportSummary>(`${this.base}/reports/yearly`, { params: p });
  }
  customerReport(opts?: { page?: number; pageSize?: number }) {
    let p = new HttpParams();
    if (opts?.page) p = p.set('page', opts.page);
    if (opts?.pageSize) p = p.set('pageSize', opts.pageSize);
    return this.http.get<PagedResult<CustomerReportRow>>(`${this.base}/reports/by-customer`, { params: p });
  }
}
