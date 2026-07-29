package com.baremodel.app.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.baremodel.core.CutPieceInfo
import com.baremodel.core.PlacedTile
import com.baremodel.core.Pt
import com.baremodel.core.TileClass
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/**
 * Мост между редактором и AR-экраном: активность не имеет доступа к ViewModel,
 * поэтому раскладка передаётся готовой картинкой пола и её размерами в метрах.
 */
object ArBridge {
    @Volatile var floorBitmap: Bitmap? = null
    @Volatile var widthM: Float = 1f
    @Volatile var heightM: Float = 1f

    /** Варианты раскладки для показа клиенту: подпись → картинка пола. */
    @Volatile var variants: List<Pair<String, Bitmap>> = emptyList()

    /** Короткая сводка по комнате: площадь, к покупке, подрезка — видна прямо в камере. */
    @Volatile var info: String = ""

    fun clear() {
        floorBitmap = null
        variants = emptyList()
    }
}

/** Панно: картинка растянута на cols×rows плиток; cellOf говорит, какая клетка у плитки. */
data class PanelInfo(
    val cols: Int,
    val rows: Int,
    val cellOf: (PlacedTile) -> Pair<Int, Int>?,
)

/** Дополнительный слой пола: плитки зоны со своим цветом. */
data class ExtraLayer(
    val tiles: List<PlacedTile>,
    val colorArgb: Int,
    val variation: Boolean,
)

private const val MAX_MESH_TILES = 420
private const val TARGET_PX = 1600f

/**
 * Рисует текущую раскладку в прозрачный Bitmap: снаружи контура комнаты — пусто,
 * внутри — плитки с фактурой или цветом, швами и декором. Возвращает картинку
 * и габариты комнаты в метрах (по ним AR строит квадрат в реальном масштабе).
 */
