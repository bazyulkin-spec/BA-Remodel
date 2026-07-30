package com.baremodel.app.ui.editor

import android.content.Intent
import com.baremodel.app.data.CrashGuard
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import com.baremodel.app.R
import com.baremodel.app.data.TrialManager
import com.baremodel.app.data.UiPrefs
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.AccDeep
import com.baremodel.app.ui.theme.AccSoft
import com.baremodel.app.ui.theme.BaIcons
import com.baremodel.app.ui.theme.Dim
import com.baremodel.app.ui.theme.Good
import com.baremodel.app.ui.theme.Warn
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt

/**
 * Единая точка «что доступно». Пока статична: подписка подключается отдельным шагом
 * (Play Billing 9 + Play Integrity), но весь интерфейс уже ходит через неё.
 */
object Entitlements {
    /** Куплено ли отключение знаков (донат-код). Compose-состояние: интерфейс обновляется сам. */
    var isPro by mutableStateOf(false)
        private set

    /** Сколько дней пробного периода осталось. */
    var trialDaysLeft: Int = TrialManager.TRIAL_DAYS
        private set

    /** Доступ открыт: либо подписка, либо действующий пробный период. */
    val active: Boolean get() = isPro || trialDaysLeft > 0

    /** Реклама показывается только когда доступ закрыт (и после подключения рекламы). */
    val showAds: Boolean get() = !active

    val tileEditor: Boolean get() = active
    val surfaces: Boolean get() = active
    val furniture: Boolean get() = active
    val brandedPdf: Boolean get() = active

    /** Водяной знак показывается всем, кроме оплативших — в том числе во время пробного периода. */
    val watermark: Boolean get() = !isPro

    /** Вызывается один раз при старте приложения. */
    fun init(context: Context) {
        trialDaysLeft = TrialManager(context).daysLeft
        isPro = context.getSharedPreferences("ba_pro", Context.MODE_PRIVATE).getBoolean("pro", false)
    }

    /**
     * Код разблокировки: почта донатера → 4 символа base36.
     * Тот же алгоритм зашит в play/code-generator.html у автора.
     */
    fun expectedCode(email: String): String {
        val src = email.trim().lowercase() + "|ba-remodel-50"
        var h = 0L
        for (ch in src) h = (h * 31 + ch.code) % 1_000_003L
        return (h % 1_679_616L).toString(36).uppercase().padStart(4, '0')
    }

    fun activate(context: Context, email: String, code: String): Boolean {
        val clean = code.trim().uppercase().removePrefix("BA-").replace("-", "")
        if (email.isBlank() || clean != expectedCode(email)) return false
        context.getSharedPreferences("ba_pro", Context.MODE_PRIVATE)
            .edit().putBoolean("pro", true).apply()
        isPro = true
        return true
    }

    fun setProForTesting(v: Boolean) { isPro = v }
}

