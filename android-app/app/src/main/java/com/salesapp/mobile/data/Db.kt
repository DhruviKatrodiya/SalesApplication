package com.salesapp.mobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.sql.DriverManager

/**
 * Fully local, on-device storage. All data lives in a single SQLite database file inside the
 * app's private storage — no server, no network. Access goes through the SQLDroid JDBC driver
 * so the repositories keep their PreparedStatement/Connection code; only the SQL dialect is SQLite.
 */
object Db {

    private const val DEFAULT_EMAIL = "sales@salesapp.com"
    private const val DEFAULT_PASSWORD = "Sales@123"

    @Volatile private var dbUrl: String? = null
    private var driverLoaded = false

    /** Create the database + schema and seed the default user. Call once on app start. */
    fun init(ctx: Context) {
        val path = ctx.getDatabasePath("salesapp.db")
        path.parentFile?.mkdirs()
        dbUrl = "jdbc:sqldroid:${path.absolutePath}"
        ensureDriver()
        open().use { conn ->
            conn.createStatement().use { st -> SCHEMA.forEach { st.execute(it) } }
            seedDefaultUser(conn)
        }
    }

    private fun ensureDriver() {
        if (!driverLoaded) {
            Class.forName("org.sqldroid.SQLDroidDriver")
            driverLoaded = true
        }
    }

    private fun open(): Connection {
        val url = dbUrl ?: error("Database is not initialized")
        val conn = DriverManager.getConnection(url)
        conn.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
        return conn
    }

