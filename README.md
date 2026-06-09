# Sales Application

A single **Sales Person** panel to run a small distribution business (one godown, one delivery truck): manage product categories, inventory/stock, truck dispatches, customer orders, payments, settlements, and monthly/yearly reports — plus full auth (login, profile, change password, email-OTP password reset).

- **Backend:** .NET 10 Web API + Entity Framework Core + SQL Server, JWT auth
- **Frontend:** Angular 22 + Angular Material
- **Email OTP:** MailKit (SMTP), with a dev fallback that logs the OTP to the server console when SMTP is not configured

```
SalesApplication/
├── backend/SalesApp.Api/      # .NET 10 Web API  (runs on http://localhost:5219)
├── frontend/sales-app/        # Angular 22 + Material  (runs on http://localhost:4200)
└── tools/populate-data.js     # optional: inserts ~20 sample records per section via the API
```

---

## Prerequisites

- **.NET SDK 10** (`dotnet --version`)
- **Node.js 20+** and **Angular CLI 22** (`ng version`)
- **SQL Server** running locally (the default instance `localhost` with Windows auth is used out of the box)
- **EF Core tools:** `dotnet tool install --global dotnet-ef`

---

## 1. Backend — `backend/SalesApp.Api`

### Configure
Connection string and settings live in `appsettings.json`:

```jsonc
"ConnectionStrings": {
  "DefaultConnection": "Server=localhost;Database=SalesAppDb;Trusted_Connection=True;TrustServerCertificate=True;MultipleActiveResultSets=true"
},
"Jwt": { "Key": "CHANGE_THIS_TO_A_LONG_RANDOM_SECRET_KEY_AT_LEAST_32_CHARS_LONG_123456", ... }
```

> **Change the `Jwt:Key`** to your own long random secret before any real use.
> If your SQL Server uses a named instance or SQL login, edit `DefaultConnection`
> (e.g. `Server=localhost\\SQLEXPRESS;...` or `Server=localhost;User Id=sa;Password=...;`).

### Run
```bash
cd backend/SalesApp.Api
dotnet run
```

On first start the app **applies EF migrations automatically** and **seeds a default user**.
The API listens on **http://localhost:5219**; Swagger is at **http://localhost:5219/swagger**.

> To (re)create the database from scratch:
> ```bash
> dotnet-ef database drop --force
> dotnet-ef database update
> ```

### Default login (seeded)
| Email | Password |
|-------|----------|
| `sales@salesapp.com` | `Sales@123` |

The email and password are both editable in-app (Profile / Change Password). **Note:** the email is your
login username — after changing it, sign in with the new email (the password is unchanged).

### Email OTP (password reset)
The reset flow always works. If SMTP is **not** configured, the 6-digit OTP is written to the
server console (`OTP for ... is: 123456`). To send **real emails**, fill the `Smtp` section in
`appsettings.json` (or user-secrets — keep secrets out of git):

```jsonc
"Smtp": {
  "Host": "smtp.gmail.com",
  "Port": 587,
  "UseSsl": true,
  "User": "you@gmail.com",
  "Password": "your-app-password",   // Gmail: use an App Password
  "FromName": "Sales App",
  "FromEmail": "you@gmail.com"
}
```

---

## 2. Frontend — `frontend/sales-app`

```bash
cd frontend/sales-app
npm install        # first time only
ng serve
```

App runs at **http://localhost:4200**. The API base URL is in
`src/environments/environment.ts` (`apiBaseUrl: http://localhost:5219/api`).
Log in with the seeded credentials above.

---

## 3. (Optional) Load sample data

With the API running, populate ~20 records into each section (categories, sub-categories, items,
customers, orders with varied statuses/payments, and dispatches). The data is stored in SQL Server
and served dynamically by the API:

```bash
node tools/populate-data.js
```

---

## Features

| Area | What it does |
|------|--------------|
| **Auth** | Login (JWT), Forgot Password → Email OTP → Reset, Profile update (**editable email**), Change Password |
| **Categories** | Create/update/delete categories and sub-categories |
| **Inventory** | Items with stock levels, unit price; search + low-stock filter |
| **Dispatch (Truck)** | Load items onto the single truck; **stock is validated and decremented**; dispatch history |
| **Customers** | Add/edit customers; **search "ABC"** → full profile: payment status, totals, pending/delivered counts, every order with items |
| **Orders** | Manual order entry; **filters** (Customer Name, Order Date, Delivery Status, Paid Status); update status & delivery date; per-item received status; auto order numbers |
| **Payments** | Record payments, **mark fully settled**; paid/remaining and payment status recompute automatically |
| **Reports** | Monthly (by month) and yearly summaries; per-customer pending vs delivered and outstanding balances |
| **UX** | All tables paginated (**5 per page**, options 5/10/25/50); Material `fill` form fields with required markers + validation |

---

## API overview (all under `/api`, JWT required except `auth/*`)

| Controller | Endpoints |
|-----------|-----------|
| `auth` | `login`, `forgot-password`, `verify-otp`, `reset-password` |
| `profile` | `GET me`, `PUT /` (update incl. email), `POST change-password` |
| `categories` / `subcategories` | full CRUD |
| `items` | CRUD (+ `?lowStock=true&threshold=10`) |
| `dispatch` | `POST` (validates + decrements stock), `GET` history |
| `customers` | CRUD, `GET search?query=`, `GET {id}/details` |
| `orders` | CRUD, `PUT {id}/status`, `PUT {id}/delivery-date`, `PUT {id}/items/{itemId}/received-status` |
| `payments` | `POST`, `POST settle/{orderId}`, `GET by-order/{orderId}`, `DELETE {id}` |
| `reports` | `GET monthly?year=&month=`, `GET yearly?year=`, `GET by-customer` |

---

## Tech notes
- Passwords hashed with **BCrypt**; OTPs are hashed and expire after 10 minutes.
- Order totals/payment status are derived from the Payments ledger on every change.
- CORS is open to `http://localhost:4200` for local development (configured in `Program.cs`).
- App user email is unique; profile updates reject an email already used by another account.