fun renderFloorBitmap(
    points: List<Pt>,
    tiles: List<PlacedTile>,
    decorIdx: Set<Int>,
    tileBmp: Bitmap?,
    decorBmp: Bitmap?,
    colorArgb: Int,
    variation: Boolean,
    panel: PanelInfo? = null,
    extra: List<ExtraLayer> = emptyList(),
    colorOf: ((PlacedTile) -> Int?)? = null,
    cutNumbers: Boolean = false,
    cutInfo: Map<Int, CutPieceInfo>? = null,
): Triple<Bitmap, Float, Float> {
    if (points.size < 3) {
        return Triple(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888), 1f, 1f)
    }
    val minx = points.minOf { it.x }
    val maxx = points.maxOf { it.x }
    val miny = points.minOf { it.y }
    val maxy = points.maxOf { it.y }
    val wM = (maxx - minx).coerceAtLeast(0.05)
    val hM = (maxy - miny).coerceAtLeast(0.05)
    val ppm = TARGET_PX / max(wM, hM).toFloat()
    val bw = ceil(wM * ppm).toInt().coerceIn(2, 2048)
    val bh = ceil(hM * ppm).toInt().coerceIn(2, 2048)

    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    fun px(x: Double) = ((x - minx) * ppm).toFloat()
    fun py(y: Double) = ((y - miny) * ppm).toFloat()

    val roomPath = Path().apply {
        points.forEachIndexed { i, p ->
            if (i == 0) moveTo(px(p.x), py(p.y)) else lineTo(px(p.x), py(p.y))
        }
        close()
    }
    c.save()
    c.clipPath(roomPath)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val grout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.rgb(42, 49, 64)
    }
    val mesh = Paint(Paint.FILTER_BITMAP_FLAG)
    val useMesh = tiles.size <= MAX_MESH_TILES

    val baseR = Color.red(colorArgb)
    val baseG = Color.green(colorArgb)
    val baseB = Color.blue(colorArgb)

    // куски картинки панно нарезаются один раз на клетку
    val slices = mutableMapOf<Pair<Int, Int>, Bitmap>()
    fun sliceFor(cell: Pair<Int, Int>): Bitmap? {
        val src = decorBmp ?: return null
        if (panel == null || panel.cols < 1 || panel.rows < 1) return null
        return slices.getOrPut(cell) {
            val sw = (src.width / panel.cols).coerceAtLeast(1)
            val sh = (src.height / panel.rows).coerceAtLeast(1)
            val sx0 = (cell.first * sw).coerceIn(0, src.width - sw)
            val sy0 = (cell.second * sh).coerceIn(0, src.height - sh)
            Bitmap.createBitmap(src, sx0, sy0, sw, sh)
        }
    }

    tiles.forEachIndexed { ti, t ->
        val q = t.corners
        val isDecor = ti in decorIdx
        val cell = panel?.cellOf?.invoke(t)
        val own = colorOf?.invoke(t)
        val face = when {
            own != null -> null
            cell != null -> sliceFor(cell) ?: decorBmp
            isDecor -> decorBmp
            else -> tileBmp
        }
        val path = Path().apply {
            moveTo(px(q[0].x), py(q[0].y))
            lineTo(px(q[1].x), py(q[1].y))
            lineTo(px(q[2].x), py(q[2].y))
            lineTo(px(q[3].x), py(q[3].y))
            close()
        }
        if (useMesh && face != null) {
            val verts = floatArrayOf(
                px(q[0].x), py(q[0].y),
                px(q[1].x), py(q[1].y),
                px(q[3].x), py(q[3].y),
                px(q[2].x), py(q[2].y),
            )
            c.drawBitmapMesh(face, 1, 1, verts, 0, null, 0, mesh)
        } else {
            val hs = abs(sin(t.rect.x * 127.1 + t.rect.y * 311.7) * 43758.5453) % 1.0
            val d = if (variation && !isDecor) ((hs - 0.5) * 26).toInt() else 0
            fill.color = if (own != null) {
                own
            } else if (isDecor) {
                Color.rgb(232, 223, 210)
            } else {
                Color.rgb(
                    (baseR + d).coerceIn(0, 255),
                    (baseG + d).coerceIn(0, 255),
                    (baseB + d).coerceIn(0, 255),
                )
            }
            c.drawPath(path, fill)
        }
        c.drawPath(path, grout)
        if (t.cls == TileClass.CUT) {
            if (cutNumbers) {
                val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(46, 255, 180, 84)
                    style = Paint.Style.FILL
                }
                c.drawPath(path, tint)
            }
            val warn = Paint(grout).apply { color = Color.argb(170, 255, 180, 84); strokeWidth = 2.8f }
            c.drawPath(path, warn)
        }
    }
    // номера подрезанных плиток — те же, что на плане: удобно сверять при резке
    if (cutNumbers && tiles.size <= 400) {
        val np = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 180, 84)
            textAlign = Paint.Align.CENTER
            textSize = (ppm * 0.09f).coerceIn(9f, 34f)
            isFakeBoldText = true
        }
        if (cutInfo != null) {
            // единая нумерация: номер стоит в центре видимого куска,
            // крохотные полоски не нумеруются
            cutInfo.values.forEach { ci ->
                c.drawText(ci.number.toString(), px(ci.cx), py(ci.cy) + np.textSize * 0.35f, np)
            }
        } else {
            var no = 0
            tiles.forEach { t ->
                if (t.cls != TileClass.CUT) return@forEach
                no++
                val cx = t.corners.sumOf { it.x } / 4
                val cy = t.corners.sumOf { it.y } / 4
                c.drawText(no.toString(), px(cx), py(cy) + np.textSize * 0.35f, np)
            }
        }
    }

    // зоны: своя плитка поверх базовой раскладки
    extra.forEach { layer ->
        val lr = Color.red(layer.colorArgb)
        val lg = Color.green(layer.colorArgb)
        val lb = Color.blue(layer.colorArgb)
        layer.tiles.forEach { t ->
            val q = t.corners
            val path = Path().apply {
                moveTo(px(q[0].x), py(q[0].y))
                lineTo(px(q[1].x), py(q[1].y))
                lineTo(px(q[2].x), py(q[2].y))
                lineTo(px(q[3].x), py(q[3].y))
                close()
            }
            val hs = abs(sin(t.rect.x * 127.1 + t.rect.y * 311.7) * 43758.5453) % 1.0
            val dd = if (layer.variation) ((hs - 0.5) * 26).toInt() else 0
            fill.color = Color.rgb(
                (lr + dd).coerceIn(0, 255),
                (lg + dd).coerceIn(0, 255),
                (lb + dd).coerceIn(0, 255),
            )
            c.drawPath(path, fill)
            c.drawPath(path, grout)
        }
    }

    c.restore()
    return Triple(bmp, wM.toFloat(), hM.toFloat())
}
