package com.baremodel.app.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.baremodel.app.ui.theme.Line2
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Panel3
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.CutPieceInfo
import com.baremodel.core.DecorMode
import com.baremodel.core.Pt
import com.baremodel.core.TileClass
import java.util.Locale
import kotlin.math.roundToInt

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
                    1 -> View3DScreen(vm)
                    2 -> PhotoFitScreen(vm)
                    3 -> ReportTab(vm)
                    else -> ProScreen()
                }
            }
            // авторский знак поверх всей работы: бледно, но на каждом экране
            if (Entitlements.watermark && tab != 4) {
                WatermarkOverlay()
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
            .background(Brush.verticalGradient(listOf(Panel3.copy(alpha = 0.9f), Panel)))
            .padding(horizontal = 12.dp, vertical = 11.dp),
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
                Text(
                    stringResource(R.string.app_name),
                    color = Txt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "BETA",
                    color = Acc,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
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
            Spacer(Modifier.width(8.dp))
            IconToggle(BaIcons.Furn, vm.showFurniture) { vm.toggleFurniture() }
            if (vm.planImage != null) {
                Spacer(Modifier.width(8.dp))
                IconToggle(BaIcons.Camera, vm.showPlanImage) { vm.togglePlanImage() }
            }
            Spacer(Modifier.width(8.dp))
            IconToggle(BaIcons.Layers, !vm.statsCollapsed) { vm.toggleStatsCollapsed() }
        }
    }
}

@Composable
private fun IconToggle(icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (on) AccSoft else Panel2)
            .border(1.dp, if (on) Acc.copy(alpha = 0.45f) else LineC, RoundedCornerShape(13.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) Acc else Sub)
    }
}

@Composable
private fun EditorTab(vm: EditorViewModel) {
    // Планшет: план слева, настройки справа — панель ничего не перекрывает.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    EditorStage(vm)
                }
                Column(
                    Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .background(Panel)
                        .padding(top = 8.dp),
                ) {
                    StatsRow(vm)
                    Box(Modifier.weight(1f)) {
                        PanelHost(vm, maxContentHeight = 2000.dp)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    EditorStage(vm)
                }


        // 0 — скрыта · 1 — половина (видно план и настройки) · 2 — полностью
        var sheetState by rememberSaveable { mutableStateOf(1) }
        // шторка: 240 в рабочем положении, 560 развёрнутая — на телефоне панель
        // больше не заставляет скроллить каждую секцию по чуть-чуть; ручка и
        // заголовок живут ВНЕ этого ограничения, поэтому свайп доступен всегда
        val panelMax by animateDpAsState(
            when (sheetState) {
                0 -> 0.dp
                1 -> 240.dp
                else -> 560.dp
            },
            label = "sheetH",
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Panel)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Line2.copy(alpha = 0.8f), Color.Transparent)),
                    RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { sheetState = if (sheetState == 0) 1 else 0 }
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = {
                                if (acc < -30f) sheetState = (sheetState + 1).coerceAtMost(2)
                                if (acc > 30f) sheetState = (sheetState - 1).coerceAtLeast(0)
                            },
                        ) { change, dy ->
                            acc += dy
                            change.consume()
                        }
                    }
                    .padding(top = 9.dp, bottom = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = if (sheetState == 2) 58.dp else 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (sheetState == 0) Acc.copy(alpha = 0.7f) else Panel3),
                )
            }
            Fade(visible = sheetState > 0) {
                Column(Modifier.fillMaxWidth()) {
                    StatsRow(vm)
                    Box(Modifier.heightIn(max = panelMax)) {
                        PanelHost(vm)
                    }
                }
            }
        }
                }
        }
    }
}

