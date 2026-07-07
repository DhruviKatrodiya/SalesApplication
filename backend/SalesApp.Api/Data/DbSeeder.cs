using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Models;
using SalesApp.Api.Services;

namespace SalesApp.Api.Data;

public static class DbSeeder
{
    public const string DefaultEmail = "sales@salesapp.com";
    public const string DefaultPassword = "Sales@123";

    public static async Task SeedAsync(AppDbContext db)
    {
        await db.Database.MigrateAsync();

        if (!await db.Users.AnyAsync())
        {
            db.Users.Add(new AppUser
            {
                FullName = "Sales Person",
                Email = DefaultEmail,
                Phone = "0000000000",
                PasswordHash = BCrypt.Net.BCrypt.HashPassword(DefaultPassword)
            });
            await db.SaveChangesAsync();
        }

        await SeedSampleDataAsync(db);
    }

    /// <summary>
    /// Seeds ~10 demo records for every feature (categories, sub-categories, items, routes,
    /// customers, trucks, dispatches, customer orders, payments, stock requests), routed through the
    /// real inventory/order logic so stock, FIFO batches, movements, payment statuses and advances
    /// are all internally consistent. Runs once — skipped if catalog data already exists.
    /// </summary>
    private static async Task SeedSampleDataAsync(AppDbContext db)
    {
        var user = await db.Users.FirstAsync(u => u.Email == DefaultEmail);
        var uid = user.Id;

        // Idempotent + additive: add the 10-record demo set once (keyed on a distinctive seeded
        // record) so it layers on top of any existing data without duplicating on later restarts.
        if (await db.Customers.AnyAsync(c => c.UserId == uid && c.Name == "Value Bazaar")) return;
        var inv = new InventoryService(db);
        var now = DateTime.UtcNow;
        var catalogDate = now.AddDays(-60);   // opening stock is the oldest FIFO layer

        // ---- Routes (10) ----
        var routeNames = new[]
        {
            "North Zone", "South Zone", "East Zone", "West Zone", "Central Zone",
            "Old City", "Industrial Area", "Market Road", "Highway Route", "Suburban Belt"
        };
        var routes = routeNames.Select(n => new DeliveryRoute { UserId = uid, Name = n, Description = $"{n} delivery route", CreatedAt = catalogDate }).ToList();
        db.Routes.AddRange(routes);
        await db.SaveChangesAsync();

        // ---- Categories (10) + one Sub-Category each (10) ----
        var catNames = new[]
        {
            "Beverages", "Dairy", "Snacks", "Bakery", "Staples",
            "Frozen Foods", "Household", "Personal Care", "Confectionery", "Stationery"
        };
        var subNames = new[]
        {
            "Soft Drinks", "Milk & Curd", "Chips & Namkeen", "Breads", "Rice & Flour",
            "Frozen Veg", "Cleaning", "Soaps", "Chocolates", "Notebooks"
        };
        var categories = catNames.Select(n => new Category { UserId = uid, Name = n, Description = $"{n} products", CreatedAt = catalogDate }).ToList();
        db.Categories.AddRange(categories);
        await db.SaveChangesAsync();

        var subCats = new List<SubCategory>();
        for (var i = 0; i < subNames.Length; i++)
            subCats.Add(new SubCategory { UserId = uid, CategoryId = categories[i].Id, Name = subNames[i], Description = subNames[i], CreatedAt = catalogDate });
        db.SubCategories.AddRange(subCats);
        await db.SaveChangesAsync();

        // ---- Items (10), one per sub-category, each with opening stock ----
        var itemDefs = new (string Name, string Sku, string Unit, int Stock, decimal Price)[]
        {
            ("Cola 500ml",          "COLA500", "Bottle", 300, 40m),
            ("Full Cream Milk 1L",  "MILK1L",  "Packet", 250, 55m),
            ("Potato Chips 100g",   "CHIP100", "Pack",   180, 25m),
            ("Brown Bread",         "BREAD1",  "Loaf",   120, 45m),
            ("Basmati Rice 5kg",    "RICE5",   "Bag",    150, 520m),
            ("Frozen Peas 500g",    "PEAS500", "Pack",    90, 80m),
            ("Dish Soap 1L",        "DISH1",   "Bottle", 140, 110m),
            ("Bath Soap",           "SOAP1",   "Piece",  300, 35m),
            ("Dark Chocolate 50g",  "CHOC50",  "Bar",    220, 60m),
            ("A5 Notebook",         "NOTE-A5", "Piece",  200, 50m),
        };
        var items = new List<Item>();
        for (var i = 0; i < itemDefs.Length; i++)
        {
            var d = itemDefs[i];
            items.Add(new Item
            {
                UserId = uid, SubCategoryId = subCats[i].Id, Name = d.Name, Sku = d.Sku, Unit = d.Unit,
                StockQuantity = d.Stock, UnitPrice = d.Price, DispatchStock = 0, CreatedAt = catalogDate
            });
        }
        db.Items.AddRange(items);
        await db.SaveChangesAsync();

        // ---- Customers (10) ----
        var custDefs = new (string Name, string Phone, string Email, string Address)[]
        {
            ("City Supermarket",     "9900112233", "info@citysuper.com",     "12 MG Road, City Center"),
            ("Green Mart",           "9812345678", "contact@greenmart.com",  "45 Park Street, South Zone"),
            ("Sharma Provisions",    "9823456712", "sharma@mail.com",        "7 Old City Lane"),
            ("Ramesh General Store", "9876543210", "ramesh@store.com",       "88 North Market"),
            ("Daily Needs",          "9765432109", "hello@dailyneeds.com",   "23 West Avenue"),
            ("Fresh Basket",         "9654321098", "sales@freshbasket.com",  "9 Highway Plaza"),
            ("Star Grocery",         "9543210987", "star@grocery.com",       "56 Central Bazaar"),
            ("Metro Mart",           "9432109876", "care@metromart.com",     "101 Industrial Road"),
            ("Corner Shop",          "9321098765", "corner@shop.com",        "3 Suburban Colony"),
            ("Value Bazaar",         "9210987654", "value@bazaar.com",       "67 Market Road"),
        };
        var customers = new List<Customer>();
        for (var i = 0; i < custDefs.Length; i++)
        {
            var d = custDefs[i];
            customers.Add(new Customer
            {
                UserId = uid, Name = d.Name, Phone = d.Phone, Email = d.Email, Address = d.Address,
                RouteId = routes[i % routes.Count].Id, CreatedAt = catalogDate
            });
        }
        db.Customers.AddRange(customers);
        await db.SaveChangesAsync();

        // ---- Trucks (10) ---- (reuse a truck if the name already exists — names are unique per user)
        var existingTrucks = await db.Trucks.Where(t => t.UserId == uid).ToListAsync();
        var trucks = new List<Truck>();
        for (var n = 1; n <= 10; n++)
        {
            var name = $"Truck {n:00}";
            var t = existingTrucks.FirstOrDefault(x => x.Name == name);
            if (t == null) { t = new Truck { UserId = uid, Name = name, CreatedAt = catalogDate }; db.Trucks.Add(t); }
            trucks.Add(t);
        }
        await db.SaveChangesAsync();

        // ---- Dispatches (10): load each truck from the godown (FIFO consume + truck stock) ----
        async Task AddTruckStock(int truckId, int itemId, int qty)
        {
            var ts = await db.TruckStocks.FirstOrDefaultAsync(t => t.TruckId == truckId && t.ItemId == itemId);
            if (ts == null) db.TruckStocks.Add(new TruckStock { TruckId = truckId, ItemId = itemId, Quantity = qty });
            else ts.Quantity += qty;
        }

        for (var i = 0; i < 10; i++)
        {
            var truck = trucks[i];
            var lines = new[] { (idx: i, qty: 25), (idx: (i + 5) % 10, qty: 15) };
            var dispatch = new Dispatch
            {
                UserId = uid, TruckLabel = truck.Name, Notes = $"Loaded {truck.Name}",
                DispatchDate = now.AddDays(-(20 - i))
            };
            db.Dispatches.Add(dispatch);
            await db.SaveChangesAsync();
            foreach (var (idx, qty) in lines)
            {
                await inv.ConsumeFifoAsync(items[idx], qty, "Dispatch", dispatch.Id, truck.Name);
                items[idx].DispatchStock += qty;
                await AddTruckStock(truck.Id, items[idx].Id, qty);
                dispatch.Items.Add(new DispatchItem { DispatchId = dispatch.Id, ItemId = items[idx].Id, Quantity = qty });
            }
            await db.SaveChangesAsync();
        }

        // ---- Customer Orders (10) with payments, varied statuses ----
        var orderSeq = 0;
        var methods = new[] { "Cash", "UPI", "Bank", "Cheque" };

        async Task<Order> NewOrder(int customerIdx, OrderSource source, int? truckId, (int idx, int qty)[] lines,
            DateTime date, OrderStatus status, DateTime? delivery, ReceivedStatus recv)
        {
            orderSeq++;
            var number = DocNumber.Format("ORD", date, orderSeq);
            while (await db.Orders.AnyAsync(o => o.OrderNumber == number))
            { orderSeq++; number = DocNumber.Format("ORD", date, orderSeq); }
            var order = new Order
            {
                OrderNumber = number,
                CustomerId = customers[customerIdx].Id, SalesmanId = uid, TruckId = truckId,
                OrderDate = date, DeliveryDate = delivery, Status = status, Source = source, CreatedAt = date
            };
            decimal total = 0;
            foreach (var (idx, qty) in lines)
            {
                var it = items[idx];
                var lt = qty * it.UnitPrice;
                order.Items.Add(new OrderItem { ItemId = it.Id, Quantity = qty, UnitPrice = it.UnitPrice, LineTotal = lt, ReceivedStatus = recv });
                total += lt;
            }
            order.TotalAmount = total;
            db.Orders.Add(order);
            await db.SaveChangesAsync();

            foreach (var (idx, qty) in lines)
            {
                if (source == OrderSource.Inventory)
                {
                    await inv.ConsumeFifoAsync(items[idx], qty, "Order", order.Id, order.OrderNumber);
                }
                else // Dispatch: draw from the truck's stock
                {
                    var ts = await db.TruckStocks.FirstAsync(t => t.TruckId == truckId && t.ItemId == items[idx].Id);
                    ts.Quantity -= qty;
                    items[idx].DispatchStock -= qty;
                }
            }
            OrderMath.Recalculate(order);
            await db.SaveChangesAsync();
            return order;
        }

        async Task Pay(Order o, decimal amount, string method, DateTime date)
        {
            var p = new Payment { OrderId = o.Id, Amount = amount, Method = method, PaymentDate = date, Note = "Payment received" };
            db.Payments.Add(p);
            o.Payments.Add(p);
            OrderMath.Recalculate(o);
            await db.SaveChangesAsync();
        }

        DateTime D(int i) => now.AddDays(-4 * i);

        var o0 = await NewOrder(0, OrderSource.Inventory, null, new[] { (0, 10), (2, 8) }, D(0), OrderStatus.Delivered, D(0).AddDays(1), ReceivedStatus.Completed);
        await Pay(o0, o0.TotalAmount, methods[0], D(0).AddDays(1));                       // Paid

        await NewOrder(1, OrderSource.Inventory, null, new[] { (3, 20) }, D(1), OrderStatus.Pending, null, ReceivedStatus.Pending);   // Pending / unpaid

        var o2 = await NewOrder(2, OrderSource.Inventory, null, new[] { (5, 5), (9, 10) }, D(2), OrderStatus.Dispatched, null, ReceivedStatus.Remaining);
        await Pay(o2, o2.TotalAmount / 2, methods[1], D(2).AddDays(1));                   // Partial (dispatched)

        var o3 = await NewOrder(3, OrderSource.Inventory, null, new[] { (1, 6) }, D(3), OrderStatus.Delivered, D(3).AddDays(1), ReceivedStatus.Completed);
        await Pay(o3, o3.TotalAmount, methods[2], D(3).AddDays(1));                       // Paid

        var o4 = await NewOrder(4, OrderSource.Inventory, null, new[] { (4, 4), (8, 12) }, D(4), OrderStatus.Pending, null, ReceivedStatus.Pending);
        await Pay(o4, Math.Round(o4.TotalAmount * 0.3m, 2), methods[0], D(4).AddDays(1)); // Advance (partial before dispatch)

        var o5 = await NewOrder(5, OrderSource.Inventory, null, new[] { (6, 3) }, D(5), OrderStatus.Completed, D(5).AddDays(1), ReceivedStatus.Completed);
        await Pay(o5, o5.TotalAmount, methods[3], D(5).AddDays(1));                       // Paid

        await NewOrder(6, OrderSource.Inventory, null, new[] { (7, 2), (9, 5) }, D(6), OrderStatus.Dispatched, null, ReceivedStatus.Remaining); // Pending payment

        // Cancelled order — pay some, then cancel (stock returns, paid amount becomes advance credit).
        var o7 = await NewOrder(7, OrderSource.Inventory, null, new[] { (0, 4) }, D(7), OrderStatus.Pending, null, ReceivedStatus.Pending);
        await Pay(o7, 100m, methods[0], D(7).AddDays(1));
        await inv.ReverseAsync("Order", o7.Id);
        o7.Status = OrderStatus.Cancelled;
        o7.RemainingAmount = 0;
        await db.SaveChangesAsync();

        // Two Dispatch-source orders drawing from Truck 01's loaded stock.
        var o8 = await NewOrder(8, OrderSource.Dispatch, trucks[0].Id, new[] { (0, 5) }, D(8), OrderStatus.Delivered, D(8).AddDays(1), ReceivedStatus.Completed);
        await Pay(o8, o8.TotalAmount, methods[1], D(8).AddDays(1));                       // Paid

        var o9 = await NewOrder(9, OrderSource.Dispatch, trucks[0].Id, new[] { (5, 4) }, D(9), OrderStatus.Pending, null, ReceivedStatus.Pending);
        await Pay(o9, o9.TotalAmount + 80m, methods[2], D(9).AddDays(1));                 // Overpaid → Paid + advance balance

        // ---- Stock Requests / "My Orders" (10) ----
        var reqSeq = 0;

        async Task NewRequest((int idx, int qty, decimal price)[] lines, DateTime date, StockRequestStatus finalStatus, decimal payAmount)
        {
            reqSeq++;
            var number = DocNumber.Format("REQ", date, reqSeq);
            while (await db.StockRequests.AnyAsync(r => r.RequestNumber == number))
            { reqSeq++; number = DocNumber.Format("REQ", date, reqSeq); }
            var req = new StockRequest
            {
                RequestNumber = number, SalesmanId = uid,
                Status = StockRequestStatus.Pending, Notes = "Restock request", CreatedAt = date
            };
            decimal total = 0;
            foreach (var (idx, qty, price) in lines)
            {
                var lt = qty * price;
                req.Items.Add(new StockRequestItem { ItemId = items[idx].Id, Quantity = qty, UnitPrice = price, LineTotal = lt });
                total += lt;
            }
            req.TotalAmount = total;
            db.StockRequests.Add(req);
            await db.SaveChangesAsync();

            if (finalStatus is StockRequestStatus.Fulfilled or StockRequestStatus.Done)
            {
                foreach (var (idx, qty, price) in lines)
                {
                    await inv.ReceiveAsync(items[idx], qty, price, "Fulfill", req.Id, req.RequestNumber);
                    items[idx].UnitPrice = price;   // latest price shown, like the website fulfil step
                }
                req.Status = StockRequestStatus.Fulfilled;
            }
            if (finalStatus == StockRequestStatus.Done) req.Status = StockRequestStatus.Done;
            if (finalStatus == StockRequestStatus.Cancelled) req.Status = StockRequestStatus.Cancelled;

            if (payAmount > 0)
            {
                db.StockRequestPayments.Add(new StockRequestPayment
                {
                    StockRequestId = req.Id, Amount = payAmount, Method = "Cash", PaymentDate = date.AddDays(1), Note = "Payment"
                });
                req.PaidAmount = payAmount;
            }
            else req.PaidAmount = 0;

            req.RemainingAmount = Math.Max(0, req.TotalAmount - req.PaidAmount);
            req.PaymentStatus =
                req.PaidAmount <= 0 ? PaymentStatus.Pending
                : req.PaidAmount >= req.TotalAmount ? PaymentStatus.Paid
                : req.Status == StockRequestStatus.Pending ? PaymentStatus.Advance
                : PaymentStatus.Partial;
            await db.SaveChangesAsync();
        }

        DateTime R(int i) => now.AddDays(-3 * i);

        await NewRequest(new[] { (0, 50, 42m) },              R(0), StockRequestStatus.Fulfilled, 2100m);        // Fulfilled, paid
        await NewRequest(new[] { (1, 40, 55m) },              R(1), StockRequestStatus.Pending, 0m);             // Pending
        await NewRequest(new[] { (2, 60, 26m), (3, 30, 46m) }, R(2), StockRequestStatus.Done, 2940m);            // Done, paid
        await NewRequest(new[] { (4, 20, 525m) },             R(3), StockRequestStatus.Fulfilled, 5250m);        // Fulfilled, partial
        await NewRequest(new[] { (5, 25, 82m) },              R(4), StockRequestStatus.Pending, 0m);             // Pending
        await NewRequest(new[] { (6, 15, 112m) },             R(5), StockRequestStatus.Fulfilled, 0m);           // Fulfilled, unpaid
        await NewRequest(new[] { (7, 10, 36m), (8, 20, 62m) }, R(6), StockRequestStatus.Done, 1600m);            // Done, paid
        await NewRequest(new[] { (9, 35, 50m) },              R(7), StockRequestStatus.Cancelled, 0m);           // Cancelled
        await NewRequest(new[] { (0, 20, 43m) },              R(8), StockRequestStatus.Fulfilled, 400m);         // Fulfilled, partial
        await NewRequest(new[] { (3, 15, 46m) },              R(9), StockRequestStatus.Pending, 0m);             // Pending
    }
}
