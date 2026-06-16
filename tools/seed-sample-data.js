// Seeds 20 realistic sample records per module via the running API.
// Usage:  node tools/seed-sample-data.js [userId]
//   Mints a JWT with the app's own signing key (appsettings) for the given user id (default 1),
//   so the data is owned by that account without needing its password.
const fs = require('fs');
const crypto = require('crypto');
const USER_ID = Number(process.argv[2]) || Number(process.env.SEED_USER_ID) || 1;
const BASE = process.env.API_BASE || 'http://localhost:5219/api';

function b64url(s) { return Buffer.from(s).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_'); }
function mintToken(userId) {
  const cfg = JSON.parse(fs.readFileSync('backend/SalesApp.Api/appsettings.json', 'utf8')).Jwt;
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({
    sub: String(userId), nameid: String(userId),
    'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier': String(userId),
    iss: cfg.Issuer, aud: cfg.Audience, iat: now, exp: now + 3600, jti: crypto.randomUUID(),
  }));
  const sig = b64url(crypto.createHmac('sha256', cfg.Key).update(`${header}.${payload}`).digest());
  return `${header}.${payload}.${sig}`;
}

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
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const rint = (lo, hi) => lo + Math.floor(Math.random() * (hi - lo + 1));
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '');

// ---- realistic reference data (20 each) ----
const ROUTES = ['Andheri East', 'Bandra West', 'Borivali', 'Dadar', 'Thane', 'Navi Mumbai', 'Powai', 'Malad',
  'Goregaon', 'Kurla', 'Chembur', 'Vashi', 'Mulund', 'Ghatkopar', 'Sion', 'Worli', 'Colaba', 'Juhu', 'Versova', 'Kandivali'];

// [category, sub-category, item, unit, price]
const CATALOG = [
  ['Beverages', 'Soft Drinks', 'Coca-Cola 750ml', 'pcs', 40],
  ['Snacks', 'Chips', "Lay's Classic 52g", 'pcs', 20],
  ['Dairy', 'Milk & Curd', 'Amul Gold Milk 1L', 'pcs', 66],
  ['Bakery', 'Bread', 'Britannia Brown Bread', 'pcs', 45],
  ['Staples', 'Atta & Flour', 'Aashirvaad Atta 5kg', 'bag', 270],
  ['Pulses', 'Dal', 'Toor Dal 1kg', 'kg', 140],
  ['Grains', 'Rice', 'India Gate Basmati 5kg', 'bag', 650],
  ['Cooking Oils', 'Edible Oil', 'Fortune Sunflower Oil 1L', 'pcs', 150],
  ['Spices', 'Ground Spices', 'Everest Turmeric 200g', 'pcs', 60],
  ['Tea & Coffee', 'Tea', 'Tata Tea Gold 500g', 'pcs', 260],
  ['Confectionery', 'Biscuits', 'Parle-G 800g', 'pcs', 80],
  ['Personal Care', 'Bath Soap', 'Dove Soap 100g', 'pcs', 55],
  ['Home Care', 'Detergent', 'Surf Excel 1kg', 'pcs', 130],
  ['Cleaning Supplies', 'Floor Cleaner', 'Lizol 975ml', 'pcs', 185],
  ['Frozen Foods', 'Frozen Snacks', 'McCain French Fries 420g', 'pack', 99],
  ['Baby Care', 'Diapers', 'Pampers M 32s', 'pack', 599],
  ['Health & Wellness', 'Health Drinks', 'Horlicks 500g', 'pcs', 245],
  ['Stationery', 'Notebooks', 'Classmate Notebook 180pg', 'pcs', 50],
  ['Pet Care', 'Dog Food', 'Pedigree Adult 1.2kg', 'pack', 260],
  ['Packaged Foods', 'Instant Noodles', 'Maggi Masala 12-pack', 'pack', 168],
];

const CUSTOMERS = ['Sharma General Store', 'Gupta Provision Mart', 'Annapurna Kirana', 'Krishna Super Market',
  'Patel Wholesale', 'Reliance Fresh Corner', 'Maa Vaishno Stores', 'Sai Traders', 'New Bombay Mart',
  'Sri Balaji Stores', 'Janta Kirana', 'Bhavani Provisions', 'Royal Departmental', 'Galaxy Super Bazaar',
  'Green Valley Grocers', 'Anand General Store', 'Lakshmi Stores', 'Metro Cash & Carry', 'Daily Needs Mart', 'Sunrise Provisions'];

