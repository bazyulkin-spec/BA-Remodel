package com.baremodel.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

/**
 * Ловец падений для раздачи в люди: без него о сбое у чужого человека
 * никто не узнает — он просто поставит одну звезду и уйдёт.
 *
 * Как работает: необработанное исключение пишется в файл (модель телефона,
 * версия Android и приложения, стек), затем падение отдаётся системе как обычно.
 * НИКАКИХ диалогов при старте: сообщение «программа упала» — это программа,
 * объявляющая о своей сырости. Отчёт лежит тихо; строка «Отправить отчёт о сбое»
 * появляется только в «О программе» и только если сбой реально был. Никакой сети
 * и сторонних сервисов: отчёт уходит только руками пользователя.
 */
object CrashGuard {

    private const val FILE = "crash-report.txt"

    /** Держим отчёт компактным: хвост стека важнее начала простыни. */
    private const val MAX_CHARS = 12_000

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull() ?: "?"
                val head = buildString {
                    append("BA-Remodel ").append(version).append('\n')
                    append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(" · Android ").append(Build.VERSION.RELEASE)
                    append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Поток: ").append(thread.name).append('\n').append('\n')
                }
                val stack = android.util.Log.getStackTraceString(e)
                val body = if (stack.length > MAX_CHARS) {
                    stack.take(2_000) + "\n…\n" + stack.takeLast(MAX_CHARS - 2_000)
                } else {
                    stack
                }
                File(app.filesDir, FILE).writeText(head + body)
            }
            // падение отдаём системе: процесс должен умереть как обычно
            prev?.uncaughtException(thread, e)
        }
    }

    /** Отчёт прошлого сбоя или null. Файл НЕ удаляется — см. [clear]. */
    fun peek(context: Context): String? =
        runCatching { File(context.applicationContext.filesDir, FILE).readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Убрать отчёт после отправки — строка в «О программе» исчезает. */
    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }

    /** Системный шаринг: пользователь сам выбирает, куда отправить (почта, мессенджер). */
    fun share(context: Context, report: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BA-Remodel crash report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { context.startActivity(Intent.createChooser(send, title)) }
    }
}
