package com.baremodel.app.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.AccDeep
import com.baremodel.app.ui.theme.AccSoft
import com.baremodel.app.ui.theme.BaIcons
import com.baremodel.app.ui.theme.Bg
import com.baremodel.app.ui.theme.Dim
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Panel3
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
        TopBar(vm, tab)
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = tab, label = "tab") { t ->
                when (t) {
                    0 -> EditorTab(vm)
                    1 -> ReportTab(vm)
                    else -> ProScreen()
                }
            }
        }
        BottomNav(tab) { tab = it }
    }
}

@Composable
private fun TopBar(vm: EditorViewModel, tab: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(37.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Acc2, AccDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Text("BA", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), color = Txt, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    "BETA",
                    color = Acc,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccSoft)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Text(stringResource(R.string.tagline), color = Dim, fontSize = 10.5.sp, maxLines = 1)
        }
        if (tab == 0) {
            IconToggle(BaIcons.Ruler, vm.showDims) { vm.toggleDims() }
            Spacer(Modifier.width(8.dp))
            IconToggle(BaIcons.Scissors, vm.showCuts) { vm.toggleCuts() }
        }
    }
}

@Composable
private fun IconToggle(icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(37.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) AccSoft else Panel2)
            .border(1.dp, if (on) Acc.copy(alpha = 0.45f) else LineC, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (on) Acc else Sub)
    }
}

@Composable
private fun EditorTab(vm: EditorViewModel) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            EditorCanvas(vm, Modifier.fillMaxSize())

            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(13.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel2.copy(alpha = 0.88f))
                    .border(1.dp, LineC, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SegItem(BaIcons.Layers, stringResource(R.string.mode_pattern), !vm.roomMode) { vm.switchRoomMode(false) }
                SegItem(BaIcons.Room, stringResource(R.string.mode_room), vm.roomMode) { vm.switchRoomMode(true) }
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(15.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Acc, AccDeep)))
                    .clickable { vm.fit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(BaIcons.Fit, null, Modifier.size(21.dp), tint = Color.White)
            }

            Fade(
                visible = vm.layout.overLimit,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 16.dp, end = 16.dp),
            ) {
                Text(
                    stringResource(R.string.too_many),
                    color = Warn,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel2.copy(alpha = 0.95f))
                        .border(1.dp, Warn.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                )
            }

            Fade(
                visible = vm.hintVisible && !vm.layout.overLimit,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 16.dp, end = 80.dp),
            ) {
                Text(
                    stringResource(if (vm.roomMode) R.string.hint_room else R.string.hint_pattern),
                    color = Sub,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel2.copy(alpha = 0.88f))
                        .border(1.dp, LineC, RoundedCornerShape(20.dp))
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Panel),
        ) {
            Box(
                Modifier
                    .padding(top = 9.dp, bottom = 3.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Panel3),
            )
            StatsRow(vm)
            PanelHost(vm)
        }
    }
}

/**
 * Обёртка над AnimatedVisibility: вызывается вне ColumnScope/RowScope,
 * поэтому выбирается обычная перегрузка, а не scope-расширение.
 */
@Composable
private fun Fade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        content()
    }
}

@Composable
private fun SegItem(icon: ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) Brush.linearGradient(listOf(Acc, AccDeep)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (on) Color.White else Sub)
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (on) Color.White else Sub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatsRow(vm: EditorViewModel) {
    val l = vm.layout
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Stat(
            stringResource(R.string.area),
            String.format(Locale.getDefault(), "%.2f", l.areaM2),
            stringResource(R.string.unit_m2),
        )
        Stat(stringResource(R.string.full_tiles), l.fullCount.toString())
        Stat(stringResource(R.string.cut_tiles), l.cutCount.toString(), valueColor = Warn)
        Stat(
            stringResource(R.string.buy),
            vm.buyCount.toString(),
            stringResource(R.string.pcs),
            accent = true,
            extra = "+${vm.reservePct}%",
        )
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = Txt,
    accent: Boolean = false,
    extra: String? = null,
) {
    Column(
        Modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (accent) AccSoft else Panel2)
            .border(1.dp, if (accent) Acc.copy(alpha = 0.4f) else LineC, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(Locale.getDefault()), color = Dim, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = if (accent) Acc2 else valueColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = Dim, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (extra != null) {
                Spacer(Modifier.width(5.dp))
                Text(extra, color = Acc, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun BottomNav(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Acc,
            selectedTextColor = Txt,
            indicatorColor = AccSoft,
            unselectedIconColor = Dim,
            unselectedTextColor = Dim,
        )
        NavigationBarItem(
            selected = tab == 0,
            onClick = { onTab(0) },
            icon = { Icon(BaIcons.Tile, null, Modifier.size(21.dp)) },
            label = { Text(stringResource(R.string.tab_editor), fontSize = 10.5.sp) },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == 1,
            onClick = { onTab(1) },
            icon = { Icon(BaIcons.Doc, null, Modifier.size(21.dp)) },
            label = { Text(stringResource(R.string.tab_report), fontSize = 10.5.sp) },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == 2,
            onClick = { onTab(2) },
            icon = { Icon(BaIcons.Star, null, Modifier.size(21.dp)) },
            label = { Text(stringResource(R.string.tab_pro), fontSize = 10.5.sp) },
            colors = colors,
        )
    }
}
