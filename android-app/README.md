# Sales App — Android (Kotlin, fully offline / local storage)

A native Android app that stores **all data locally on the device** in a SQLite database — no server,
no network, no backend. It reimplements the business logic of the original .NET Web API in Kotlin
**repository** classes running against on-device SQLite. Data access goes through a lightweight JDBC
driver ([SQLDroid](https://github.com/SQLDroid/SQLDroid)) so the repositories keep clean
`PreparedStatement` SQL.

- **Language / UI:** Kotlin + XML Views + Material 3
- **Data storage:** on-device **SQLite** (`salesapp.db` in the app's private storage) — fully offline
- **Auth:** login validated against the local `Users` table (BCrypt via jBCrypt)
- **First launch:** the schema is created automatically and a default user is seeded

> ✅ Truly offline. No SQL Server, no connection setup, no permissions beyond storage. The database
> lives inside the app sandbox; uninstalling the app removes it.

## Default login (seeded on first launch)
| Email | Password |
|-------|----------|
| `sales@salesapp.com` | `Sales@123` |

## Build & run
1. Open the `android-app/` folder in **Android Studio**. It generates `local.properties` (SDK path)
   and the Gradle wrapper on first sync, and downloads dependencies.
2. Build & run on any emulator or device (minSdk 24). No configuration needed.
3. Sign in with the seeded credentials above (changeable in Profile → Change password).

### Command-line build
With JDK 17 + Android SDK installed and `ANDROID_HOME` set, from `android-app/`:
`gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Implementation status
Built incrementally, mirroring the API controllers:

| Area | Status |
|------|--------|
| Project scaffold, theme, launcher | ✅ |
| Local SQLite database (schema + auto-seed on first launch) | ✅ |
| Auth (login, session, BCrypt) | ✅ |
| Navigation drawer shell | ✅ |
| **Categories** (list / search / add / edit / soft-delete / activate) | ✅ |
| **Sub-categories** (list / search / add / edit / soft-delete / activate, category dropdown) | ✅ |
| **Inventory/Items** (CRUD, low-stock filter, opening-stock batch, FIFO price-history, movements, delete guards) | ✅ |
| **Routes** (CRUD, customer counts) | ✅ |
| **Customers** (CRUD, route dropdown, search, full details/profile with payment totals + advance) | ✅ |
| **Trucks** (CRUD, unique-name guard, stock view, delete-while-loaded guard) | ✅ |
| **Dispatch** (record load → FIFO-consume godown + load truck, same-day merge, delete/reactivate reversal, history) | ✅ |
| **Orders** (create/edit inventory-vs-dispatch with stock handling + numbering, status, received-status, cancel-with-advance, delete) | ✅ |
| **Payments** (add / settle / apply-advance / delete, ledger→status recompute) | ✅ |
| **My Orders (stock requests)** (create/edit, fulfil→priced FIFO batches, done, cancel-with-stock-return, request payments/settle/apply-advance) | ✅ |
| **Reports** (monthly / daily / yearly / by-customer) | ✅ |
| **Profile / Change password** | ✅ |
| **Dashboard** (headline stats) | ✅ |

**All screens are implemented.** Shared engines mirroring backend services: `InventoryOps` (FIFO consume/receive/reverse + opening batch), `OrderMath` and `StockReqMath` (paid/remaining/status + advance). Intentionally omitted (no server): PDF/email invoices, profile image upload, email-OTP password reset. In-line editing of an already-saved *dispatch* is also deferred (delete + re-create instead).

Each feature follows the same pattern established by Categories:
`data/repo/XxxRepository.kt` (raw SQL + business rules) → `ui/xxx/` (Fragment + Adapter + dialog).
