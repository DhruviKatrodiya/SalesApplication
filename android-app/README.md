# Sales App — Android (Kotlin, direct SQL Server)

A native Android client for the existing `SalesAppDb` SQL Server database. It replaces the
.NET Web API + Angular frontend with a phone app that connects **directly** to SQL Server over
the network via JDBC (jTDS). All business logic that used to live in the API controllers is
reimplemented in Kotlin **repository** classes.

- **Language / UI:** Kotlin + XML Views + Material 3
- **Data access:** [jTDS](http://jtds.sourceforge.net/) JDBC driver → SQL Server (no backend API)
- **Auth:** login validated against `Users.PasswordHash` (BCrypt, via jBCrypt)
- **Storage of connection details:** encrypted on-device (`EncryptedSharedPreferences`)

> ⚠️ This is **not** a truly offline/backendless design. The phone must be able to reach a
> running SQL Server. If you want a fully offline app, the alternative is on-device SQLite/Room
> (see the conversation notes). This build follows the "connect to SQL Server directly" choice.

## Prerequisites on the SQL Server host
1. **Enable TCP/IP** in *SQL Server Configuration Manager* → Protocols → TCP/IP → Enabled, restart the service.
2. Open **port 1433** on the Windows firewall (inbound).
3. Create a **SQL Server login** (Mixed Mode auth) with a username + password and grant it access to
   `SalesAppDb`. The app **cannot** use `Trusted_Connection` (Windows auth).
4. Make sure the database exists — run the existing .NET backend once (it applies EF migrations and
   seeds the default user `sales@salesapp.com` / `Sales@123`), or restore the schema another way.

## Networking
- **Android emulator:** host machine is reachable at `10.0.2.2` (use that as the Host).
- **Physical device:** use the PC's LAN IP (e.g. `192.168.1.x`), same Wi-Fi network.

## Build & run
1. Open the `android-app/` folder in **Android Studio** (Giraffe+). It will generate
   `local.properties` (SDK path) and the Gradle wrapper jar automatically.
   - From a terminal with Gradle installed you can also run `gradle wrapper` once to create `gradlew`.
2. Build & run on a device/emulator.
3. On first launch tap **Connection settings**, enter Host / Port / Database / SQL user / password,
   **Test connection**, then **Save**.
4. Sign in with your SQL `Users` credentials (e.g. the seeded `sales@salesapp.com` / `Sales@123`).

## Implementation status
Built incrementally, mirroring the API controllers:

| Area | Status |
|------|--------|
| Project scaffold, theme, launcher | ✅ |
| SQL Server connection layer + settings screen | ✅ |
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
