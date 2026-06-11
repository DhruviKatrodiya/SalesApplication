// ---- Enums (numeric, mirror the backend) ----
export enum OrderStatus { Pending = 0, Dispatched = 1, Delivered = 2, Completed = 3, Remaining = 4 }
export enum PaymentStatus { Pending = 0, Advance = 1, Paid = 2, Partial = 3 }
export enum ReceivedStatus { Pending = 0, Remaining = 1, Completed = 2 }
export enum OrderSource { Inventory = 0, Dispatch = 1 }

export const OrderStatusLabels: Record<number, string> = {
  0: 'Pending', 1: 'Dispatched', 2: 'Delivered', 3: 'Completed', 4: 'Remaining'
};
export const PaymentStatusLabels: Record<number, string> = {
  0: 'Pending', 1: 'Advance', 2: 'Paid', 3: 'Partial'
};
export const ReceivedStatusLabels: Record<number, string> = {
  0: 'Pending', 1: 'Remaining', 2: 'Completed'
};

// ---- Auth / user ----
export interface User {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  profileImagePath?: string;
}
export interface LoginResponse { token: string; user: User; }

// ---- Pagination ----
export interface PagedResult<T> { items: T[]; total: number; page: number; pageSize: number; }

// ---- Dispatch draft (unsaved cart); each line carries its truck ----
export interface DispatchDraftItem { itemId: number; quantity: number; truckLabel?: string | null; }
export interface DispatchDraft { truckLabel?: string | null; notes?: string | null; items: DispatchDraftItem[]; }

// ---- Catalog ----
export interface Category { id: number; name: string; description?: string; subCategoryCount: number; }
export interface SubCategory {
  id: number; categoryId: number; categoryName: string; name: string; description?: string; itemCount: number;
}
export interface Item {
  id: number; subCategoryId: number; subCategoryName: string; categoryName: string;
  name: string; sku?: string; unit?: string; stockQuantity: number; dispatchStock: number; unitPrice: number;
}

// ---- Dispatch ----
export interface DispatchItem { itemId: number; itemName: string; quantity: number; }
export interface Dispatch {
  id: number; dispatchDate: string; truckLabel: string; notes?: string; items: DispatchItem[];
}

// ---- Customers ----
export interface Route { id: number; name: string; description?: string; customerCount: number; }
export interface Customer {
  id: number; name: string; phone?: string; email?: string; address?: string;
  routeId?: number | null; routeName?: string | null; createdAt: string;
}
export interface CustomerSearchResult {
  customer: Customer;
  totalOrdered: number; totalPaid: number; totalRemaining: number;
  overallPaymentStatus: string;
  pendingOrders: number; deliveredOrders: number;
  orders: Order[];
}

// ---- Orders ----
export interface OrderItem {
  id: number; itemId: number; itemName: string;
  quantity: number; unitPrice: number; lineTotal: number; receivedStatus: ReceivedStatus;
}
export interface Order {
  id: number; orderNumber: string; customerId: number; customerName: string;
  orderDate: string; deliveryDate?: string;
  status: OrderStatus; paymentStatus: PaymentStatus; source: OrderSource;
  totalAmount: number; paidAmount: number; remainingAmount: number;
  notes?: string; items: OrderItem[];
}

// ---- Payments ----
export interface Payment {
  id: number; orderId: number; amount: number; paymentDate: string; method?: string; note?: string;
}

// ---- Reports ----
export interface ReportRow {
  label: string; orderCount: number; totalAmount: number; paidAmount: number; remainingAmount: number;
}
export interface ReportSummary {
  period: string; totalOrders: number;
  totalAmount: number; totalPaid: number; totalRemaining: number;
  pendingOrders: number; deliveredOrders: number; rowsTotal: number; rows: ReportRow[];
}
export interface CustomerReportRow {
  customerId: number; customerName: string;
  totalOrders: number; pendingOrders: number; deliveredOrders: number;
  totalAmount: number; paidAmount: number; remainingAmount: number;
}