    /** Run [block] with an open connection on the IO dispatcher, closing it afterwards. */
    suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        open().use { conn -> block(conn) }
    }

    private fun seedDefaultUser(conn: Connection) {
        val count = conn.prepareStatement("SELECT COUNT(*) FROM Users").use { ps ->
            ps.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
        }
        if (count > 0) return
        val hash = BCrypt.hashpw(DEFAULT_PASSWORD, BCrypt.gensalt())
        conn.prepareStatement(
            "INSERT INTO Users (FullName, Email, PasswordHash, CreatedAt) VALUES (?, ?, ?, datetime('now'))"
        ).use { ps ->
            ps.setString(1, "Sales Person"); ps.setString(2, DEFAULT_EMAIL); ps.setString(3, hash)
            ps.executeUpdate()
        }
    }

    /** SQLite schema. Booleans are INTEGER 0/1, money is REAL, dates/timestamps are TEXT. */
    private val SCHEMA = listOf(
        """CREATE TABLE IF NOT EXISTS Users (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, FullName TEXT NOT NULL DEFAULT '',
            Email TEXT NOT NULL UNIQUE, PasswordHash TEXT NOT NULL DEFAULT '',
            Phone TEXT, ProfileImagePath TEXT, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')))""",
        """CREATE TABLE IF NOT EXISTS Categories (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, Name TEXT NOT NULL DEFAULT '',
            Description TEXT, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS SubCategories (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, CategoryId INTEGER NOT NULL,
            Name TEXT NOT NULL DEFAULT '', Description TEXT,
            CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS Items (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, SubCategoryId INTEGER NOT NULL,
            Name TEXT NOT NULL DEFAULT '', Sku TEXT, Unit TEXT,
            StockQuantity INTEGER NOT NULL DEFAULT 0, DispatchStock INTEGER NOT NULL DEFAULT 0,
            UnitPrice REAL NOT NULL DEFAULT 0, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')),
            IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS InventoryBatches (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, ItemId INTEGER NOT NULL,
            InitialQuantity INTEGER NOT NULL DEFAULT 0, Quantity INTEGER NOT NULL DEFAULT 0,
            PurchasePrice REAL NOT NULL DEFAULT 0, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')),
            StockRequestId INTEGER)""",
        """CREATE TABLE IF NOT EXISTS InventoryTransactions (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, ItemId INTEGER NOT NULL,
            BatchId INTEGER, Type INTEGER NOT NULL DEFAULT 0, Quantity INTEGER NOT NULL DEFAULT 0,
            UnitCost REAL NOT NULL DEFAULT 0, TotalCost REAL NOT NULL DEFAULT 0,
            RefType TEXT NOT NULL DEFAULT '', RefId INTEGER, Source TEXT,
            Reversed INTEGER NOT NULL DEFAULT 0, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')))""",
        """CREATE TABLE IF NOT EXISTS Routes (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, Name TEXT NOT NULL DEFAULT '',
            Description TEXT, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS Customers (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, Name TEXT NOT NULL DEFAULT '',
            Phone TEXT, Email TEXT, Address TEXT, RouteId INTEGER,
            CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS Trucks (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, Name TEXT NOT NULL DEFAULT '',
            CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS TruckStocks (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, TruckId INTEGER NOT NULL, ItemId INTEGER NOT NULL,
            Quantity INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS Orders (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, OrderNumber TEXT NOT NULL DEFAULT '',
            CustomerId INTEGER NOT NULL, SalesmanId INTEGER, TruckId INTEGER,
            OrderDate TEXT NOT NULL DEFAULT (datetime('now')), DeliveryDate TEXT,
            Status INTEGER NOT NULL DEFAULT 0, PaymentStatus INTEGER NOT NULL DEFAULT 0, Source INTEGER NOT NULL DEFAULT 0,
            TotalAmount REAL NOT NULL DEFAULT 0, PaidAmount REAL NOT NULL DEFAULT 0, RemainingAmount REAL NOT NULL DEFAULT 0,
            Notes TEXT, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS OrderItems (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, OrderId INTEGER NOT NULL, ItemId INTEGER NOT NULL,
            Quantity INTEGER NOT NULL DEFAULT 0, UnitPrice REAL NOT NULL DEFAULT 0, LineTotal REAL NOT NULL DEFAULT 0,
            ReceivedStatus INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS Payments (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, OrderId INTEGER NOT NULL, Amount REAL NOT NULL DEFAULT 0,
            PaymentDate TEXT NOT NULL DEFAULT (datetime('now')), Method TEXT, Note TEXT)""",
        """CREATE TABLE IF NOT EXISTS Dispatches (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, TruckLabel TEXT NOT NULL DEFAULT '',
            Notes TEXT, DispatchDate TEXT NOT NULL DEFAULT (datetime('now')), IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS DispatchItems (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, DispatchId INTEGER NOT NULL, ItemId INTEGER NOT NULL,
            Quantity INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS StockRequests (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, RequestNumber TEXT NOT NULL DEFAULT '', SalesmanId INTEGER,
            Status INTEGER NOT NULL DEFAULT 0, Notes TEXT, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')),
            TotalAmount REAL NOT NULL DEFAULT 0, PaidAmount REAL NOT NULL DEFAULT 0, RemainingAmount REAL NOT NULL DEFAULT 0,
            PaymentStatus INTEGER NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1)""",
        """CREATE TABLE IF NOT EXISTS StockRequestItems (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, StockRequestId INTEGER NOT NULL, ItemId INTEGER NOT NULL,
            Quantity INTEGER NOT NULL DEFAULT 0, UnitPrice REAL NOT NULL DEFAULT 0, LineTotal REAL NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS StockRequestPayments (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, StockRequestId INTEGER NOT NULL, Amount REAL NOT NULL DEFAULT 0,
            PaymentDate TEXT NOT NULL DEFAULT (datetime('now')), Method TEXT, Note TEXT)""",
        """CREATE TABLE IF NOT EXISTS PasswordResetOtps (
            Id INTEGER PRIMARY KEY AUTOINCREMENT, Email TEXT NOT NULL, OtpHash TEXT NOT NULL,
            ExpiresAt TEXT NOT NULL, IsUsed INTEGER NOT NULL DEFAULT 0,
            CreatedAt TEXT NOT NULL DEFAULT (datetime('now')))""",
    )
}
