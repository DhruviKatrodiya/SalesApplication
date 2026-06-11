/*
 * Sample data seeder — 50 records per module for the current year (2026),
 * for testing and reporting. Calls the running Web API so all business rules
 * (totals, payment status, stock, order numbers) are applied.
 *
 * Usage:  node tools/seed-sample-data.js
 * Requires the API running on http://localhost:5219.
 */

const BASE = process.env.API_BASE || 'http://localhost:5219/api';
const EMAIL = process.env.SEED_EMAIL || 'dhruvikatrodiya01@gmail.com';
const PASSWORD = process.env.SEED_PASSWORD || 'Sales@123';
const YEAR = 2026;
const N = 50;

let token = '';
async function api(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${await res.text()}`);
  return res.status === 204 ? null : res.json();
}
// PagedResult-aware GET (returns the items array).
const items = (r) => (Array.isArray(r) ? r : r.items);

const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const pick = (arr) => arr[rand(0, arr.length - 1)];
const pad = (n, w = 3) => String(n).padStart(w, '0');

/** A date spread across the current year (for monthly/daily reports). */
function dateInYear() {
  const month = rand(1, 12);
  const day = rand(1, 28);
  return `${YEAR}-${pad(month, 2)}-${pad(day, 2)}`;
}

const CAT_BASE = ['Beverages', 'Snacks', 'Dairy', 'Bakery', 'Grains', 'Spices', 'Frozen', 'Cleaning',
  'Personal Care', 'Stationery', 'Cooking Oils', 'Confectionery', 'Health', 'Baby Care', 'Pet Supplies',
  'Home Care', 'Tea & Coffee', 'Canned', 'Sauces', 'Dry Fruits'];
const SUB_BASE = ['General', 'Premium', 'Economy', 'Bulk', 'Retail', 'Imported', 'Local', 'Organic'];
const ITEM_BASE = ['Mango Juice', 'Orange Soda', 'Potato Chips', 'Cream Biscuits', 'Toned Milk', 'Cheddar',
  'Wheat Bread', 'Butter Cookies', 'Basmati Rice', 'Toor Dal', 'Black Pepper', 'Garam Masala', 'Frozen Peas',
  'Ice Cream', 'Detergent', 'Floor Cleaner', 'Bath Soap', 'Shampoo', 'Notebook', 'Gel Pen'];
const UNITS = ['pcs', 'box', 'kg', 'pack', 'litre', 'dozen'];
const FIRST = ['Aarav', 'Vivaan', 'Diya', 'Ananya', 'Kabir', 'Ishaan', 'Riya', 'Aditya', 'Meera', 'Rohan',
  'Sneha', 'Arjun', 'Pooja', 'Karan', 'Nisha', 'Manoj', 'Priya', 'Rahul', 'Sunita', 'Vikram'];
const LAST = ['Traders', 'Stores', 'Enterprises', 'Mart', 'Distributors', 'Agencies', 'Brothers', 'Suppliers'];
const CITIES = ['MG Road', 'Market Street', 'Station Road', 'Gandhi Nagar', 'Ring Road', 'Civil Lines'];
const METHODS = ['Cash', 'UPI', 'Bank', 'Cheque'];
const ROUTE_AREAS = ['North', 'South', 'East', 'West', 'Central', 'Uptown', 'Downtown', 'Riverside', 'Hillside', 'Lakeview'];

(async () => {
  console.log(`Seeding ${N} records/module for ${YEAR}...\nLogging in as ${EMAIL}...`);
  token = (await api('POST', '/auth/login', { email: EMAIL, password: PASSWORD })).token;
  console.log('OK\n');

  // 1) Routes
  const routes = [];
  for (let i = 0; i < N; i++)
    routes.push(await api('POST', '/routes', { name: `${ROUTE_AREAS[i % ROUTE_AREAS.length]} Route ${i + 1}`, description: `Delivery route ${i + 1}` }));
  console.log(`Routes: ${routes.length}`);

  // 2) Categories
  const categories = [];
  for (let i = 0; i < N; i++)
    categories.push(await api('POST', '/categories', { name: `${CAT_BASE[i % CAT_BASE.length]} ${i + 1}`, description: `Category ${i + 1}` }));
  console.log(`Categories: ${categories.length}`);

  // 3) Sub-categories (one per category)
  const subCategories = [];
  for (let i = 0; i < N; i++) {
    const cat = categories[i];
    subCategories.push(await api('POST', '/subcategories', { categoryId: cat.id, name: `${SUB_BASE[i % SUB_BASE.length]} ${i + 1}`, description: `Sub ${i + 1}` }));
  }
  console.log(`Sub-categories: ${subCategories.length}`);

  // 4) Items (high stock so orders/dispatches don't run out)
  const inv = [];
  for (let i = 0; i < N; i++) {
    inv.push(await api('POST', '/items', {
      subCategoryId: pick(subCategories).id,
      name: `${ITEM_BASE[i % ITEM_BASE.length]} ${i + 1}`,
      sku: `SKU-${pad(i + 1)}`,
      unit: pick(UNITS),
      stockQuantity: rand(2000, 6000),
      unitPrice: rand(20, 1200),
    }));
  }
  console.log(`Items: ${inv.length}`);

  // 5) Customers (phone required; some assigned to a route)
  const customers = [];
  for (let i = 0; i < N; i++) {
    customers.push(await api('POST', '/customers', {
      name: `${FIRST[i % FIRST.length]} ${pick(LAST)} ${i + 1}`,
      phone: `9${rand(100000000, 999999999)}`,
      email: `cust${i + 1}@example.com`,
      address: `${rand(1, 200)}, ${pick(CITIES)}`,
      routeId: Math.random() > 0.3 ? pick(routes).id : null,
    }));
  }
  console.log(`Customers: ${customers.length}`);

  // 6) Orders (dates across the year; varied status + payments for reporting)
  const statuses = [1, 2, 3]; // Dispatched, Delivered, Completed
  let paid = 0;
  for (let i = 0; i < N; i++) {
    const used = new Set();
    const lines = [];
    for (let j = 0; j < rand(1, 4); j++) {
      const it = pick(inv);
      if (used.has(it.id)) continue;
      used.add(it.id);
      lines.push({ itemId: it.id, quantity: rand(1, 8), unitPrice: it.unitPrice });
    }
    const order = await api('POST', '/orders', {
      customerId: pick(customers).id,
      orderDate: dateInYear(),
      deliveryDate: Math.random() > 0.3 ? dateInYear() : null,
      notes: pick(['Urgent', 'Regular order', 'Handle with care', '']),
      source: 0,
      items: lines,
    });

    // ~70% get a delivery status (so payment can read as Partial when partly paid)
    if (Math.random() > 0.3) await api('PUT', `/orders/${order.id}/status`, { status: pick(statuses) });

    const roll = Math.random();
    if (roll > 0.6) { await api('POST', '/payments', { orderId: order.id, amount: order.totalAmount, method: pick(METHODS), note: 'Full payment' }); paid++; }
    else if (roll > 0.3) { await api('POST', '/payments', { orderId: order.id, amount: Math.max(1, Math.round(order.totalAmount * 0.4)), method: pick(METHODS), note: 'Advance' }); paid++; }
  }
  console.log(`Orders: ${N} (payments on ${paid})`);

  // 7) Dispatches (distinct truck labels so same-day entries don't merge)
  let dispatches = 0;
  for (let i = 0; i < N; i++) {
    const used = new Set();
    const lines = [];
    for (let j = 0; j < rand(1, 3); j++) {
      const it = pick(inv);
      if (used.has(it.id)) continue;
      used.add(it.id);
      lines.push({ itemId: it.id, quantity: rand(1, 20) });
    }
    if (!lines.length) continue;
    await api('POST', '/dispatch', { truckLabel: `Truck-${i + 1}`, notes: `Dispatch run #${i + 1}`, items: lines });
    dispatches++;
  }
  console.log(`Dispatches: ${dispatches}`);

  // 8) My Orders (stock requests) — varied status + payments
  let reqs = 0;
  for (let i = 0; i < N; i++) {
    const used = new Set();
    const lines = [];
    for (let j = 0; j < rand(1, 3); j++) {
      const it = pick(inv);
      if (used.has(it.id)) continue;
      used.add(it.id);
      lines.push({ itemId: it.id, quantity: rand(1, 15), unitPrice: it.unitPrice });
    }
    const req = await api('POST', '/stock-requests', { notes: pick(['Restock', 'Low stock', 'Monthly order', '']), items: lines });
    reqs++;

    const roll = Math.random();
    if (roll > 0.5) {
      // pay something, then fulfill (and sometimes mark done)
      if (Math.random() > 0.5) await api('POST', '/stock-request-payments', { stockRequestId: req.id, amount: Math.max(1, Math.round(req.totalAmount * (Math.random() > 0.5 ? 1 : 0.5))), method: pick(METHODS) });
      await api('PUT', `/stock-requests/${req.id}/fulfill`, {});
      if (Math.random() > 0.6) await api('PUT', `/stock-requests/${req.id}/done`, {});
    }
  }
  console.log(`My Orders (stock requests): ${reqs}`);

  console.log('\nDone. Refresh the app to see the data.');
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
