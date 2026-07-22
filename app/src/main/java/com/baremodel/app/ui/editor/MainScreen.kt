package com.baremodel.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.Bg
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.app.ui.theme.Warn
import java.util.Locale

@Composable
fun MainScreen(vm: EditorViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.refreshProjects() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding(),
    ) {
        TopBar(tab) { tab = it }
        Box(Modifier.weight(1f)) {
            if (tab == 0) EditorTab(vm) else ReportTab(vm)
        }
    }
}

@Composable
private fun TopBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Acc, Color(0xFF2A62C8)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("BA", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), color = Txt, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text("β", color = Acc2, fontSize = 11.sp)
            }
            Text(stringResource(R.string.tagline), color = Sub, fontSize = 10.5.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.tab_editor), tab == 0) { onTab(0) }
            Chip(stringResource(R.string.tab_report), tab == 1) { onTab(1) }
        }
    }
}

@Composable
private fun EditorTab(vm: EditorViewModel) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            EditorCanvas(vm, Modifier.fillMaxSize())

            // режимы
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(stringResource(R.string.mode_pattern), !vm.roomMode) { vm.switchRoomMode(false) }
                Chip(stringResource(R.string.mode_room), vm.roomMode) { vm.switchRoomMode(true) }
            }

            // слои
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip("📐", vm.showDims) { vm.toggleDims() }
                Chip("✂", vm.showCuts) { vm.toggleCuts() }
            }

            // вписать
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel.copy(alpha = 0.92f))
                    .border(1.dp, LineC, RoundedCornerShape(12.dp))
                    .clickable { vm.fit() },
                contentAlignment = Alignment.Center,
            ) {
                Text("⤢", color = Acc2, fontSize = 17.sp)
            }

            if (vm.layout.overLimit) {
                Text(
                    stringResource(R.string.too_many),
                    color = Warn,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Panel2.copy(alpha = 0.95f))
                        .border(1.dp, Warn, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            } else if (vm.hintVisible) {
                Text(
                    stringResource(if (vm.roomMode) R.string.hint_room else R.string.hint_pattern),
                    color = Sub,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        StatsRow(vm)
        PanelHost(vm)
    }
}

@Composable
private fun StatsRow(vm: EditorViewModel) {
    val l = vm.layout
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel2)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Stat(
            stringResource(R.string.area),
            String.format(Locale.getDefault(), "%.2f", l.areaM2) + " " + stringResource(R.string.unit_m2),
        )
        Stat(stringResource(R.string.full_tiles), l.fullCount.toString())
        Stat(stringResource(R.string.cut_tiles), l.cutCount.toString(), Warn)
        Stat(
            stringResource(R.string.buy),
            "${vm.buyCount} " + stringResource(R.string.pcs),
            Txt,
            "+${vm.reservePct}%",
        )
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color = Txt, extra: String? = null) {
    Column {
        Text(label, color = Sub, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (extra != null) {
                Spacer(Modifier.width(4.dp))
                Text(extra, color = Acc2, fontSize = 10.sp)
            }
        }
    }
}
