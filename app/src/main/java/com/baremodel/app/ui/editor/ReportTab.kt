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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.report.PdfReport
import com.baremodel.app.ui.theme.Acc
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
    val l = vm.layout
    val label = patternLabel(vm.pattern.type, vm.pattern.rotationDeg)
    val name = vm.projectName.ifBlank { stringResource(R.string.default_name) }
    val m2 = stringResource(R.string.unit_m2)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Card {
            Text(stringResource(R.string.app_name), color = Acc2, fontSize = 11.sp)
            Text(name, color = Txt, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.date) + ": " +
                    DateFormat.getDateInstance(DateFormat.LONG).format(Date()),
                color = Sub,
                fontSize = 10.5.sp,
            )
        }
        Spacer(Modifier.height(10.dp))

        Card {
            Text(stringResource(R.string.params), color = Acc2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
        }
        Spacer(Modifier.height(10.dp))

        Card {
            Text(stringResource(R.string.results), color = Acc2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row2(stringResource(R.string.full_tiles), l.fullCount.toString())
            Row2(stringResource(R.string.cut_tiles), l.cutCount.toString(), Warn)
            Row2(stringResource(R.string.total_tiles), l.totalCount.toString())
            Row2(stringResource(R.string.reserve), "${vm.reservePct}%")
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Acc.copy(alpha = 0.12f))
                    .border(1.dp, Acc, RoundedCornerShape(11.dp))
                    .padding(12.dp),
            ) {
                Text(stringResource(R.string.buy), color = Acc2, fontSize = 11.sp)
                Text(
                    "${vm.buyCount} " + stringResource(R.string.pcs) + " ≈ " +
                        String.format(Locale.getDefault(), "%.2f", vm.buyM2) + " " + m2,
                    color = Txt,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Card {
            Text(stringResource(R.string.cut_map), color = Acc2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (l.cutPieces.isEmpty()) {
                Text(stringResource(R.string.no_cuts), color = Sub, fontSize = 12.sp)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    l.cutPieces.take(30).forEach { p ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Panel2)
                                .border(1.dp, LineC, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
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
            }
        }
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Acc)
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
                        buyM2 = vm.buyM2,
                        patternLabel = label,
                    )
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "⬇ " + stringResource(R.string.share_pdf),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.disclaimer),
            color = Sub,
            fontSize = 10.5.sp,
        )
        Spacer(Modifier.height(8.dp))
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
