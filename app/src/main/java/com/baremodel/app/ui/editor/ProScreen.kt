package com.baremodel.app.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.AccDeep
import com.baremodel.app.ui.theme.AccSoft
import com.baremodel.app.ui.theme.BaIcons
import com.baremodel.app.ui.theme.Dim
import com.baremodel.app.ui.theme.Good
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
    /** Есть ли активная подписка. */
    var isPro: Boolean = false
        private set

    /** Показывать ли рекламу (у подписчиков — никогда). */
    val showAds: Boolean get() = !isPro

    /** Функции уровня Pro. */
    val tileEditor: Boolean get() = isPro
    val surfaces: Boolean get() = isPro
    val furniture: Boolean get() = isPro
    val brandedPdf: Boolean get() = isPro

    fun setProForTesting(v: Boolean) { isPro = v }
}

@Composable
fun ProScreen() {
    var plan by rememberSaveable { mutableStateOf(1) }
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

        Spacer(Modifier.height(16.dp))
        listOf(
            R.string.pro_f1, R.string.pro_f2, R.string.pro_f3, R.string.pro_f4, R.string.pro_f5,
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

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanCard(stringResource(R.string.plan_month), plan == 0, Modifier.weight(1f)) { plan = 0 }
            PlanCard(stringResource(R.string.plan_year), plan == 1, Modifier.weight(1f)) { plan = 1 }
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel2)
                .border(1.dp, LineC, RoundedCornerShape(16.dp))
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.soon), color = Dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.pro_note),
            color = Dim,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.restore),
            color = Sub,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .padding(8.dp),
        )
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
