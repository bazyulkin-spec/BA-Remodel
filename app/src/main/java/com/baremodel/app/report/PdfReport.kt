package com.baremodel.app.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.baremodel.app.R
import com.baremodel.core.CutNumbering
import com.baremodel.core.LayoutResult
import com.baremodel.core.PatternSpec
import com.baremodel.app.ar.ExtraLayer
import com.baremodel.app.ar.PanelInfo
import com.baremodel.app.ar.renderFloorBitmap
import com.baremodel.app.data.UiPrefs
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
        thresholdPieces: Int = 0,
        pairSaved: Int = 0,
        planBmp: Bitmap? = null,
        buyM2: Double,
        patternLabel: String,
        watermark: Boolean = true,
        logo: Bitmap? = null,
        stairsRows: List<Pair<String, String>> = emptyList(),
        stairsCuts: List<String> = emptyList(),
        estimate: List<Pair<String, String>> = emptyList(),
        estimateTotal: String? = null,
        apartment: List<Pair<String, String>> = emptyList(),
        apartmentTotal: String? = null,
        colorArgb: Int = -1,
        variation: Boolean = false,
        decorIdx: Set<Int> = emptySet(),
        tileBmp: Bitmap? = null,
        decorBmp: Bitmap? = null,
        panel: PanelInfo? = null,
        extra: List<ExtraLayer> = emptyList(),
        colorOf: ((com.baremodel.core.PlacedTile) -> Int?)? = null,
        cutNumbers: Boolean = false,
        opts: com.baremodel.app.data.ReportOptions = com.baremodel.app.data.ReportOptions(),
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

        // фоновая диагональ — заметна на просвет, но не мешает читать
        if (watermark) {
            val wm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(16, 61, 139, 255)
                textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.save()
            c.rotate(-28f, 297f, 421f)
            var wy = 120f
            var alt = false
            while (wy < 900f) {
                c.drawText(if (alt) "Baziulkin Alexander" else "BA-Remodel", 297f, wy, wm)
                alt = !alt
                wy += 170f
            }
            c.restore()
        }

        // логотип мастера — или рамка-заглушка на его месте
        if (logo != null) {
            val bw = logo.width.toFloat()
            val bh = logo.height.toFloat()
            val k = minOf(125f / bw, 40f / bh)
            val lw = bw * k
            val lh = bh * k
            val left = 430f + (125f - lw) / 2f
            val top = 34f + (40f - lh) / 2f
            c.drawBitmap(
                logo, null,
                android.graphics.RectF(left, top, left + lw, top + lh),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        } else if (watermark) {
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(214, 222, 236)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            c.drawRoundRect(430f, 34f, 555f, 74f, 8f, 8f, box)
            val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(150, 162, 180)
                textSize = 7.5f
                textAlign = Paint.Align.CENTER
            }
            c.drawText(s(R.string.wm_slot), 492f, 58f, hint)
        }

        // план раскладки — в правой колонке, где страница пустует
        if (opts.plan) {
            val fb = renderFloorBitmap(
                points = room.points,
                tiles = layout.tiles,
                decorIdx = decorIdx,
                tileBmp = tileBmp,
                decorBmp = decorBmp,
                colorArgb = if (colorArgb != -1) colorArgb else android.graphics.Color.rgb(199, 204, 214),
                variation = variation,
                panel = panel,
                extra = extra,
                colorOf = colorOf,
                cutNumbers = cutNumbers,
                cutInfo = if (cutNumbers) CutNumbering.compute(room, layout) else null,
            )
            // если передан полный чертёж (стены, проёмы, размеры) — он в приоритете
            val bmp = planBmp ?: fb.first
            val pw = 233f
            val ph = 150f
            val k = minOf(pw / bmp.width, ph / bmp.height)
            val w2 = bmp.width * k
            val hh = bmp.height * k
            val boxTop = 100f
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(214, 222, 236)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            c.drawRoundRect(310f, boxTop, 555f, boxTop + ph + 44f, 8f, 8f, frame)
            c.drawText(s(R.string.plan_title), 322f, boxTop + 17f, h2)
            val left = 316f + (pw - w2) / 2f
            c.drawBitmap(
                bmp, null,
                android.graphics.RectF(left, boxTop + 24f, left + w2, boxTop + 24f + hh),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            c.drawText(
                n2(fb.second.toDouble()) + " × " + n2(fb.third.toDouble()) + " " + s(R.string.unit_m),
                322f, boxTop + ph + 37f, small,
            )
        }

        var y = 56f
        c.drawText(s(R.string.credit), 40f, y - 22f, small)
        c.drawText(name.ifBlank { s(R.string.default_name) }, 40f, y, title)
        y += 16f
        c.drawText(
            s(R.string.date) + ": " + DateFormat.getDateInstance(DateFormat.LONG, UiPrefs.locale(context)).format(Date()),
            40f, y, small,
        )
        y += 16f
        c.drawLine(40f, y, 555f, y, rule)

        // параметры
        if (opts.params) {
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
        }

        // материалы
        if (opts.results) {
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
        if (opts.stairs && stairsRows.isNotEmpty()) {
            y += 19f
            c.drawText(s(R.string.stairs_title), 40f, y, bodyBold)
            for (row in stairsRows) {
                y += 14f
                c.drawText(row.first + ": " + row.second, 40f, y, body)
            }
            for (line in stairsCuts) {
                y += 14f
                c.drawText(line, 40f, y, body)
            }
        }
        if (thresholdPieces > 0 || pairSaved > 0) {
            // числа сходятся: покупка = целые + подрезка с парованием + пороги + запас
            y += 14f
            val parts = ArrayList<String>()
            if (thresholdPieces > 0) parts.add(s(R.string.thresholds_lbl) + " +" + thresholdPieces)
            if (pairSaved > 0) parts.add(s(R.string.pair_saving) + " −" + pairSaved)
            c.drawText(parts.joinToString(" · "), 40f, y, body)
        }
        }

        // сводка по квартире: строка на комнату
        if (opts.apartment && apartment.size > 1) {
            val bodyR2 = Paint(body).apply { textAlign = Paint.Align.RIGHT }
            val bodyBoldR2 = Paint(bodyBold).apply { textAlign = Paint.Align.RIGHT }
            y += 30f
            c.drawText(s(R.string.apt_total), 40f, y, h2)
            for ((label, value) in apartment) {
                if (y > 700f) break
                y += 15f
                c.drawText(label, 40f, y, body)
                c.drawText(value, 555f, y, bodyR2)
            }
            apartmentTotal?.let {
                y += 19f
                c.drawText(s(R.string.grand_total), 40f, y, bodyBold)
                c.drawText(it, 555f, y, bodyBoldR2)
            }
        }

        // карта подрезки
        if (opts.cutMap) {
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
            y = rowY
        }
        }

        // смета
        if (opts.estimate && (estimate.isNotEmpty() || estimateTotal != null)) {
            val bodyR = Paint(body).apply { textAlign = Paint.Align.RIGHT }
            val bodyBoldR = Paint(bodyBold).apply { textAlign = Paint.Align.RIGHT }
            y += 28f
            c.drawText(s(R.string.sec_estimate), 40f, y, h2)
            for ((label, value) in estimate) {
                if (y > 742f) break
                y += 15f
                c.drawText(label, 40f, y, body)
                c.drawText(value, 555f, y, bodyR)
            }
            estimateTotal?.let {
                y += 19f
                c.drawText(s(R.string.grand_total), 40f, y, bodyBold)
                c.drawText(it, 555f, y, bodyBoldR)
            }
        }

        // футер
        c.drawText(s(R.string.disclaimer), 40f, 796f, small)
        c.drawText(
            s(R.string.credit) + if (watermark) "  ·  " + s(R.string.wm_free) else "",
            40f, 810f, small,
        )

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
