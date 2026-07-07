package com.salesapp.mobile.data.repo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import java.io.File

/**
 * On-device replacement for the backend QuestPDF InvoiceService. Renders an item-wise A4 PDF invoice
 * for an order with the same content/layout the website produces (header, Bill-To, item table,
 * totals, notes, status footer). Currency is shown as "Rs." exactly like the server invoice.
 */
class InvoiceService {

    private data class Line(val name: String, val qty: Int, val unitPrice: Double, val lineTotal: Double)
    private data class Data(
        val orderNumber: String,
        val orderDate: String?,
        val deliveryDate: String?,
        val status: String,
        val paymentStatus: String,
        val total: Double,
        val paid: Double,
        val remaining: Double,
        val notes: String?,
        val customerName: String,
        val customerPhone: String?,
        val customerEmail: String?,
        val customerAddress: String?,
        val lines: List<Line>,
    )

    /** Loads the order + customer + lines and renders the PDF to a cache file. Returns the file. */
    suspend fun generate(ctx: Context, orderId: Int): File = Db.withConnection { conn ->
        val data = load(conn, orderId)
        render(ctx, data)
    }

    private fun load(conn: java.sql.Connection, orderId: Int): Data {
        val uid = Session.userId
        val header = conn.prepareStatement(
            """SELECT o.OrderNumber, o.OrderDate, o.DeliveryDate, o.Status, o.PaymentStatus,
                      o.TotalAmount, o.PaidAmount, o.RemainingAmount, o.Notes,
                      c.Name, c.Phone, c.Email, c.Address
               FROM Orders o JOIN Customers c ON c.Id = o.CustomerId
               WHERE o.Id = ? AND o.SalesmanId = ?"""
        ).use { ps ->
            ps.setInt(1, orderId); ps.setInt(2, uid)
            ps.executeQuery().use { rs ->
                if (!rs.next()) error("Order not found.")
                Data(
                    orderNumber = rs.getString(1) ?: "",
                    orderDate = rs.getString(2),
                    deliveryDate = rs.getString(3),
                    status = com.salesapp.mobile.data.models.OrderStatus.from(rs.getInt(4)).label,
                    paymentStatus = com.salesapp.mobile.data.models.PaymentStatus.from(rs.getInt(5)).label,
                    total = rs.getDouble(6), paid = rs.getDouble(7), remaining = rs.getDouble(8),
                    notes = rs.getString(9),
                    customerName = rs.getString(10) ?: "-",
                    customerPhone = rs.getString(11), customerEmail = rs.getString(12), customerAddress = rs.getString(13),
                    lines = emptyList(),
                )
            }
        }
        val lines = conn.prepareStatement(
            """SELECT i.Name, oi.Quantity, oi.UnitPrice, oi.LineTotal
               FROM OrderItems oi JOIN Items i ON i.Id = oi.ItemId
               WHERE oi.OrderId = ? ORDER BY oi.Id"""
        ).use { ps ->
            ps.setInt(1, orderId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Line(rs.getString(1) ?: "-", rs.getInt(2), rs.getDouble(3), rs.getDouble(4))) }
            }
        }
        if (lines.isEmpty()) error("Order has no items.")
        return header.copy(lines = lines)
    }

    private fun render(ctx: Context, d: Data): File {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 36f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x88, 0xE5); textSize = 12f }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val grey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x75, 0x75, 0x75); textSize = 10f }
        val headBg = Paint().apply { color = Color.rgb(0xEE, 0xEE, 0xEE) }
        val line = Paint().apply { color = Color.rgb(0xE0, 0xE0, 0xE0); strokeWidth = 1f }

        // Column x positions (left-aligned; qty/price/total right-aligned to their right edge)
        val xNum = margin
        val xItem = margin + 24
        val xQtyR = margin + 300
        val xPriceR = margin + 410
        val xTotalR = pageW - margin

        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        var c: Canvas = page.canvas
        var y = margin + 6

        fun rightText(s: String, xr: Float, yy: Float, p: Paint) {
            c.drawText(s, xr - p.measureText(s), yy, p)
        }

        // Header
        c.drawText("Sales Application", margin, y + 6, title)
        c.drawText("Tax Invoice", margin, y + 22, sub)
        rightText("Invoice: ${d.orderNumber}", xTotalR, y, bold)
        rightText("Date: ${fmtDateTime(d.orderDate)}", xTotalR, y + 14, normal)
        if (!d.deliveryDate.isNullOrBlank()) rightText("Delivery: ${fmtDateTime(d.deliveryDate)}", xTotalR, y + 28, normal)
        y += 48

        // Bill To
        c.drawText("Bill To", margin, y, grey); y += 14
        c.drawText(d.customerName, margin, y, bold); y += 13
        d.customerPhone?.takeIf { it.isNotBlank() }?.let { c.drawText("Phone: $it", margin, y, normal); y += 12 }
        d.customerEmail?.takeIf { it.isNotBlank() }?.let { c.drawText("Email: $it", margin, y, normal); y += 12 }
        d.customerAddress?.takeIf { it.isNotBlank() }?.let { c.drawText(it, margin, y, normal); y += 12 }
        y += 8

        // Item table header
        fun tableHeader() {
            c.drawRect(margin, y - 11, pageW - margin, y + 4, headBg)
            c.drawText("#", xNum, y, bold)
            c.drawText("Item", xItem, y, bold)
            rightText("Qty", xQtyR, y, bold)
            rightText("Unit Price", xPriceR, y, bold)
            rightText("Total", xTotalR, y, bold)
            y += 18
        }
        tableHeader()

        d.lines.forEachIndexed { idx, ln ->
            if (y > pageH - 120) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, doc.pages.size + 1).create())
                c = page.canvas; y = margin + 6; tableHeader()
            }
            c.drawText((idx + 1).toString(), xNum, y, normal)
            c.drawText(ellipsize(ln.name, normal, xQtyR - xItem - 40), xItem, y, normal)
            rightText(ln.qty.toString(), xQtyR, y, normal)
            rightText(money(ln.unitPrice), xPriceR, y, normal)
            rightText(money(ln.lineTotal), xTotalR, y, normal)
            y += 6
            c.drawLine(margin, y, pageW - margin, y, line)
            y += 14
        }
        y += 6

        // Totals
        rightText("Total: ${money(d.total)}", xTotalR, y, Paint(bold).apply { textSize = 12f }); y += 16
        rightText("Paid: ${money(d.paid)}", xTotalR, y, normal); y += 14
        rightText("Remaining: ${money(d.remaining)}", xTotalR, y, normal); y += 20

        if (!d.notes.isNullOrBlank()) { c.drawText("Notes: ${d.notes}", margin, y, grey); y += 16 }
        c.drawText("Order Status: ${d.status}", margin, y, bold); y += 14
        c.drawText("Payment Status: ${d.paymentStatus}", margin, y, bold)

        // Footer
        val footer = "Generated by Sales Application on ${fmtDate(nowIso())}"
        c.drawText(footer, (pageW - grey.measureText(footer)) / 2, (pageH - 24).toFloat(), grey)

        doc.finishPage(page)

        val file = File(ctx.cacheDir, "Invoice-${d.orderNumber}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun money(v: Double): String = "Rs. %,.2f".format(v)

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    // Dates are stored as "yyyy-MM-dd HH:mm:ss" (or date-only). Format leniently.
    private fun fmtDateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "-"
        val datePart = raw.take(10)
        val d = fmtDate(datePart)
        val time = raw.drop(11).take(5)
        return if (time.isNotBlank() && time != "00:00") "$d $time" else d
    }

    private fun fmtDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "-"
        val p = raw.take(10).split("-")
        return if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else raw.take(10)
    }

    private fun nowIso(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }
}