@Composable
private fun BoxScope.EditorStage(vm: EditorViewModel) {
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

            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var menuOpen by remember { mutableStateOf(false) }
                var askReset by remember { mutableStateOf(false) }
                var askNew by remember { mutableStateOf(false) }
                Box {
                    RoundBtn(BaIcons.More, true) { menuOpen = true }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(Panel2),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fit), color = Txt, fontSize = 13.sp) },
                            onClick = { menuOpen = false; vm.fit() },
                        )
                        if (vm.room.points.size > 4) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.simplify), color = Txt, fontSize = 13.sp)
                                },
                                onClick = { menuOpen = false; vm.simplifyRoom() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reset_all), color = Warn, fontSize = 13.sp) },
                            onClick = { menuOpen = false; askReset = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_project), color = Txt, fontSize = 13.sp) },
                            onClick = { menuOpen = false; askNew = true },
                        )
                    }
                }
                if (askReset) {
                    AlertDialog(
                        onDismissRequest = { askReset = false },
                        containerColor = Panel2,
                        title = { Text(stringResource(R.string.reset_all), color = Txt) },
                        text = {
                            Text(stringResource(R.string.reset_all_confirm), color = Sub, fontSize = 12.5.sp)
                        },
                        confirmButton = {
                            TextButton(onClick = { askReset = false; vm.resetPlacements() }) {
                                Text(stringResource(R.string.apply), color = Acc2)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { askReset = false }) {
                                Text(stringResource(R.string.cancel), color = Sub)
                            }
                        },
                    )
                }
                if (askNew) {
                    AlertDialog(
                        onDismissRequest = { askNew = false },
                        containerColor = Panel2,
                        title = { Text(stringResource(R.string.new_project), color = Txt) },
                        text = {
                            Text(stringResource(R.string.new_confirm), color = Sub, fontSize = 12.5.sp)
                        },
                        confirmButton = {
                            TextButton(onClick = { askNew = false; vm.newProject() }) {
                                Text(stringResource(R.string.apply), color = Acc2)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { askNew = false }) {
                                Text(stringResource(R.string.cancel), color = Sub)
                            }
                        },
                    )
                }
                RoundBtn(BaIcons.Undo, vm.canUndo) { vm.undo() }
                RoundBtn(BaIcons.Redo, vm.canRedo) { vm.redo() }
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(15.dp)
                    .size(52.dp)
                    .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = AccDeep, ambientColor = AccDeep)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Acc, AccDeep)))
                    .clickable { vm.fit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(BaIcons.Fit, null, Modifier.size(21.dp), tint = Color.White)
            }

            // всё, что сейчас включено — видно и выключается отсюда, без захода в секции
            val active = buildList<Pair<String, () -> Unit>> {
                if (vm.paintMode) add(stringResource(R.string.paint) to { vm.togglePaintMode() })
                if (vm.formatBrush) {
                    add(stringResource(R.string.brush_size) to { vm.toggleFormatBrush() })
                }
                if (vm.tileColors.isNotEmpty()) {
                    add(stringResource(R.string.paint_clear) to { vm.clearTileColors() })
                }
                if (vm.panelOn) add(stringResource(R.string.panel_off) to { vm.clearPanel() })
                if (vm.showArt) add(stringResource(R.string.show_art) to { vm.toggleArt() })
                if (vm.decor.mode != DecorMode.NONE) {
                    add(stringResource(R.string.sec_decor) to { vm.setDecorMode(DecorMode.NONE) })
                }
                if (vm.activeZone >= 0) {
                    add(stringResource(R.string.zones) to { vm.updateActiveZone(-1) })
                }
                if (vm.traceMode) add(stringResource(R.string.plan_trace) to { vm.toggleTraceMode() })
                if (vm.calibMode) {
                    add(stringResource(R.string.plan_calib) to { vm.updateCalibDialog(false) })
                }
                if (vm.planMove) add(stringResource(R.string.plan_move) to { vm.togglePlanMove() })
                if (vm.planImage != null) {
                    add(stringResource(R.string.plan_clear) to { vm.clearPlanImage() })
                }
                if (vm.ocrNumbers.isNotEmpty()) {
                    add(stringResource(R.string.ocr_read) to { vm.clearOcr() })
                }
                if (vm.highlightCut != null) {
                    add(stringResource(R.string.cuts_short) to { vm.clearHighlightCut() })
                }
            }
            Fade(
                visible = active.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 13.dp, bottom = 13.dp, end = 74.dp),
            ) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(13.dp))
                        .background(Panel2.copy(alpha = 0.92f))
                        .border(1.dp, LineC, RoundedCornerShape(13.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    active.forEach { (label, off) ->
                        Chip("$label ✕", selected = true) { off() }
                    }
                }
            }

            Fade(
                visible = vm.drawMode,
                // ниже верхнего ряда (переключатель режима, меню, отмена/повтор),
                // чтобы кнопки не перекрывали друг друга
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Panel2.copy(alpha = 0.94f))
                            .border(1.dp, LineC, RoundedCornerShape(14.dp))
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Chip(stringResource(R.string.draw_close), selected = true) { vm.finishDraw() }
                        IconChip(BaIcons.Undo, stringResource(R.string.draw_undo)) { vm.undoDrawPoint() }
                        IconChip(BaIcons.Close, stringResource(R.string.cancel), warn = true) {
                            vm.cancelDraw()
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.draw_hint),
                        color = Sub,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Panel2.copy(alpha = 0.8f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }

            // режим «Комната»: проёмы ставятся в два касания — тип, затем стена
            Fade(
                visible = vm.roomMode && !vm.drawMode && vm.selection == null &&
                    vm.openingSel == null,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier
                            .fillMaxWidth(0.96f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            OPENING_DOOR to R.string.opening_door,
                            OPENING_WINDOW to R.string.opening_window,
                            OPENING_BALCONY to R.string.opening_balcony,
                            OPENING_ENTRY to R.string.opening_entry,
                            OPENING_PASSAGE to R.string.opening_passage,
                        ).forEach { (kind, res) ->
                            Chip(
                                stringResource(res),
                                selected = vm.placeOpeningKind == kind,
                            ) { vm.armPlaceOpening(kind) }
                        }
                    }
                    if (vm.placeOpeningKind >= 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.place_opening_hint),
                            color = Acc2,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Panel2.copy(alpha = 0.92f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // выбранный тапом проём: изменить размер или удалить
            vm.openingSel?.let { selO ->
                val wId = "wall-" + (selO.first + 1)
                val oSel = vm.openingsOf(wId).getOrNull(selO.second)
                val kSel = vm.openingKindsOf(wId).getOrNull(selO.second)
                    ?: if ((oSel?.y ?: 1.0) < 0.05) OPENING_DOOR else OPENING_WINDOW
                Fade(
                    visible = oSel != null,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp),
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel2.copy(alpha = 0.95f))
                            .border(
                                1.dp,
                                openingTone(kSel).copy(alpha = 0.6f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(
                                when (kSel) {
                                    OPENING_WINDOW -> R.string.kind_window
                                    OPENING_BALCONY -> R.string.kind_balcony
                                    OPENING_ENTRY -> R.string.kind_entry
                                    OPENING_PASSAGE -> R.string.kind_passage
                                    else -> R.string.kind_door
                                },
                            ) + " · " + stringResource(R.string.surf_wall) + " " + (selO.first + 1),
                            color = openingTone(kSel),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Chip(stringResource(R.string.op_edit)) { vm.editSelectedOpening() }
                        Chip(stringResource(R.string.op_delete)) { vm.deleteSelectedOpening() }
                        Chip("✕") { vm.clearOpeningSel() }
                    }
                }
            }

            val sel = vm.selection
            Fade(
                visible = sel != null,
                // ниже верхнего ряда: кнопка отмены и переключатель узор/комната
                // остаются видимыми и нажимаемыми при любом выборе
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth(0.96f)
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel2.copy(alpha = 0.92f))
                        .border(1.dp, LineC, RoundedCornerShape(14.dp))
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    when (sel) {
                        is Selection.Furn -> {
                            Chip("90°") { vm.rotateSelectedFurn() }
                            IconChip(BaIcons.Close, stringResource(R.string.del_furn), warn = true) {
                                vm.deleteSelectedFurniture()
                            }
                        }
                        is Selection.Vertex -> {
                            Chip(stringResource(R.string.edge_lens)) { vm.updateEdgeDialog(true) }
                            Chip(stringResource(R.string.fillet)) { vm.updateFilletDialog(true) }
                            Chip(stringResource(R.string.niche)) { vm.addNicheAfterSelected() }
                            IconChip(
                                BaIcons.Close, stringResource(R.string.del_point), warn = true,
                            ) { vm.deleteSelectedVertex() }
                        }
                        is Selection.Zone -> {
                            val z = vm.zones.getOrNull(sel.i)
                            if (z != null) {
                                Text(
                                    stringResource(R.string.zone_n, sel.i + 1) + " · " +
                                        z.tile.widthMm.toInt() + "×" + z.tile.heightMm.toInt(),
                                    color = Acc2,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(horizontal = 7.dp),
                                )
                                IconChip(
                                    BaIcons.Close, stringResource(R.string.del_zone), warn = true,
                                ) { vm.deleteActiveZone() }
                            }
                        }

                        is Selection.Cut -> IconChip(
                            BaIcons.Close, stringResource(R.string.del_cutout), warn = true,
                        ) { vm.deleteSelectedCutout() }

                        is Selection.Tile -> {
                            val t = vm.layout.tiles.getOrNull(sel.i)
                            if (t != null) {
                                // тот же номер, что на плане и в 3D: единая нумерация из ядра
                                val info =
                                    "${vm.tile.widthMm.toInt()}×${vm.tile.heightMm.toInt()}" +
                                        if (t.cls == TileClass.CUT) {
                                            " · " + stringResource(R.string.cut_tiles) +
                                                cutChipSuffix(vm.cutInfo[sel.i])
                                        } else {
                                            ""
                                        }
                                Text(
                                    info,
                                    color = if (t.cls == TileClass.CUT) Warn else Sub,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(horizontal = 7.dp),
                                )
                                Chip(
                                    stringResource(R.string.sec_decor),
                                    sel.i in vm.decorIdx,
                                ) { vm.toggleTileDecor() }
                                Chip(
                                    stringResource(R.string.panel_here),
                                    vm.panelCell(t) == (0 to 0),
                                ) { vm.placePanelAtSelected() }
                                if (vm.panelOn) {
                                    Chip(stringResource(R.string.panel_off), warn = true) {
                                        vm.clearPanel()
                                    }
                                }
                            }
                        }
                        is Selection.Stair -> {
                            Chip("90°") { vm.rotateStairs() }
                            IconChip(BaIcons.Close, stringResource(R.string.stairs_del), warn = true) {
                                vm.deleteSelectedStairs()
                            }
                        }
                        null -> Unit
                    }
                }
            }

            val selV = vm.selection as? Selection.Vertex
            vm.openingWizard?.let { wz ->
                val wallLen = vm.model.walls.getOrNull(wz.wall)?.lengthM ?: 4.0
                val mU = stringResource(R.string.unit_m)
                if (wz.kind < 0) {
                    // шаг 1: программа спрашивает, что здесь
                    AlertDialog(
                        onDismissRequest = { vm.closeOpeningWizard() },
                        containerColor = Panel2,
                        title = { Text(stringResource(R.string.wiz_what), color = Txt) },
                        text = {
                            Column {
                                Text(
                                    stringResource(R.string.surf_wall) + " " + (wz.wall + 1) +
                                        " · " + String.format(Locale.getDefault(), "%.2f", wallLen) +
                                        " " + mU,
                                    color = Sub,
                                    fontSize = 11.sp,
                                )
                                Spacer(Modifier.height(9.dp))
                                listOf(
                                    OPENING_DOOR to R.string.kind_door,
                                    OPENING_WINDOW to R.string.kind_window,
                                    OPENING_BALCONY to R.string.kind_balcony,
                                    OPENING_ENTRY to R.string.kind_entry,
                                    OPENING_PASSAGE to R.string.kind_passage,
                                ).forEach { (k, res) ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(openingTone(k).copy(alpha = 0.16f))
                                            .border(
                                                1.dp,
                                                openingTone(k).copy(alpha = 0.7f),
                                                RoundedCornerShape(10.dp),
                                            )
                                            .clickable { vm.wizardPickKind(k) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            stringResource(res),
                                            color = Txt,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { vm.closeOpeningWizard() }) {
                                Text(stringResource(R.string.cancel), color = Sub)
                            }
                        },
                    )
                } else {
                    // шаг 2: размер — пресеты как в жизни + свои цифры
                    val existingO = if (wz.editIndex >= 0) {
                        vm.openingsOf("wall-" + (wz.wall + 1)).getOrNull(wz.editIndex)
                    } else {
                        null
                    }
                    val defs = existingO?.let { Triple(it.w, it.h, it.y) }
                        ?: defaultOpeningSize(wz.kind)
                    var wIn by remember(wz) {
                        mutableStateOf(String.format(java.util.Locale.US, "%.2f", defs.first))
                    }
                    var hIn by remember(wz) {
                        mutableStateOf(String.format(java.util.Locale.US, "%.2f", defs.second))
                    }
                    var sillIn by remember(wz) {
                        mutableStateOf(String.format(java.util.Locale.US, "%.2f", defs.third))
                    }
                    fun applyPreset(t: Triple<Double, Double, Double>) {
                        val wv = if (t.first < 0) wallLen else t.first
                        wIn = String.format(java.util.Locale.US, "%.2f", wv)
                        hIn = String.format(java.util.Locale.US, "%.2f", t.second)
                        sillIn = String.format(java.util.Locale.US, "%.2f", t.third)
                    }
                    val presets = when (wz.kind) {
                        OPENING_DOOR -> listOf(
                            "0.7" to Triple(0.7, 2.05, 0.0),
                            "0.8" to Triple(0.8, 2.05, 0.0),
                            "0.9" to Triple(0.9, 2.05, 0.0),
                            "1.0" to Triple(1.0, 2.05, 0.0),
                        )
                        OPENING_ENTRY -> listOf(
                            "0.9" to Triple(0.9, 2.05, 0.0),
                            "1.0" to Triple(1.0, 2.05, 0.0),
                            "1.2" to Triple(1.2, 2.05, 0.0),
                        )
                        OPENING_BALCONY -> listOf(
                            "0.8" to Triple(0.8, 2.1, 0.0),
                            "1.8" to Triple(1.8, 2.1, 0.0),
                            stringResource(R.string.op_full_wall) to Triple(-1.0, 2.1, 0.0),
                        )
                        OPENING_PASSAGE -> listOf(
                            "0.9" to Triple(0.9, 2.1, 0.0),
                            "1.2" to Triple(1.2, 2.1, 0.0),
                            "1.5" to Triple(1.5, 2.1, 0.0),
                        )
                        else -> listOf(
                            "0.9×1.2" to Triple(0.9, 1.2, 0.9),
                            "1.4×1.4" to Triple(1.4, 1.4, 0.9),
                            "1.8×1.4" to Triple(1.8, 1.4, 0.9),
                            stringResource(R.string.op_full_height) to Triple(0.9, 2.2, 0.0),
                            stringResource(R.string.op_full_wall) to Triple(-1.0, 2.2, 0.0),
                        )
                    }
                    val kindName = stringResource(
                        when (wz.kind) {
                            OPENING_WINDOW -> R.string.kind_window
                            OPENING_BALCONY -> R.string.kind_balcony
                            OPENING_ENTRY -> R.string.kind_entry
                            OPENING_PASSAGE -> R.string.kind_passage
                            else -> R.string.kind_door
                        },
                    )
                    AlertDialog(
                        onDismissRequest = { vm.closeOpeningWizard() },
                        containerColor = Panel2,
                        title = {
                            Text(kindName + " — " + stringResource(R.string.wiz_size), color = Txt)
                        },
                        text = {
                            Column {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    presets.forEach { (lbl, t) ->
                                        Chip(lbl) { applyPreset(t) }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = wIn,
                                        onValueChange = { wIn = it },
                                        label = { Text(stringResource(R.string.width)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = hIn,
                                        onValueChange = { hIn = it },
                                        label = { Text(stringResource(R.string.height)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (wz.kind == OPENING_WINDOW) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = sillIn,
                                        onValueChange = { sillIn = it },
                                        label = { Text(stringResource(R.string.opening_sill)) },
                                        singleLine = true,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val wv = wIn.replace(',', '.').toDoubleOrNull()
                                val hv = hIn.replace(',', '.').toDoubleOrNull()
                                val sv = sillIn.replace(',', '.').toDoubleOrNull() ?: 0.0
                                if (wv != null && hv != null) {
                                    vm.confirmOpeningWizard(wv, hv, sv)
                                }
                            }) { Text(stringResource(R.string.wiz_place), color = Acc2) }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.closeOpeningWizard() }) {
                                Text(stringResource(R.string.cancel), color = Sub)
                            }
                        },
                    )
                }
            }

            if (vm.edgeEditIndex >= 0 && vm.edgeEditIndex < vm.room.points.size) {
                val ei = vm.edgeEditIndex
                val ea = vm.room.points[ei]
                val eb = vm.room.points[(ei + 1) % vm.room.points.size]
                val curLen = kotlin.math.hypot(eb.x - ea.x, eb.y - ea.y)
                var lenIn2 by remember(ei) {
                    mutableStateOf(String.format(java.util.Locale.US, "%.2f", curLen))
                }
                var thickIn2 by remember(ei) {
                    mutableStateOf(
                        String.format(
                            java.util.Locale.US, "%.0f",
                            vm.wallThicknessOf("wall-" + (ei + 1)) * 100,
                        ),
                    )
                }
                var moveEnd2 by remember(ei) { mutableStateOf(true) }
                AlertDialog(
                    onDismissRequest = { vm.closeEdgeEdit() },
                    containerColor = Panel2,
                    title = { Text(stringResource(R.string.edge_len_title, ei + 1), color = Txt) },
                    text = {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = lenIn2,
                                    onValueChange = { lenIn2 = it },
                                    label = { Text(stringResource(R.string.len_m)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = thickIn2,
                                    onValueChange = { thickIn2 = it },
                                    label = {
                                        Text(
                                            stringResource(R.string.thick_lbl) + ", " +
                                                stringResource(R.string.unit_cm),
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.move_which), color = Dim, fontSize = 10.sp)
                            Spacer(Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Chip("A", selected = !moveEnd2) { moveEnd2 = false }
                                Chip("B", selected = moveEnd2) { moveEnd2 = true }
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                stringResource(R.string.edge_len_hint),
                                color = Sub,
                                fontSize = 10.5.sp,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            lenIn2.replace(',', '.').toDoubleOrNull()?.let {
                                vm.setEdgeLength(ei, it, moveEnd2)
                            }
                            thickIn2.replace(',', '.').toDoubleOrNull()?.let {
                                vm.updateWallThicknessOf("wall-" + (ei + 1), it / 100.0)
                            }
                            vm.closeEdgeEdit()
                        }) { Text(stringResource(R.string.apply), color = Acc2) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.closeEdgeEdit() }) {
                            Text(stringResource(R.string.cancel), color = Sub)
                        }
                    },
                )
            }

            if (vm.edgeDialog && selV != null) {
                val n = vm.room.points.size
                val cur = vm.room.points[selV.i]
                val pPrev = vm.room.points[(selV.i - 1 + n) % n]
                val pNext = vm.room.points[(selV.i + 1) % n]
                fun len(a: Pt, b: Pt): Double =
                    kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                var prevIn by remember(selV.i) {
                    mutableStateOf(String.format(java.util.Locale.US, "%.2f", len(cur, pPrev)))
                }
                var nextIn by remember(selV.i) {
                    mutableStateOf(String.format(java.util.Locale.US, "%.2f", len(cur, pNext)))
                }
                AlertDialog(
                    onDismissRequest = { vm.updateEdgeDialog(false) },
                    containerColor = Panel2,
                    title = { Text(stringResource(R.string.edge_lens), color = Txt) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = prevIn,
                                onValueChange = { prevIn = it },
                                label = { Text(stringResource(R.string.edge_prev)) },
                                singleLine = true,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nextIn,
                                onValueChange = { nextIn = it },
                                label = { Text(stringResource(R.string.edge_next)) },
                                singleLine = true,
                            )
                            val near = vm.ocrNear(cur)
                            if (near.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.ocr_pick), color = Dim, fontSize = 10.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    near.forEach { v ->
                                        Chip(String.format(Locale.getDefault(), "%.2f", v)) {
                                            nextIn = String.format(Locale.US, "%.2f", v)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val a = prevIn.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val b = nextIn.replace(',', '.').toDoubleOrNull() ?: 0.0
                            vm.applyEdgeLengths(a, b)
                            vm.updateEdgeDialog(false)
                        }) { Text(stringResource(R.string.apply), color = Acc2) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.updateEdgeDialog(false) }) {
                            Text(stringResource(R.string.cancel), color = Sub)
                        }
                    },
                )
            }

            if (vm.calibDialog) {
                var lenIn by remember(vm.calibB) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { vm.updateCalibDialog(false) },
                    containerColor = Panel2,
                    title = { Text(stringResource(R.string.plan_calib), color = Txt) },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.plan_calib_hint),
                                color = Sub,
                                fontSize = 11.5.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = lenIn,
                                onValueChange = { lenIn = it },
                                label = { Text(stringResource(R.string.plan_len)) },
                                singleLine = true,
                            )
                            if (vm.ocrNumbers.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.ocr_pick), color = Dim, fontSize = 10.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    vm.ocrNumbers.take(6).forEach { (v, _, _) ->
                                        Chip(String.format(Locale.getDefault(), "%.2f", v)) {
                                            lenIn = String.format(Locale.US, "%.2f", v)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val v = lenIn.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (v > 0.01) vm.applyCalibration(v) else vm.updateCalibDialog(false)
                        }) { Text(stringResource(R.string.apply), color = Acc2) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.updateCalibDialog(false) }) {
                            Text(stringResource(R.string.cancel), color = Sub)
                        }
                    },
                )
            }

            if (vm.filletDialog && selV != null) {
                var rIn by remember(selV.i) { mutableStateOf("0.50") }
                AlertDialog(
                    onDismissRequest = { vm.updateFilletDialog(false) },
                    containerColor = Panel2,
                    title = { Text(stringResource(R.string.fillet), color = Txt) },
                    text = {
                        OutlinedTextField(
                            value = rIn,
                            onValueChange = { rIn = it },
                            label = { Text(stringResource(R.string.fillet_r)) },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val r = rIn.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (r > 0.04) vm.roundSelectedCorner(r)
                            vm.updateFilletDialog(false)
                        }) { Text(stringResource(R.string.apply), color = Acc2) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.updateFilletDialog(false) }) {
                            Text(stringResource(R.string.cancel), color = Sub)
                        }
                    },
                )
            }

            Fade(
                visible = vm.layout.overLimit,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 118.dp, start = 16.dp, end = 16.dp),
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

            // предупреждение о полоске — на экране, а не в глубине «Советов»:
            // тап по тексту подсвечивает стену, «Исправить» сдвигает узор до полуплитки
            val thinWarn = vm.cutReport.warnings
                .filter { it.code == "THIN_STRIP" }
                .minByOrNull { it.valueCm }
            Fade(
                visible = thinWarn != null && !vm.drawMode && !vm.layout.overLimit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp, start = 16.dp, end = 16.dp),
            ) {
                thinWarn?.let { tw ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel2.copy(alpha = 0.95f))
                            .border(1.dp, Warn.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.warn_thin,
                                String.format(Locale.getDefault(), "%.1f", tw.valueCm),
                                tw.edgeIndex + 1,
                            ),
                            color = Warn,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { vm.toggleWarnEdge(tw.edgeIndex) },
                        )
                        val rotFix = ((vm.pattern.rotationDeg % 90.0) + 90.0) % 90.0
                        if (rotFix < 0.01 || rotFix > 89.99) {
                            Chip(stringResource(R.string.warn_fix)) { vm.fixThinEdge(tw.edgeIndex) }
                        }
                    }
                }
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

