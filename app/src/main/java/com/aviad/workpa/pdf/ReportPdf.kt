package com.aviad.workpa.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.aviad.workpa.data.Job
import com.aviad.workpa.data.dateText
import com.aviad.workpa.data.localDate
import com.aviad.workpa.data.money
import com.aviad.workpa.data.monthTitle
import com.aviad.workpa.data.shortDayName
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly work report, A4, right-to-left Hebrew table.
 */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f              // page margin
    private const val ROW = 24f
    private val ACCENT = Color.rgb(255, 179, 0)
    private val DARK = Color.rgb(30, 30, 30)
    private val GRAY = Color.rgb(110, 110, 110)
    private val LINE = Color.rgb(215, 215, 215)
    private val STRIPE = Color.rgb(247, 247, 247)

    private class Col(val title: String, val width: Float)

    fun write(
        out: OutputStream,
        month: YearMonth,
        employerName: String,
        reportName: String,
        jobs: List<Job>,
        showAmounts: Boolean,
    ) {
        val cols = buildList {
            add(Col("#", 24f))
            add(Col("תאריך", 66f))
            add(Col("יום", 46f))
            add(Col("אירוע", if (showAmounts) 150f else 180f))
            add(Col("מיקום", if (showAmounts) 80f else 100f))
            add(Col("הערות", if (showAmounts) 86f else 107f))
            if (showAmounts) add(Col("סכום", 71f))
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; textSize = 10f; textAlign = Paint.Align.RIGHT }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint().apply { style = Paint.Style.FILL }
        val line = Paint().apply { color = LINE; strokeWidth = 0.7f }

        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f
        val right = W - M

        fun header(first: Boolean) {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            fill.color = ACCENT
            c.drawRect(0f, 0f, W.toFloat(), 6f, fill)
            y = M + 10f
            if (first) {
                c.drawText("דוח עבודות – ${monthTitle(month)}", right, y + 12f, Paint(bold).apply { textSize = 20f })
                y += 34f
                c.drawText("עבור: $employerName", right, y, Paint(text).apply { textSize = 12f })
                y += 18f
                if (reportName.isNotBlank()) {
                    c.drawText("מאת: $reportName", right, y, Paint(text).apply { textSize = 12f })
                    y += 18f
                }
                c.drawText("הופק בתאריך ${dateText(LocalDate.now())}", right, y,
                    Paint(text).apply { color = GRAY })
                y += 22f
            }
            // table header
            fill.color = DARK
            c.drawRect(M, y, right, y + ROW, fill)
            var x = right
            val hp = Paint(bold).apply { color = Color.WHITE }
            cols.forEach { col ->
                c.drawText(col.title, x - 5f, y + 16f, hp)
                x -= col.width
            }
            y += ROW
        }

        fun footer() {
            c.drawText("Aviad workPA · עמוד $pageNo", W / 2f, H - 20f,
                Paint(text).apply { color = GRAY; textSize = 8f; textAlign = Paint.Align.CENTER })
            doc.finishPage(page)
        }

        header(first = true)
        jobs.forEachIndexed { i, job ->
            if (y + ROW > H - M - 70f) { footer(); header(first = false) }
            if (i % 2 == 1) { fill.color = STRIPE; c.drawRect(M, y, right, y + ROW, fill) }
            val d = job.event.localDate()
            val cells = buildList {
                add("${i + 1}")
                add(dateText(d))
                add(shortDayName(d))
                add(job.event.title)
                add(job.event.location)
                add(job.meta.notes.replace('\n', ' ').trim())
                if (showAmounts) add(job.meta.amount?.let { money(it) } ?: "—")
            }
            var x = right
            cells.forEachIndexed { ci, s ->
                val w = cols[ci].width
                c.drawText(fit(s, w - 10f, text), x - 5f, y + 16f, text)
                x -= w
            }
            c.drawLine(M, y + ROW, right, y + ROW, line)
            y += ROW
        }

        // totals
        if (y + 70f > H - M) { footer(); header(first = false) }
        y += 16f
        fill.color = Color.rgb(255, 243, 214)
        val boxH = if (showAmounts) 58f else 30f
        c.drawRect(M, y, right, y + boxH, fill)
        fill.color = ACCENT
        c.drawRect(right - 4f, y, right, y + boxH, fill)
        val tp = Paint(bold).apply { textSize = 12f }
        c.drawText("סה״כ עבודות: ${jobs.size}", right - 12f, y + 20f, tp)
        if (showAmounts) {
            val total = jobs.sumOf { it.meta.amount ?: 0.0 }
            val paid = jobs.filter { it.meta.paid }.sumOf { it.meta.amount ?: 0.0 }
            c.drawText("סה״כ לתשלום: ${money(total)}", right - 12f, y + 42f, Paint(tp).apply { textSize = 14f })
            if (paid > 0) {
                c.drawText("שולם: ${money(paid)} · יתרה: ${money(total - paid)}", right - 240f, y + 42f,
                    Paint(text).apply { color = GRAY })
            }
        }
        footer()

        doc.writeTo(out)
        doc.close()
    }

    /** Cut text that does not fit into its column, with an ellipsis. */
    private fun fit(s: String, max: Float, p: Paint): String {
        if (p.measureText(s) <= max) return s
        var n = p.breakText(s, true, max - p.measureText("…"), null)
        if (n < 0) n = 0
        return s.take(n) + "…"
    }
}
