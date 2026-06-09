/*
 * One-off data population script.
 * Inserts ~20 realistic records into each section by calling the running Web API,
 * so the data is stored in SQL Server and served dynamically (no frontend static
 * data, no backend seed code).
 *
 * Usage:  node tools/populate-data.js
 * Requires the API running on http://localhost:5219.
 */

const BASE = process.env.API_BASE || 'http://localhost:5219/api';
const EMAIL = 'sales@salesapp.com';
const PASSWORD = 'Sales@123';

let token = '';
async function api(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${path} -> ${res.status}: ${text}`);
  }
  return res.status === 204 ? null : res.json();
}

const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const pick = (arr) => arr[rand(0, arr.length - 1)];

// ---- Source data ----
const CATEGORIES = [
  ['Beverages', 'Drinks and refreshments'],
  ['Snacks', 'Packaged snacks'],
  ['Dairy', 'Milk and dairy products'],
  ['Bakery', 'Breads and baked goods'],
  ['Grains & Pulses', 'Rice, wheat, lentils'],
  ['Spices & Masala', 'Cooking spices'],
  ['Frozen Foods', 'Frozen items'],
  ['Cleaning Supplies', 'Household cleaning'],
  ['Personal Care', 'Hygiene and grooming'],
  ['Stationery', 'Office and school supplies'],
  ['Cooking Oils', 'Edible oils and ghee'],
  ['Confectionery', 'Sweets and chocolates'],
  ['Health & Wellness', 'Supplements and care'],
  ['Baby Care', 'Baby products'],
  ['Pet Supplies', 'Pet food and care'],
  ['Home Care', 'Home essentials'],
  ['Tea & Coffee', 'Hot beverages'],
  ['Canned Foods', 'Preserved foods'],
  ['Sauces & Spreads', 'Condiments'],
  ['Dry Fruits & Nuts', 'Nuts and dried fruit'],
];

const SUBCATS = {
  'Beverages': ['Juices', 'Soft Drinks'],
  'Snacks': ['Chips', 'Biscuits'],
  'Dairy': ['Milk', 'Cheese'],
  'Bakery': ['Bread', 'Cookies'],
  'Grains & Pulses': ['Rice', 'Lentils'],
  'Spices & Masala': ['Whole Spices', 'Ground Masala'],
  'Frozen Foods': ['Frozen Veg', 'Ice Cream'],
  'Cleaning Supplies': ['Detergents', 'Floor Cleaners'],
  'Personal Care': ['Soaps', 'Shampoos'],
  'Stationery': ['Notebooks', 'Pens'],
};

const ITEM_NAMES = [
  'Mango Juice', 'Orange Soda', 'Potato Chips', 'Cream Biscuits', 'Toned Milk',
  'Cheddar Cheese', 'Whole Wheat Bread', 'Butter Cookies', 'Basmati Rice', 'Toor Dal',
  'Black Pepper', 'Garam Masala', 'Frozen Peas', 'Vanilla Ice Cream', 'Liquid Detergent',
  'Floor Cleaner', 'Bath Soap', 'Anti-Dandruff Shampoo', 'A4 Notebook', 'Gel Pen Pack',
];
const UNITS = ['pcs', 'box', 'kg', 'pack', 'litre', 'dozen'];

const FIRST = ['Aarav', 'Vivaan', 'Diya', 'Ananya', 'Kabir', 'Ishaan', 'Riya', 'Aditya', 'Meera', 'Rohan',
  'Sneha', 'Arjun', 'Pooja', 'Karan', 'Nisha', 'Manoj', 'Priya', 'Rahul', 'Sunita', 'Vikram'];
const LAST = ['Traders', 'Stores', 'Enterprises', 'Mart', 'Distributors', 'Agencies', 'Brothers', 'Suppliers'];
const CITIES = ['MG Road', 'Market Street', 'Station Road', 'Gandhi Nagar', 'Ring Road', 'Civil Lines'];
const METHODS = ['Cash', 'UPI', 'Bank', 'Cheque'];

function isoDate(daysFromNow) {
  const base = new Date(2026, 0, 1).getTime() + daysFromNow * 86400000;
  const d = new Date(base);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

(async () => {
  console.log('Logging in...');
  const login = await api('POST', '/auth/login', { email: EMAIL, password: PASSWORD });
  token = login.token;
  console.log('OK\n');

  // 1) Categories
  const categories = [];
  for (const [name, description] of CATEGORIES) {
    categories.push(await api('POST', '/categories', { name, description }));
  }
  console.log(`Categories created: ${categories.length}`);

  // 2) Sub-categories (>=20, spread across categories)
  const subCategories = [];
  for (const cat of categories) {
    const subs = SUBCATS[cat.name] || ['General'];
    for (const s of subs) {
      subCategories.push(await api('POST', '/subcategories', { categoryId: cat.id, name: s, description: `${s} under ${cat.name}` }));
    }
  }
  console.log(`Sub-categories created: ${subCategories.length}`);

  // 3) Items / inventory (20)
  const items = [];
  for (let i = 0; i < 20; i++) {
    const sub = pick(subCategories);
    items.push(await api('POST', '/items', {
      subCategoryId: sub.id,
      name: ITEM_NAMES[i],
      sku: `SKU-${String(i + 1).padStart(3, '0')}`,
      unit: pick(UNITS),
      stockQuantity: rand(50, 500),
      unitPrice: rand(20, 1200),
    }));
  }
  console.log(`Items created: ${items.length}`);

  // 4) Customers (20)
  const customers = [];
  for (let i = 0; i < 20; i++) {
    customers.push(await api('POST', '/customers', {
      name: `${FIRST[i]} ${pick(LAST)}`,
      phone: `9${rand(100000000, 999999999)}`,
      email: `${FIRST[i].toLowerCase()}${i}@example.com`,
      address: `${rand(1, 200)}, ${pick(CITIES)}`,
    }));
  }
  console.log(`Customers created: ${customers.length}`);

  // 5) Orders (20) with varied delivery dates, statuses and payments
  const statuses = [0, 1, 2, 3, 4];
  let orderCount = 0, paymentCount = 0;
  for (let i = 0; i < 20; i++) {
    const cust = pick(customers);
    const lineCount = rand(1, 3);
    const used = new Set();
    const lines = [];
    for (let j = 0; j < lineCount; j++) {
      const it = pick(items);
      if (used.has(it.id)) continue;
      used.add(it.id);
      lines.push({ itemId: it.id, quantity: rand(1, 10), unitPrice: it.unitPrice });
    }
    const order = await api('POST', '/orders', {
      customerId: cust.id,
      orderDate: isoDate(rand(0, 150)),
      deliveryDate: Math.random() > 0.3 ? isoDate(rand(150, 175)) : null,
      notes: pick(['Urgent', 'Regular order', 'Handle with care', '']),
      items: lines,
    });
    orderCount++;

    await api('PUT', `/orders/${order.id}/status`, { status: pick(statuses) });

    const roll = Math.random();
    if (roll > 0.66) {
      await api('POST', '/payments', { orderId: order.id, amount: order.totalAmount, method: pick(METHODS), note: 'Full payment' });
      paymentCount++;
    } else if (roll > 0.33) {
      await api('POST', '/payments', { orderId: order.id, amount: Math.round(order.totalAmount * 0.4), method: pick(METHODS), note: 'Advance' });
      paymentCount++;
    }
  }
  console.log(`Orders created: ${orderCount} (payments recorded on ${paymentCount})`);

  // 6) Dispatches (20) — respect available stock
  let dispatchCount = 0;
  const freshItems = await api('GET', '/items');
  const stockMap = new Map(freshItems.map((it) => [it.id, it.stockQuantity]));
  for (let i = 0; i < 20; i++) {
    const lineCount = rand(1, 2);
    const used = new Set();
    const lines = [];
    for (let j = 0; j < lineCount; j++) {
      const it = pick(freshItems);
      if (used.has(it.id)) continue;
      const avail = stockMap.get(it.id);
      if (avail < 2) continue;
      const qty = rand(1, Math.min(10, avail));
      used.add(it.id);
      stockMap.set(it.id, avail - qty);
      lines.push({ itemId: it.id, quantity: qty });
    }
    if (!lines.length) continue;
    await api('POST', '/dispatch', { truckLabel: 'Truck-1', notes: `Dispatch run #${i + 1}`, items: lines });
    dispatchCount++;
  }
  console.log(`Dispatches created: ${dispatchCount}`);

  console.log('\nDone. Refresh the app to see the data.');
})().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
