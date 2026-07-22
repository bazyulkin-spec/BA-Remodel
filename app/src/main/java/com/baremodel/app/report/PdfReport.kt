package com.baremodel.app.report

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.baremodel.app.R
import com.baremodel.core.LayoutResult
import com.baremodel.core.PatternSpec
import com.baremodel.core.RoomSpec
import com.baremodel.core.TileSpec
import com.baremodel.core.polygonPerimeter
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Одностраничный PDF-отчёт по расчёту и шаринг через системный chooser. */
object PdfReport {

    fun share(
        context: Context,
        name: String,
        room: RoomSpec,
        tile: TileSpec,
        pattern: PatternSpec,
        layout: LayoutResult,
        reservePct: Int,
        buyCount: Int,
        buyM2: Double,
        patternLabel: String,
    ) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(11, 19, 34)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(61, 139, 255)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 38, 52)
            textSize = 11f
        }
        val bodyBold = Paint(body).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(120, 132, 150)
            textSize = 8.5f
        }
        val rule = Paint().apply { color = android.graphics.Color.rgb(220, 226, 236); strokeWidth = 1f }

        fun s(id: Int) = context.getString(id)
        fun n2(v: Double) = String.format(Locale.getDefault(), "%.2f", v)

        var y = 56f
        c.drawText(s(R.string.credit), 40f, y - 22f, small)
        c.drawText(name.ifBlank { s(R.string.default_name) }, 40f, y, title)
        y += 16f
        c.drawText(
            s(R.string.date) + ": " + DateFormat.getDateInstance(DateFormat.LONG).format(Date()),
            40f, y, small,
        )
        y += 16f
        c.drawLine(40f, y, 555f, y, rule)

        // параметры
        y += 26f
        c.drawText(s(R.string.params), 40f, y, h2)
        y += 18f
        c.drawText(
            s(R.string.room_label) + ": " + n2(layout.areaM2) + " " + s(R.string.unit_m2) +
                " · " + s(R.string.perimeter) + " " + n2(polygonPerimeter(room.points)) + " " + s(R.string.unit_m),
            40f, y, body,
        )
        y += 15f
        c.drawText(
            s(R.string.tile_label) + ": " + tile.widthMm.toInt() + "×" + tile.heightMm.toInt() + " " +
                s(R.string.unit_mm) + " · " + s(R.string.grout) + " " + tile.groutMm.toInt() + " " + s(R.string.unit_mm),
            40f, y, body,
        )
        y += 15f
        c.drawText(s(R.string.layout_label) + ": " + patternLabel, 40f, y, body)
        if (room.cutouts.isNotEmpty()) {
            y += 15f
            c.drawText(s(R.string.add_cutout) + ": " + room.cutouts.size, 40f, y, body)
        }

        // материалы
        y += 30f
        c.drawText(s(R.string.results), 40f, y, h2)
        y += 18f
        c.drawText(s(R.string.full_tiles) + ": " + layout.fullCount, 40f, y, body)
        y += 15f
        c.drawText(s(R.string.cut_tiles) + ": " + layout.cutCount, 40f, y, body)
        y += 15f
        c.drawText(s(R.string.total_tiles) + ": " + layout.totalCount, 40f, y, body)
        y += 15f
        c.drawText(s(R.string.reserve) + ": " + reservePct + "%", 40f, y, body)
        y += 19f
        c.drawText(
            s(R.string.buy) + ": " + buyCount + " " + s(R.string.pcs) + " ≈ " + n2(buyM2) + " " + s(R.string.unit_m2),
            40f, y, bodyBold,
        )

        // карта подрезки
        y += 30f
        c.drawText(s(R.string.cut_map), 40f, y, h2)
        y += 18f
        if (layout.cutPieces.isEmpty()) {
            c.drawText(s(R.string.no_cuts), 40f, y, body)
        } else {
            val shown = layout.cutPieces.take(28)
            var col = 0
            var rowY = y
            shown.forEach { p ->
                val x = 40f + col * 175f
                c.drawText(
                    n2(p.aCm) + " × " + n2(p.bCm) + " " + s(R.string.unit_cm) + " · " + p.count + " " + s(R.string.pcs),
                    x, rowY, body,
                )
                col++
                if (col == 3) { col = 0; rowY += 15f }
            }
            if (col != 0) rowY += 15f
            if (layout.cutPieces.size > shown.size) {
                c.drawText("…", 40f, rowY, body)
            }
        }

        // футер
        c.drawText(s(R.string.disclaimer), 40f, 796f, small)
        c.drawText(s(R.string.credit), 40f, 810f, small)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safe = name.ifBlank { s(R.string.default_name) }
            .trim().replace(Regex("[^\\w\\u0400-\\u04FF -]"), "_").take(60)
        val file = File(dir, "BA-Remodel_$safe.pdf")
        runCatching {
            FileOutputStream(file).use { doc.writeTo(it) }
        }
        doc.close()

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, s(R.string.share_pdf)))
    }
}
