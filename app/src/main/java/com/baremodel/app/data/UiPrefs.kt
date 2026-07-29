package com.baremodel.app.data

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Настройки интерфейса: язык и масштаб. Значения — Compose-состояние,
 * поэтому изменение размера применяется мгновенно без перезапуска.
 */
object UiPrefs {

    /** Масштаб интерфейса: 0.9 компактный · 1.0 обычный · 1.12 крупный. */
    var scale by mutableStateOf(1f)
        private set

    /** Язык: "system" / "ru" / "en". */
    var lang by mutableStateOf("system")
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scale = p.getFloat(KEY_SCALE, 1f)
        lang = p.getString(KEY_LANG, "system") ?: "system"
    }

    fun updateScale(context: Context, v: Float) {
        scale = v.coerceIn(0.8f, 1.3f)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SCALE, scale).apply()
    }

    /** После смены языка вызывающая сторона делает recreate() активности. */
    fun updateLang(context: Context, v: String) {
        lang = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, v).apply()
    }

    /** Локаль приложения: для дат и чисел в отчётах. */
    fun locale(base: Context): Locale {
        val code = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "system") ?: "system"
        return if (code == "system") Locale.getDefault() else Locale(code)
    }

    /** Обёртка контекста с выбранным языком; "system" оставляет системный. */
    fun wrap(base: Context): Context {
        val code = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "system") ?: "system"
        if (code == "system") return base
        val locale = Locale(code)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }

    private const val PREFS = "ba_ui"
    private const val KEY_SCALE = "scale"
    private const val KEY_LANG = "lang"
}
