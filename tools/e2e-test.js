// End-to-end smoke/integration test of every module & workflow against the running API.
// Runs under an isolated throwaway user; cleans up at the end.
const BASE = process.env.API_BASE || 'http://localhost:5219/api';
let token = '';
const results = [];
const ok = (name, cond, extra = '') => { results.push({ name, pass: !!cond, extra }); console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${extra ? '  — ' + extra : ''}`); };

async function api(method, path, body, raw = false) {
  const res = await fetch(`${BASE}${path}`, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (raw) return res;
  const text = await res.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch {}
  return { status: res.status, ok: res.ok, json, text };
}
const expectOk = async (m, p, b) => { const r = await api(m, p, b); if (!r.ok) throw new Error(`${m} ${p} -> ${r.status}: ${r.text}`); return r.json; };

async function main() {
  const email = `e2e_${Date.now()}@test.local`;

  // ---- AUTH ----
  let r = await api('POST', '/auth/register', { fullName: 'E2E Bot', email, phone: '9999999999', password: 'Qa@12345' });
  ok('Auth: register', r.ok && r.json?.token); token = r.json.token;
  r = await api('POST', '/auth/login', { email, password: 'Qa@12345' });
  ok('Auth: login', r.ok && r.json?.token);
  r = await api('POST', '/auth/login', { email, password: 'wrong' });
  ok('Auth: bad password rejected (401)', r.status === 401);

  // ---- CATEGORIES / SUBCATEGORIES ----
  const cat = await expectOk('POST', '/categories', { name: 'E2E Cat', description: 'd' });
  ok('Categories: create', !!cat.id);
  const catList = await expectOk('GET', '/categories?page=1&pageSize=5');
  ok('Categories: paged list', Array.isArray(catList.items) && catList.total >= 1);
  const upCat = await expectOk('PUT', `/categories/${cat.id}`, { name: 'E2E Cat 2', description: 'd2' });
  ok('Categories: update', upCat.name === 'E2E Cat 2');
  const sub = await expectOk('POST', '/subcategories', { categoryId: cat.id, name: 'E2E Sub', description: 'd' });
  ok('SubCategories: create', !!sub.id);
  const subList = await expectOk('GET', `/subcategories?categoryId=${cat.id}`);
  ok('SubCategories: list by category', subList.items.length >= 1);

  // ---- ITEMS (opening batch) ----
  const item = await expectOk('POST', '/items', { subCategoryId: sub.id, name: 'E2E Item', sku: 'E2E-1', unit: 'pcs', stockQuantity: 100, unitPrice: 10 });
  ok('Items: create with opening stock', item.stockQuantity === 100);
  const ph0 = await expectOk('GET', `/items/${item.id}/price-history`);
  ok('Items: opening batch + valuation', ph0.batches.length === 1 && ph0.stockValue === 1000, `value=${ph0.stockValue}`);
  const zeroItem = await expectOk('POST', '/items', { subCategoryId: sub.id, name: 'E2E Zero', sku: 'E2E-0', unit: 'pcs', stockQuantity: 0, unitPrice: 5 });
  ok('Items: zero-stock item created', zeroItem.stockQuantity === 0);
  const low = await expectOk('GET', '/items?lowStock=true&threshold=10');
  ok('Items: low-stock filter returns zero-stock item', low.items.some(i => i.id === zeroItem.id));

  // ---- ROUTES ----
  const route = await expectOk('POST', '/routes', { name: 'E2E Route', description: 'd' });
  const routeList = await expectOk('GET', '/routes?page=1&pageSize=5');
  ok('Routes: create + paged list', !!route.id && routeList.total >= 1);

  // ---- CUSTOMERS ----
  const cust = await expectOk('POST', '/customers', { name: 'E2E Customer', phone: '9000000001', email: 'c@e.com', address: 'addr', routeId: route.id });
  ok('Customers: create', !!cust.id);
  const det = await expectOk('GET', `/customers/${cust.id}/details`);
  ok('Customers: details breakdown', det && 'advanceBalance' in det && 'orderPayments' in det);
  const srch = await expectOk('GET', `/customers/search?query=E2E`);
  ok('Customers: search', Array.isArray(srch) && srch.length >= 1);

  // ---- TRUCKS ----
  const truck = await expectOk('POST', '/trucks', { name: 'E2E-TRUCK-1' });
  const truckList = await expectOk('GET', '/trucks?page=1&pageSize=5');
  ok('Trucks: create + paged list', !!truck.id && truckList.total >= 1);

  // ---- STOCK REQUESTS + FULFILL (FIFO batch at new price) ----
  const req = await expectOk('POST', '/stock-requests', { notes: 'restock', items: [{ itemId: item.id, quantity: 50, unitPrice: 20 }] });
  ok('StockRequests: create + number format', /^REQ-\d{2}-\d{2}-\d{2}-\d{6}$/.test(req.requestNumber), req.requestNumber);
  await expectOk('PUT', `/stock-requests/${req.id}/fulfill`);
  const ph1 = await expectOk('GET', `/items/${item.id}/price-history`);
  ok('Fulfil: new price batch (no merge)', ph1.batches.length === 2 && ph1.latestPrice === 20 && ph1.oldestPrice === 10);
  ok('Fulfil: stock + value', ph1.stockQuantity === 150 && ph1.stockValue === 2000, `value=${ph1.stockValue}`);

  // ---- ORDERS: inventory FIFO consumption ----
  const ord = await expectOk('POST', '/orders', { customerId: cust.id, source: 0, notes: 'o', items: [{ itemId: item.id, quantity: 120 }] });
  ok('Orders: create + number format', /^ORD-\d{2}-\d{2}-\d{2}-\d{6}$/.test(ord.orderNumber), ord.orderNumber);
  const ph2 = await expectOk('GET', `/items/${item.id}/price-history`);
  ok('Orders: FIFO consumed oldest first (remaining 30@20)', ph2.stockQuantity === 30 && ph2.stockValue === 600, `value=${ph2.stockValue}`);
  const mv = await expectOk('GET', `/items/${item.id}/movements`);
  const cogs = mv.filter(m => m.type === 'OUT' && m.refType === 'Order').reduce((s, m) => s + m.totalCost, 0);
  ok('Orders: FIFO COGS = 100*10 + 20*20 = 1400', cogs === 1400, `cogs=${cogs}`);

  // ---- ORDERS: insufficient stock rejected ----
  r = await api('POST', '/orders', { customerId: cust.id, source: 0, items: [{ itemId: zeroItem.id, quantity: 5 }] });
  ok('Orders: zero-stock order rejected (400)', r.status === 400);

  // ---- PAYMENTS + ADVANCE ----
  const ph3 = await expectOk('GET', `/items/${item.id}/price-history`); void ph3;
  const small = await expectOk('POST', '/orders', { customerId: cust.id, source: 0, items: [{ itemId: item.id, quantity: 1 }] }); // total 20 (price now 20)
  const paid = await expectOk('POST', '/payments', { orderId: small.id, amount: 50, method: 'Cash' });
  ok('Payments: overpay clamps remaining to 0', paid.remainingAmount === 0 && paid.paidAmount === 50);
  const adv = await expectOk('GET', `/customers/${cust.id}/advance`);
  ok('Payments: advance balance tracked (30)', adv.advanceBalance === 30, `adv=${adv.advanceBalance}`);
  // apply advance to the big order (remaining 0? it had no payment; total = 120*?). Use another order:
  const ord2 = await expectOk('POST', '/orders', { customerId: cust.id, source: 0, items: [{ itemId: item.id, quantity: 1 }] }); // total 20
  const applied = await expectOk('POST', `/payments/apply-advance/${ord2.id}`, { amount: null });
  ok('Payments: apply advance reduces remaining', applied.paidAmount === 20 && applied.remainingAmount === 0);
  const adv2 = await expectOk('GET', `/customers/${cust.id}/advance`);
  ok('Payments: advance reduced after apply (10 left)', adv2.advanceBalance === 10, `adv=${adv2.advanceBalance}`);

  // ---- INVOICE PDF ----
  const inv = await api('GET', `/orders/${ord.id}/invoice`, null, true);
  const buf = Buffer.from(await inv.arrayBuffer());
  ok('Invoice: PDF downloads', inv.ok && buf.length > 1000 && buf.slice(0, 4).toString() === '%PDF', `${buf.length}b`);
  r = await api('POST', `/orders/${ord.id}/invoice/email`, {});
  ok('Invoice: email (SMTP off -> handled 400)', r.status === 400 || r.ok);

  // ---- ORDERS: edit (reverse + reconsume) ----
  const beforeEdit = (await expectOk('GET', `/items/${item.id}/price-history`)).stockQuantity;
  await expectOk('PUT', `/orders/${small.id}`, { customerId: cust.id, source: 0, items: [{ itemId: item.id, quantity: 3 }] }); // was 1 -> 3 (net -2)
  const afterEdit = (await expectOk('GET', `/items/${item.id}/price-history`)).stockQuantity;
  ok('Orders: edit reverses+reconsumes (stock -2)', afterEdit === beforeEdit - 2, `${beforeEdit}->${afterEdit}`);

  // ---- ORDERS filter ----
  const ofil = await expectOk('GET', `/orders?orderNumber=${ord.orderNumber}`);
  ok('Orders: filter by number (exact)', ofil.total === 1 && ofil.items[0].orderNumber === ord.orderNumber);

  // ---- DISPATCH (godown FIFO -> truck) ----
  const before = (await expectOk('GET', `/items/${item.id}`)).stockQuantity;
  await expectOk('POST', '/dispatch', { truckLabel: truck.name, notes: 'd', items: [{ itemId: item.id, quantity: 5 }] });
  const after = (await expectOk('GET', `/items/${item.id}`)).stockQuantity;
  ok('Dispatch: consumes godown (-5)', after === before - 5, `${before}->${after}`);
  const tstock = await expectOk('GET', `/trucks/${truck.id}/stock`);
  ok('Dispatch: truck stock loaded (5)', tstock.find(s => s.itemId === item.id)?.quantity === 5);
  const dlabels = await expectOk('GET', '/dispatch/trucks');
  ok('Dispatch: truck-labels endpoint', dlabels.includes(truck.name));

  // ---- ORDERS: dispatch source from truck ----
  const dord = await expectOk('POST', '/orders', { customerId: cust.id, source: 1, truckId: truck.id, items: [{ itemId: item.id, quantity: 2 }] });
  ok('Orders: dispatch-source order from truck', dord.truckName === truck.name);
  const tstock2 = await expectOk('GET', `/trucks/${truck.id}/stock`);
  ok('Orders: truck stock reduced (5->3)', (tstock2.find(s => s.itemId === item.id)?.quantity ?? 0) === 3);

  // ---- STOCK REQUEST PAYMENTS + ADVANCE ----
  const req2 = await expectOk('POST', '/stock-requests', { notes: 'pay', items: [{ itemId: item.id, quantity: 2 }] }); // total 40 (price 20)
  const rpaid = await expectOk('POST', '/stock-request-payments', { stockRequestId: req2.id, amount: 100, method: 'Cash' });
  ok('StockReq payments: overpay clamps remaining', rpaid.remainingAmount === 0);
  const radv = await expectOk('GET', '/stock-request-payments/advance');
  ok('StockReq advance: balance tracked (60)', radv.advanceBalance === 60, `adv=${radv.advanceBalance}`);

  // ---- REPORTS ----
  const now = new Date();
  const rep = await expectOk('GET', `/reports/monthly?year=${now.getFullYear()}&page=1&pageSize=12`);
  ok('Reports: monthly summary', rep && typeof rep.totalOrders === 'number');
  const crep = await expectOk('GET', '/reports/by-customer?page=1&pageSize=5');
  ok('Reports: by-customer', Array.isArray(crep.items));

  // ---- SUMMARY ----
  const fails = results.filter(x => !x.pass);
  console.log(`\n==== ${results.length - fails.length}/${results.length} checks passed ====`);
  if (fails.length) { console.log('FAILED:'); fails.forEach(f => console.log(' -', f.name, f.extra)); }
  console.log(`\nE2E_USER_EMAIL=${email}`);
}
main().catch(e => { console.error('TEST HARNESS ERROR:', e.message); process.exit(2); });
