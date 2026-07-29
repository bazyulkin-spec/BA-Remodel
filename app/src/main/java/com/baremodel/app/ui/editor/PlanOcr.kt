package com.baremodel.app.ui.editor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Число, прочитанное на чертеже: значение в метрах и место на картинке (в пикселях).
 * [horizontal] — размер подписан вдоль горизонтали (обычно относится к горизонтальной стене).
 */
data class OcrNumber(
    val meters: Double,
    val cx: Double,
    val cy: Double,
    val horizontal: Boolean,
    val raw: String,
)

/**
 * Распознавание размеров на фото плана. Работает на устройстве, офлайн.
 * Печатные чертежи читаются хорошо, рукописные — как повезёт, поэтому результат
 * показывается пользователю, а не применяется молча.
 */
object PlanOcr {

    /** Числа на чертеже: 555, 4,25, 1200. Единицы угадываются по величине. */
    private val NUM = Regex("""\d{1,5}(?:[.,]\d{1,2})?""")

    fun read(bitmap: Bitmap, onDone: (List<OcrNumber>) -> Unit) {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val out = ArrayList<OcrNumber>()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        for (el in line.elements) {
                            val m = NUM.find(el.text) ?: continue
                            val v = m.value.replace(',', '.').toDoubleOrNull() ?: continue
                            val box = el.boundingBox ?: continue
                            val meters = toMeters(v) ?: continue
                            out.add(
                                OcrNumber(
                                    meters = meters,
                                    cx = box.exactCenterX().toDouble(),
                                    cy = box.exactCenterY().toDouble(),
                                    horizontal = box.width() >= box.height(),
                                    raw = m.value,
                                ),
                            )
                        }
                    }
                }
                onDone(out.sortedByDescending { it.meters })
            }
            .addOnFailureListener { onDone(emptyList()) }
    }

    /**
     * Единицы на чертежах разные: 5.55 — метры, 555 — сантиметры, 5550 — миллиметры.
     * Значения вне разумного для помещения диапазона отбрасываются.
     */
    private fun toMeters(v: Double): Double? {
        val m = when {
            v < 30 -> v
            v < 1000 -> v / 100.0
            else -> v / 1000.0
        }
        return if (m in 0.15..60.0) m else null
    }
}
