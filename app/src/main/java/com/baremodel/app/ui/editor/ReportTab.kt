package com.baremodel.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.data.UiPrefs
import com.baremodel.app.report.PdfReport
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.AccDeep
import com.baremodel.app.ui.theme.BaIcons
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.polygonPerimeter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportTab(vm: EditorViewModel) {
    val context = LocalContext.current
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadMasterLogo(context, uri)
    }
    val l = vm.layout
    val label = patternLabel(vm.pattern.type, vm.pattern.rotationDeg)
    val name = vm.projectName.ifBlank { stringResource(R.string.default_name) }
    val m2 = stringResource(R.string.unit_m2)
    val cur = vm.prices.currency
    val costs = vm.surfaceCosts()
    val estMat = costs.sumOf { it.materials }
    val estWork = costs.sumOf { it.work }
    val matsLabel = stringResource(R.string.materials_cost)
    val workLabel = stringResource(R.string.work_cost)
    val estRows = buildList {
        costs.filter { it.materials + it.work > 0 }.forEach {
            add((surfaceTitle(it.id) + " · " + finishTitle(it.finish)) to money(it.materials + it.work, cur))
        }
        // разбивка нужна, только если есть обе части — иначе она повторяет итог
        if (estMat > 0 && estWork > 0) {
            add(matsLabel to money(estMat, cur))
            add(workLabel to money(estWork, cur))
        }
    }
    val estTotal = if (estMat + estWork > 0) money(estMat + estWork, cur) else null
    val pcsLabel = stringResource(R.string.pcs)
    val aptStats = if (vm.rooms.size > 1) vm.apartmentStats() else emptyList()
    fun aptValue(buy: Int, moneySum: Double): String =
        buy.toString() + " " + pcsLabel + if (moneySum > 0) " ≈ " + money(moneySum, cur) else ""
    val aptRows = aptStats.map { st ->
        (st.name + " · " + String.format(Locale.getDefault(), "%.2f", st.areaM2) + " " + m2 +
            " · " + st.tileLabel) to aptValue(st.buy, st.money)
    }
    val aptTotal = if (aptStats.isEmpty()) {
        null
    } else {
        aptValue(aptStats.sumOf { it.buy }, aptStats.sumOf { it.money })
    }
    val tileAreaM2r = vm.tile.widthMm * vm.tile.heightMm / 1_000_000.0
    val tileUnitCost = if (vm.prices.tilePc > 0) vm.prices.tilePc else vm.prices.tileM2 * tileAreaM2r
    val heroCost = vm.buyCount * tileUnitCost
    val tileAreaCmR = vm.tile.widthMm * vm.tile.heightMm / 100.0
    val offcutM2r = vm.layout.cutPieces.sumOf { pcs ->
        pcs.count * (tileAreaCmR - pcs.aCm * pcs.bCm).coerceAtLeast(0.0)
    } / 10000.0
    val offcutCostR = offcutM2r * (if (vm.prices.tilePc > 0) vm.prices.tilePc / tileAreaM2r else vm.prices.tileM2)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        // Главное: проект и сколько покупать
        Card {
            if (Entitlements.watermark) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Panel2)
                        .border(1.dp, LineC, RoundedCornerShape(11.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.wm_slot), color = Sub, fontSize = 11.sp)
                }
            }
            Text(stringResource(R.string.app_name), color = Acc2, fontSize = 11.sp)
            Text(name, color = Txt, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                DateFormat.getDateInstance(DateFormat.LONG, UiPrefs.locale(context)).format(Date()),
                color = Sub,
                fontSize = 10.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(Acc.copy(alpha = 0.30f), Acc.copy(alpha = 0.10f))),
                    )
                    .border(1.dp, Acc, RoundedCornerShape(12.dp))
                    .padding(13.dp),
            ) {
                Text(
                    stringResource(R.string.buy),
                    color = Acc2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${vm.buyCount} " + stringResource(R.string.pcs) + " ≈ " +
                        String.format(Locale.getDefault(), "%.2f", vm.buyM2) + " " + m2,
                    color = Txt,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (heroCost > 0) {
                    Text(
                        "≈ " + money(heroCost, cur),
                        color = Acc2,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    stringResource(R.string.reserve) + " ${vm.reservePct}%",
                    color = Sub,
                    fontSize = 10.5.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Действие — сразу под главным числом
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Acc, AccDeep)))
                .clickable {
                    PdfReport.share(
                        context = context,
                        name = name,
                        room = vm.room,
                        tile = vm.tile,
                        pattern = vm.pattern,
                        layout = l,
                        reservePct = vm.reservePct,
                        buyCount = vm.buyCount,
                        thresholdPieces = vm.thresholdPieces,
                        pairSaved = if (vm.pairCuts) vm.cutPairs.saved else 0,
                        buyM2 = vm.buyM2,
                        patternLabel = label,
                        watermark = Entitlements.watermark,
                        logo = if (Entitlements.brandedPdf) vm.masterLogo?.asAndroidBitmap() else null,
                        estimate = estRows,
                        estimateTotal = estTotal,
                        apartment = aptRows,
                        apartmentTotal = aptTotal,
                        colorArgb = vm.tileColor.toArgb(),
                        variation = vm.variation,
                        decorIdx = vm.decorIdx,
                        tileBmp = vm.tileImage?.asAndroidBitmap(),
                        decorBmp = (vm.decorImage ?: vm.tileImage)?.asAndroidBitmap(),
                        panel = vm.panelInfo(),
                        extra = vm.zoneLayers(),
                        colorOf = { t -> vm.colorOfTile(t) },
                        cutNumbers = vm.showCuts,
                    )
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BaIcons.Share, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.share_pdf),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Вся квартира: строка на комнату — видно, где сколько
        if (aptRows.isNotEmpty()) {
            Card {
                Text(
                    stringResource(R.string.apt_total),
                    color = Acc2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                aptRows.forEach { (label, value) -> Row2(label, value) }
                aptTotal?.let { Row2(stringResource(R.string.grand_total), it, Acc2) }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Кратко о проекте — одна карточка вместо трёх
        Card {
            Text(
                stringResource(R.string.summary),
                color = Acc2,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row2(
                stringResource(R.string.room_label),
                String.format(Locale.getDefault(), "%.2f", l.areaM2) + " " + m2 + " · " +
                    String.format(Locale.getDefault(), "%.2f", polygonPerimeter(vm.room.points)) + " " +
                    stringResource(R.string.unit_m),
            )
            Row2(
                stringResource(R.string.tile_label),
                "${vm.tile.widthMm.toInt()}×${vm.tile.heightMm.toInt()} " + stringResource(R.string.unit_mm) +
                    " · " + stringResource(R.string.grout) + " ${vm.tile.groutMm.toInt()}",
            )
            Row2(stringResource(R.string.layout_label), label)
            Row2(
                stringResource(R.string.full_tiles) + " / " + stringResource(R.string.cut_tiles),
                "${l.fullCount} / ${l.cutCount}",
            )
            if (offcutM2r > 0.005) {
                Row2(
                    stringResource(R.string.offcut_area),
                    String.format(Locale.getDefault(), "%.2f", offcutM2r) + " " + m2 +
                        (if (offcutCostR > 0) " ≈ " + money(offcutCostR, cur) else ""),
                    Warn,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Смета — только когда заданы цены
        if (estTotal != null) {
            Card {
                Text(
                    stringResource(R.string.sec_estimate),
                    color = Acc2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                if (estMat > 0 && estWork > 0) {
                    Row2(matsLabel, money(estMat, cur))
                    Row2(workLabel, money(estWork, cur))
                }
                Row2(stringResource(R.string.grand_total), estTotal, Acc2)
            }
            Spacer(Modifier.height(10.dp))
        }

        // Логотип мастера
        Card {
            Text(
                stringResource(R.string.master_logo),
                color = Acc2,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.logo_note), color = Sub, fontSize = 10.5.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val logo = vm.masterLogo
                if (logo != null) {
                    Image(
                        bitmap = logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 88.dp, height = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Panel2)
                            .border(1.dp, LineC, RoundedCornerShape(8.dp)),
                    )
                }
                IconChip(BaIcons.Camera, stringResource(R.string.load_logo), logo != null) {
                    logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (logo != null) {
                    Chip(stringResource(R.string.clear)) { vm.clearMasterLogo() }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Подрезка: только крупнейшие размеры, полный список — в PDF
        Card {
            Text(
                stringResource(R.string.cut_map),
                color = Acc2,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (l.cutPieces.isEmpty()) {
                Text(stringResource(R.string.no_cuts), color = Sub, fontSize = 12.sp)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    l.cutPieces.take(4).forEach { p ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Panel2)
                                .border(1.dp, LineC, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                        ) {
                            Text(
                                String.format(Locale.getDefault(), "%.1f", p.aCm) + "×" +
                                    String.format(Locale.getDefault(), "%.1f", p.bCm) + " " +
                                    stringResource(R.string.unit_cm) + " · " + p.count,
                                color = Sub,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                val rest = l.cutPieces.size - 4
                if (rest > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.more_sizes, rest), color = Sub, fontSize = 10.5.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.disclaimer), color = Sub, fontSize = 10.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.credit),
            color = Sub,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Panel)
            .border(1.dp, LineC, RoundedCornerShape(13.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun Row2(label: String, value: String, valueColor: Color = Txt) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Sub, fontSize = 12.5.sp)
        Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