/**
 * Обёртка над AnimatedVisibility: вызывается вне ColumnScope/RowScope,
 * поэтому выбирается обычная перегрузка, а не scope-расширение.
 */
/**
 * Диагональная подложка со знаком автора: строки чередуют название программы и имя.
 * Не перехватывает касания (Canvas без обработчиков), убирается донат-кодом.
 */
@Composable
private fun WatermarkOverlay() {
    val app = stringResource(R.string.app_name)
    val author = "Baziulkin Alexander"
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(20, 233, 238, 246)
            textSize = 13f * d
            textAlign = android.graphics.Paint.Align.LEFT
        }
        val stepY = 104f * d
        val stepX = 250f * d
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.rotate(-28f, size.width / 2f, size.height / 2f)
            var row = 0
            var y = -size.height * 0.4f
            while (y < size.height * 1.4f) {
                val text = if (row % 2 == 0) app else author
                var x = -size.width * 0.4f + (row % 2) * stepX * 0.5f
                while (x < size.width * 1.4f) {
                    canvas.nativeCanvas.drawText(text, x, y, paint)
                    x += stepX
                }
                y += stepY
                row++
            }
            canvas.nativeCanvas.restore()
        }
    }
}

@Composable
internal fun Fade(
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
private fun RoundBtn(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.32f, label = "btn")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed && enabled) 0.9f else 1f, label = "btnK")
    Box(
        Modifier
            .graphicsLayer { scaleX = k; scaleY = k }
            .size(44.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(Panel2.copy(alpha = 0.88f))
            .border(1.dp, LineC, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, interactionSource = press, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = Acc2.copy(alpha = alpha))
    }
}

