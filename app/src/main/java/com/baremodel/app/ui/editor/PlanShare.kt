package com.baremodel.app.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.baremodel.app.R
import com.baremodel.app.ar.renderFloorBitmap
import com.baremodel.app.ui.theme.Good
import com.baremodel.core.Pt
import com.baremodel.core.pointInPolygon
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Быстрый шаринг схемы одним нажатием: PNG плана с полосой стен, проёмами,
 * подписанными СЛОВАМИ («Дверь», «Балкон», «Вход»…), размерами сторон и итогами
 * внизу — получатель понимает расположение сразу, без легенд и расшифровок.
 */
object PlanShare {

    /** Полный чертёж как Bitmap; withHeader=false — без шапки/итогов, для PDF. */
    fun renderBitmap(
        context: Context,
        vm: EditorViewModel,
        withHeader: Boolean = true,
    ): Bitmap? {
        val pts = vm.room.points
        if (pts.size < 3) return null
        fun s(id: Int) = context.getString(id)

        val base = renderFloorBitmap(
            points = pts,
            tiles = vm.layout.tiles,
            decorIdx = vm.decorIdx,
            tileBmp = vm.tileImage?.asAndroidBitmap(),
            decorBmp = (vm.decorImage ?: vm.tileImage)?.asAndroidBitmap(),
            colorArgb = vm.tileColor.toArgb(),
            variation = vm.variation,
            panel = vm.panelInfo(),
            extra = vm.zoneLayers(),
            colorOf = { t -> vm.colorOfTile(t) },
            cutNumbers = vm.showCuts,
            cutInfo = vm.cutInfo,
        ).first

        val minx = pts.minOf { it.x }
        val miny = pts.minOf { it.y }
        val wMd = (pts.maxOf { it.x } - minx).coerceAtLeast(0.01)
        val ppm = base.width / wMd.toFloat()

        val padS = if (withHeader) 110 else 48
        val padT = if (withHeader) 100 else 48
        val footH = if (withHeader) 150 else 48
        val out = Bitmap.createBitmap(
            base.width + padS * 2,
            base.height + padT + footH,
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.rgb(11, 16, 26))
        c.drawBitmap(base, padS.toFloat(), padT.toFloat(), null)

        fun px(x: Double) = ((x - minx) * ppm).toFloat() + padS
        fun py(y: Double) = ((y - miny) * ppm).toFloat() + padT

        val wallFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(46, 56, 76)
        }
        val wallEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(150, 165, 190)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val bgFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(11, 16, 26)
        }

        // 1) полоса стен наружу
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val len = sqrt(ex * ex + ey * ey)
            if (len < 1e-6) continue
            var nx = ey / len
            var ny = -ex / len
            val mid = Pt(a.x + ex / 2, a.y + ey / 2)
            if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), pts)) {
                nx = -nx
                ny = -ny
            }
            val th = vm.wallThicknessOf("wall-" + (i + 1))
            val band = Path().apply {
                moveTo(px(a.x), py(a.y))
                lineTo(px(b.x), py(b.y))
                lineTo(px(b.x + nx * th), py(b.y + ny * th))
                lineTo(px(a.x + nx * th), py(a.y + ny * th))
                close()
            }
            c.drawPath(band, wallFill)
            c.drawPath(band, wallEdge)
        }

        // 2) проёмы: разрыв, дуга у дверных и слово-табличка снаружи
        val kindWords = listOf(
            s(R.string.kind_window), s(R.string.kind_door), s(R.string.kind_balcony),
            s(R.string.kind_entry), s(R.string.kind_passage),
        )
        val kindTones = (0..4).map { openingTone(it).toArgb() }
        val wordTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 27f
            isFakeBoldText = true
        }
        for (i in pts.indices) {
            val id = "wall-" + (i + 1)
            val listO = vm.openingsOf(id)
            if (listO.isEmpty()) continue
            val kinds = vm.openingKindsOf(id)
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val len = sqrt(ex * ex + ey * ey)
            if (len < 1e-6) continue
            val ux = ex / len
            val uy = ey / len
            var nx = ey / len
            var ny = -ex / len
            val mid = Pt(a.x + ex / 2, a.y + ey / 2)
            if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), pts)) {
                nx = -nx
                ny = -ny
            }
            val th = vm.wallThicknessOf(id)
            listO.forEachIndexed { oi, o ->
                val kind = kinds.getOrNull(oi) ?: OPENING_WINDOW
                val tone = kindTones.getOrElse(kind) { openingTone(1).toArgb() }
                val p0 = Pt(a.x + ux * o.x, a.y + uy * o.x)
                val p1 = Pt(a.x + ux * (o.x + o.w), a.y + uy * (o.x + o.w))
                val gap = Path().apply {
                    moveTo(px(p0.x), py(p0.y))
                    lineTo(px(p1.x), py(p1.y))
                    lineTo(px(p1.x + nx * th), py(p1.y + ny * th))
                    lineTo(px(p0.x + nx * th), py(p0.y + ny * th))
                    close()
                }
                c.drawPath(gap, bgFill)
                c.drawPath(
                    gap,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = tone
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    },
                )
                // дуга открывания у дверных — как на чертеже
                if (kind != OPENING_WINDOW) {
                    val r = o.w
                    val arc = Path()
                    for (k in 0..10) {
                        val ang = k / 10.0 * (Math.PI / 2)
                        val ax2 = p0.x + ux * (r * cos(ang)) - nx * (r * sin(ang))
                        val ay2 = p0.y + uy * (r * cos(ang)) - ny * (r * sin(ang))
                        if (k == 0) arc.moveTo(px(ax2), py(ay2)) else arc.lineTo(px(ax2), py(ay2))
                    }
                    c.drawPath(
                        arc,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = tone
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                            pathEffect = DashPathEffect(floatArrayOf(9f, 7f), 0f)
                        },
                    )
                    c.drawLine(
                        px(p0.x), py(p0.y),
                        px(p0.x - nx * r), py(p0.y - ny * r),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = tone
                            strokeWidth = 3.5f
                        },
                    )
                }
                // слово-табличка снаружи стены с указателем
                val cxW = a.x + ux * (o.x + o.w / 2)
                val cyW = a.y + uy * (o.x + o.w / 2)
                val gx = px(cxW + nx * th)
                val gy = py(cyW + ny * th)
                val bx = gx + nx.toFloat() * 52f
                val by = gy + ny.toFloat() * 52f
                c.drawLine(
                    gx, gy, bx, by,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = tone
                        strokeWidth = 2.5f
                    },
                )
                val word = kindWords.getOrElse(kind) { "?" }
                val tw = wordTxt.measureText(word)
                val rr = RectF(bx - tw / 2 - 13f, by - 18f, bx + tw / 2 + 13f, by + 18f)
                c.drawRoundRect(
                    rr, 13f, 13f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone },
                )
                c.drawRoundRect(
                    rr, 13f, 13f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(11, 18, 32)
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                    },
                )
                c.drawText(word, bx, by + 8f, wordTxt)
            }
        }

        // 3) размеры сторон — внутрь комнаты, чтобы не мешать табличкам
        val dimTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Good.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 23f
            isFakeBoldText = true
        }
        val dimBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(215, 15, 22, 34)
        }
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val len = sqrt(ex * ex + ey * ey)
            if (len * ppm < 80f) continue
            var nx = ey / len
            var ny = -ex / len
            val mid = Pt(a.x + ex / 2, a.y + ey / 2)
            if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), pts)) {
                nx = -nx
                ny = -ny
            }
            val tx = px(mid.x) - nx.toFloat() * 34f
            val ty = py(mid.y) - ny.toFloat() * 34f
            val label = String.format(Locale.getDefault(), "%.2f", len) + " " + s(R.string.unit_m)
            val tw = dimTxt.measureText(label)
            c.drawRoundRect(
                RectF(tx - tw / 2 - 9f, ty - 16f, tx + tw / 2 + 9f, ty + 16f),
                9f, 9f, dimBg,
            )
            c.drawText(label, tx, ty + 8f, dimTxt)
        }

        // 4) заголовок и итоги
        if (!withHeader) return out
        val h1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(230, 236, 245)
            textSize = 36f
            isFakeBoldText = true
        }
        c.drawText(
            vm.projectName.ifBlank { s(R.string.default_name) },
            padS.toFloat(), 58f, h1,
        )
        val l = vm.layout
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(200, 210, 225)
            textSize = 26f
        }
        val fy = (padT + base.height + 56).toFloat()
        c.drawText(
            s(R.string.area) + ": " +
                String.format(Locale.getDefault(), "%.2f", l.areaM2) + " " + s(R.string.unit_m2) +
                "    " + s(R.string.full_tiles) + ": " + l.fullCount +
                "    " + s(R.string.cut_tiles) + ": " + l.cutCount +
                "    " + s(R.string.buy) + ": " + vm.buyCount + " " + s(R.string.pcs) +
                " (" + vm.tile.widthMm.toInt() + "×" + vm.tile.heightMm.toInt() + ")",
            padS.toFloat(), fy, body,
        )
        c.drawText(
            s(R.string.wm_brand),
            padS.toFloat(), fy + 44f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(118, 130, 152)
                textSize = 22f
            },
        )

        return out
    }

    /** Отправить готовый чертёж одним нажатием. */
    fun share(context: Context, vm: EditorViewModel) {
        val out = renderBitmap(context, vm) ?: return
        fun s(id: Int) = context.getString(id)
        // сохранить и пошарить (та же папка, что у PDF — разрешена провайдером)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "BA-Remodel_plan.png")
        runCatching {
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, s(R.string.share_plan)))
    }
}