const ORDER_NOTES = ['Monthly restock', 'Weekly order', 'Festival stock', 'Urgent delivery', 'Regular supply', 'Bulk order'];
const REQ_NOTES = ['Low stock refill', 'New product line', 'Replenishment', 'Seasonal demand', 'Fast-moving items'];
const DISPATCH_NOTES = ['Morning route delivery', 'Truck loaded for east zone', 'Daily dispatch', 'Priority delivery'];

async function main() {
  console.log(`Seeding realistic data on ${BASE} for user id ${USER_ID} ...`);
  token = mintToken(USER_ID);

  const routes = [];
  for (const r of ROUTES) routes.push(await api('POST', '/routes', { name: `${r} Route`, description: `${r} delivery zone` }));
  console.log(`✓ ${routes.length} routes`);

  const cats = [], subs = [], items = [];
  for (const [cat, sub, item, unit, price] of CATALOG) {
    const c = await api('POST', '/categories', { name: cat, description: `${cat} products` });
    cats.push(c);
    const s = await api('POST', '/subcategories', { categoryId: c.id, name: sub, description: `${sub} (${cat})` });
    subs.push(s);
    const it = await api('POST', '/items', {
      subCategoryId: s.id, name: item, sku: `SKU-${slug(item).slice(0, 6).toUpperCase()}`,
      unit, stockQuantity: rint(200, 1500), unitPrice: price,
    });
    items.push(it);
  }
  console.log(`✓ ${cats.length} categories, ${subs.length} sub-categories, ${items.length} items`);

  const customers = [];
  for (let i = 0; i < CUSTOMERS.length; i++) {
    const name = CUSTOMERS[i];
    customers.push(await api('POST', '/customers', {
      name, phone: `9${rint(700000000, 899999999)}`, email: `${slug(name)}@example.com`,
      address: `${name}, ${ROUTES[i]}, Mumbai`, routeId: routes[i].id,
    }));
  }
  console.log(`✓ ${customers.length} customers`);

  const states = ['MH', 'GJ', 'KA', 'DL', 'RJ'];
  const trucks = [];
  for (let i = 1; i <= 20; i++) {
    const plate = `${pick(states)}-${rint(1, 49).toString().padStart(2, '0')}-${String.fromCharCode(65 + rint(0, 25))}${String.fromCharCode(65 + rint(0, 25))}-${rint(1000, 9999)}`;
    trucks.push(await api('POST', '/trucks', { name: plate }));
  }
  console.log(`✓ ${trucks.length} trucks`);

  let orders = 0;
  for (let i = 0; i < 20; i++) {
    const chosen = pickItems(items, rint(1, 3));
    await api('POST', '/orders', { customerId: customers[i].id, source: 0, notes: pick(ORDER_NOTES),
      items: chosen.map((it) => ({ itemId: it.id, quantity: rint(2, 12) })) });
    orders++;
  }
  console.log(`✓ ${orders} customer orders`);

  let reqs = 0;
  for (let i = 0; i < 20; i++) {
    const chosen = pickItems(items, rint(1, 2));
    await api('POST', '/stock-requests', { notes: pick(REQ_NOTES),
      items: chosen.map((it) => ({ itemId: it.id, quantity: rint(10, 50) })) });
    reqs++;
  }
  console.log(`✓ ${reqs} stock requests`);

  let disp = 0;
  for (let i = 0; i < 20; i++) {
    const chosen = pickItems(items, rint(1, 2));
    // distinct truck per dispatch avoids same-truck/day merge, so all 20 are separate records
    await api('POST', '/dispatch', { truckLabel: trucks[i].name, notes: pick(DISPATCH_NOTES),
      items: chosen.map((it) => ({ itemId: it.id, quantity: rint(2, 10) })) });
    disp++;
  }
  console.log(`✓ ${disp} dispatches`);

  console.log('Done.');
}
function pickItems(items, count) {
  const copy = [...items];
  const out = [];
  for (let i = 0; i < count && copy.length; i++) out.push(copy.splice(Math.floor(Math.random() * copy.length), 1)[0]);
  return out;
}
main().catch((e) => { console.error('SEED FAILED:', e.message); process.exit(1); });