@Composable
private fun SegItem(icon: ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) Brush.linearGradient(listOf(Acc, AccDeep)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (on) Color.White else Sub)
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (on) Color.White else Sub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatsRow(vm: EditorViewModel) {
    val l = vm.layout
    val totals = if (vm.statsApartment && vm.rooms.size > 1) vm.apartmentTotals() else null
    val areaVal = totals?.get(0)?.toDouble() ?: vm.layout.areaM2
    val fullVal = totals?.get(1)?.toInt() ?: vm.layout.fullCount
    val cutVal = totals?.get(2)?.toInt() ?: (vm.layout.cutCount + vm.thresholdPieces)
    val buyVal = totals?.get(3)?.toInt() ?: vm.buyCount
    val full by animateIntAsState(fullVal, label = "full")
    val cut by animateIntAsState(cutVal, label = "cut")
    val buy by animateIntAsState(buyVal, label = "buy")
    val tileAreaM2 = vm.tile.widthMm * vm.tile.heightMm / 1_000_000.0
    val unitPc = if (vm.prices.tilePc > 0) vm.prices.tilePc else vm.prices.tileM2 * tileAreaM2
    val roomName = vm.rooms.getOrNull(vm.activeRoom)?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.room_n, vm.activeRoom + 1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 15.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            if (vm.statsApartment) stringResource(R.string.scope_flat) else roomName,
            color = Acc2,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (vm.rooms.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Chip(stringResource(R.string.scope_room), !vm.statsApartment) {
                    if (vm.statsApartment) vm.toggleStatsScope()
                }
                Chip(stringResource(R.string.scope_flat), vm.statsApartment) {
                    if (!vm.statsApartment) vm.toggleStatsScope()
                }
            }
        }
    }
    if (vm.statsCollapsed) {
        // свёрнуто: одна строка вместо четырёх карточек — план получает пол-экрана
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { vm.toggleStatsCollapsed() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                String.format(Locale.getDefault(), "%.2f", areaVal) + " " +
                    stringResource(R.string.unit_m2),
                color = Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.buy) + " " + buy + " " + stringResource(R.string.pcs),
                color = Acc2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            if (cut > 0) {
                Text(
                    stringResource(R.string.cut_tiles) + " " + cut,
                    color = Warn, fontSize = 12.sp,
                )
            }
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Stat(
            stringResource(R.string.area),
            String.format(Locale.getDefault(), "%.2f", areaVal),
            stringResource(R.string.unit_m2),
        )
        Stat(stringResource(R.string.full_tiles), full.toString())
        Stat(
            stringResource(R.string.cut_tiles),
            cut.toString(),
            valueColor = Warn,
            onClick = { vm.updatePanelSection(6) },
        )
        Stat(
            stringResource(R.string.buy),
            buy.toString(),
            stringResource(R.string.pcs),
            accent = true,
            onClick = { vm.updatePanelSection(7) },
            extra = if (unitPc > 0) {
                money(vm.buyCount * unitPc, vm.prices.currency) + " · +${vm.reservePct}%"
            } else {
                "+${vm.reservePct}%"
            },
        )
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = Txt,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    extra: String? = null,
) {
    Column(
        Modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(13.dp))
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            )
            .then(
                if (accent) {
                    Modifier.background(
                        Brush.linearGradient(listOf(Acc.copy(alpha = 0.30f), Acc.copy(alpha = 0.10f))),
                    )
                } else {
                    Modifier.background(Panel2)
                },
            )
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        NavItem(BaIcons.Tile, stringResource(R.string.tab_editor), tab == 0, Modifier.weight(1f)) { onTab(0) }
        NavItem(BaIcons.Cube, stringResource(R.string.tab_3d), tab == 1, Modifier.weight(1f)) { onTab(1) }
        NavItem(BaIcons.Camera, stringResource(R.string.tab_fit), tab == 2, Modifier.weight(1f)) { onTab(2) }
        NavItem(BaIcons.Doc, stringResource(R.string.tab_report), tab == 3, Modifier.weight(1f)) { onTab(3) }
        NavItem(BaIcons.Star, stringResource(R.string.tab_pro), tab == 4, Modifier.weight(1f)) { onTab(4) }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pill by animateColorAsState(if (selected) AccSoft else Color.Transparent, label = "navPill")
    val tint by animateColorAsState(if (selected) Acc else Dim, label = "navTint")
    val text by animateColorAsState(if (selected) Txt else Dim, label = "navText")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed) 0.92f else 1f, label = "navK")
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .graphicsLayer { scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = press, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(pill)
                .padding(horizontal = 17.dp, vertical = 4.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Общий хвост чипа подрезки для 2D и 3D:
 * « №N · остаётся W×H мм (P%) · срезано C мм».
 * Номер и размеры берутся из единой нумерации ядра; процент — реальная
 * площадь куска, а не габаритная рамка. Для куска без номера (крохотная
 * полоска) возвращается пустая строка.
 */
@Composable
internal fun cutChipSuffix(ci: CutPieceInfo?): String {
    if (ci == null) return ""
    val remain = stringResource(R.string.cut_remain)
    val cutOff = stringResource(R.string.cut_off)
    val mm = stringResource(R.string.unit_mm)
    val sb = StringBuilder()
    sb.append(" №").append(ci.number)
        .append(" · ").append(remain).append(' ')
        .append(ci.wMm.roundToInt()).append('×').append(ci.hMm.roundToInt())
        .append(' ').append(mm)
        .append(" (").append(ci.areaPct.roundToInt()).append("%)")
    val off = ci.cutOffMm
    if (off != null && off >= 1.0) {
        sb.append(" · ").append(cutOff).append(' ')
            .append(off.roundToInt()).append(' ').append(mm)
    }
    return sb.toString()
}
