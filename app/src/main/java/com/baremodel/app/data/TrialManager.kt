package com.baremodel.app.data

import android.content.Context
import java.io.File

/**
 * Пробный период: 30 дней полного доступа с первого запуска.
 *
 * Дата старта дублируется в двух местах — SharedPreferences и файл в filesDir —
 * и оба включены в правила резервного копирования. Если у пользователя работает
 * облачная копия Google, переустановка не сбросит отсчёт. Полностью надёжной эта
 * схема не является: копию можно отключить, а данные очистить. Настоящий пробный
 * период появится вместе с подпиской Google Play, где он привязан к аккаунту.
 */
class TrialManager(context: Context) {

    private val prefs = context.getSharedPreferences("ba_trial", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "trial.dat")

    val startedAt: Long
        get() {
            val fromPrefs = prefs.getLong(KEY_START, 0L)
            val fromFile = runCatching { file.readText().trim().toLong() }.getOrDefault(0L)
            // берём самую раннюю известную дату — так переустановка не удлиняет период
            val known = listOf(fromPrefs, fromFile).filter { it > 0L }.minOrNull()
            val start = known ?: System.currentTimeMillis()
            if (fromPrefs != start) prefs.edit().putLong(KEY_START, start).apply()
            if (fromFile != start) runCatching { file.writeText(start.toString()) }
            return start
        }

    /** Сколько дней пробного периода осталось (0 — закончился). */
    val daysLeft: Int
        get() {
            val passed = (System.currentTimeMillis() - startedAt) / DAY_MS
            return (TRIAL_DAYS - passed).coerceIn(0L, TRIAL_DAYS.toLong()).toInt()
        }

    val isActive: Boolean get() = daysLeft > 0

    companion object {
        const val TRIAL_DAYS = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val KEY_START = "started_at"
    }
}