@Composable
fun ProScreen() {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Acc, AccDeep)))
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(BaIcons.Star, null, Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.pro_title),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.pro_sub),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        val days = Entitlements.trialDaysLeft
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (days > 0) Good.copy(alpha = 0.12f) else Warn.copy(alpha = 0.12f))
                .border(1.dp, if (days > 0) Good.copy(alpha = 0.45f) else Warn.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (days > 0) BaIcons.Check else BaIcons.Star, null, Modifier.size(18.dp), tint = if (days > 0) Good else Warn)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (days > 0) stringResource(R.string.trial_left, days) else stringResource(R.string.trial_over),
                    color = Txt,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.trial_note), color = Sub, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, LineC, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text(
                stringResource(R.string.lang_title),
                color = Dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(stringResource(R.string.lang_system), UiPrefs.lang == "system") {
                    UiPrefs.updateLang(context, "system")
                    (context as? Activity)?.recreate()
                }
                Chip("Русский", UiPrefs.lang == "ru") {
                    UiPrefs.updateLang(context, "ru")
                    (context as? Activity)?.recreate()
                }
                Chip("English", UiPrefs.lang == "en") {
                    UiPrefs.updateLang(context, "en")
                    (context as? Activity)?.recreate()
                }
                Chip("עברית", UiPrefs.lang == "iw") {
                    UiPrefs.updateLang(context, "iw")
                    (context as? Activity)?.recreate()
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.ui_size),
                color = Dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(stringResource(R.string.size_compact), UiPrefs.scale < 0.95f) {
                    UiPrefs.updateScale(context, 0.9f)
                }
                Chip(stringResource(R.string.size_normal), UiPrefs.scale in 0.95f..1.05f) {
                    UiPrefs.updateScale(context, 1f)
                }
                Chip(stringResource(R.string.size_large), UiPrefs.scale > 1.05f) {
                    UiPrefs.updateScale(context, 1.12f)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        listOf(
            R.string.pro_f1, R.string.pro_f2, R.string.pro_f3, R.string.pro_f4, R.string.pro_f5,
            R.string.pro_f6,
        ).forEach { res ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BaIcons.Check, null, Modifier.size(17.dp), tint = Good)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(res), color = Txt, fontSize = 13.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BaIcons.Check, null, Modifier.size(17.dp), tint = Good)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.ad_free), color = Txt, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        DonateCard()
        // тихая строка: видна только после реального сбоя, никаких диалогов на старте
        run {
            val ctx = LocalContext.current
            val report = remember { CrashGuard.peek(ctx) }
            var sent by remember { mutableStateOf(false) }
            if (report != null && !sent) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Panel2)
                        .border(1.dp, LineC, RoundedCornerShape(11.dp))
                        .clickable {
                            CrashGuard.share(ctx, report, ctx.getString(R.string.crash_send))
                            CrashGuard.clear(ctx)
                            sent = true
                        }
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.crash_send),
                            color = Txt, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.crash_quiet_note), color = Sub, fontSize = 10.5.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.credit),
            color = Dim,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DonateCard() {
    val context = LocalContext.current
    val mail = "bazyulkin@gmail.com"
    var showDialog by remember { mutableStateOf(false) }
    var emailIn by remember { mutableStateOf("") }
    var codeIn by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, LineC, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.donate), color = Acc2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.donate_offer), color = Txt, fontSize = 12.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.donate_thanks), color = Sub, fontSize = 11.5.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AccSoft)
                    .border(1.dp, Acc.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/donate?business=" + mail))
                            )
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("PayPal · 50 ₪", color = Acc2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Panel2)
                    .border(1.dp, LineC, RoundedCornerShape(13.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + mail)))
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.write_author), color = Sub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(9.dp))
        if (Entitlements.isPro) {
            Text(stringResource(R.string.code_ok), color = Good, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Panel2)
                    .border(1.dp, LineC, RoundedCornerShape(13.dp))
                    .clickable { showDialog = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.have_code), color = Txt, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(mail, color = Dim, fontSize = 10.5.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Panel2,
            title = { Text(stringResource(R.string.have_code), color = Txt) },
            text = {
                Column {
                    OutlinedTextField(
                        value = emailIn,
                        onValueChange = { emailIn = it; codeError = false },
                        label = { Text(stringResource(R.string.enter_mail)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = codeIn,
                        onValueChange = { codeIn = it; codeError = false },
                        label = { Text(stringResource(R.string.enter_code)) },
                        singleLine = true,
                    )
                    if (codeError) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.code_bad), color = Warn, fontSize = 11.5.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Entitlements.activate(context, emailIn, codeIn)) showDialog = false else codeError = true
                }) { Text(stringResource(R.string.apply), color = Acc2) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Sub)
                }
            },
        )
    }
}

@Composable
private fun PlanCard(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AccSoft else Panel)
            .border(1.dp, if (selected) Acc else LineC, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Text(title, color = if (selected) Acc2 else Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text("—", color = Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
