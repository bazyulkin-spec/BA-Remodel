package com.baremodel.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.AccSoft
import com.baremodel.app.ui.theme.BaIcons
import com.baremodel.app.ui.theme.Dim
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.Good
import com.baremodel.app.ui.theme.Bad
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.Arcs
import com.baremodel.core.AnchorMode
import com.baremodel.core.ArtRect
import com.baremodel.core.DecorMode
import com.baremodel.core.CutPiece
import com.baremodel.core.Finish
import com.baremodel.core.MaterialCalc
import com.baremodel.core.SurfaceKind
import com.baremodel.core.LayoutSuggester
import com.baremodel.core.MaterialKind
import com.baremodel.core.StairsFinish
import com.baremodel.core.isPlank
import com.baremodel.core.PatternType
import com.baremodel.core.TileSpec
import com.baremodel.core.polygonPerimeter
import java.text.DateFormat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt
import java.util.Date
import java.util.Locale

// ---------- атомы ----------

/** Материалы пола в порядке частоты на объекте. */
/** Отделка ступеней: плитка считается раскладкой, дерево — досками, бетон и отметка — ничем. */
private val STAIR_FINISHES = listOf(
    StairsFinish.TILE to R.string.mat_tile,
    StairsFinish.WOOD to R.string.fin_wood,
    StairsFinish.CONCRETE to R.string.fin_concrete,
    StairsFinish.NONE to R.string.fin_mark,
)

/** Типовые размеры: ванная и санузел — самые частые объекты плиточника. */
private val ROOM_PRESETS = listOf(
    R.string.preset_bath to (1.7 to 1.7),
    R.string.preset_wc to (1.2 to 1.5),
    R.string.preset_kitchen to (3.0 to 2.7),
    R.string.preset_room to (4.0 to 3.0),
    R.string.preset_hall to (1.2 to 4.0),
)

private val MATERIALS = listOf(
    MaterialKind.TILE to R.string.mat_tile,
    MaterialKind.LAMINATE to R.string.mat_laminate,
    MaterialKind.PARQUET to R.string.mat_parquet,
    MaterialKind.DECK to R.string.mat_deck,
    MaterialKind.NONE to R.string.mat_none,
)

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        if (warn) Warn else if (selected) Acc else LineC, label = "chipBorder",
    )
    val bg by animateColorAsState(
        when {
            warn -> Warn.copy(alpha = 0.14f)
            selected -> AccSoft
            else -> Color.Transparent
        },
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (warn) Warn else if (selected) Acc2 else Sub, label = "chipFg",
    )
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed) 0.93f else 1f, label = "chipK")
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .graphicsLayer { scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .clickable(interactionSource = press, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconChip(
    icon: ImageVector,
    text: String,
    selected: Boolean = false,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val border = if (warn) Warn else if (selected) Acc else LineC
    val bg = when {
        warn -> Warn.copy(alpha = 0.14f)
        selected -> AccSoft
        else -> Color.Transparent
    }
    val fg = if (warn) Warn else if (selected) Acc2 else Sub
    val pressSrc = remember { MutableInteractionSource() }
    val pressed2 by pressSrc.collectIsPressedAsState()
    val k2 by animateFloatAsState(if (pressed2) 0.93f else 1f, label = "iconChipK")
    val haptic2 = LocalHapticFeedback.current
    Row(
        Modifier
            .graphicsLayer { scaleX = k2; scaleY = k2 }
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .clickable(
                interactionSource = pressSrc,
                indication = null,
            ) {
                haptic2.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = fg)
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Миниатюра узора: рисуется прямо в карточке выбора раскладки. */
@Composable
private fun PatternThumb(type: PatternType, tint: Color) {
    Canvas(Modifier.size(width = 38.dp, height = 27.dp)) {
        val w = size.width
        val h = size.height
        val u = w / 4.6f
        val g = 1.6f
        fun cell(x: Float, y: Float, cw: Float, ch: Float) =
            drawRect(tint, topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(cw, ch))
        when (type) {
            PatternType.GRID -> for (r in 0..1) for (c in 0..1)
                cell(c * (2 * u + g), r * (u + g) + h / 6, 2 * u, u)
            PatternType.HALF -> {
                for (c in 0..1) cell(c * (2 * u + g), h / 6, 2 * u, u)
                for (c in -1..1) cell(c * (2 * u + g) + u, u + g + h / 6, 2 * u, u)
            }
            PatternType.THIRD -> {
                for (c in 0..1) cell(c * (2 * u + g), h / 6, 2 * u, u)
                for (c in 0..1) cell(c * (2 * u + g) - u * 0.7f, u + g + h / 6, 2 * u, u)
            }
            PatternType.HERRINGBONE -> {
                cell(u * 0.4f, h / 8, u, 2.2f * u)
                cell(u * 1.4f + g, h / 8, 2.2f * u, u)
                cell(u * 1.4f + g, h / 8 + u + g, u, 2.2f * u)
                cell(u * 2.4f + 2 * g, h / 8 + u + g, 2.2f * u, u)
            }
        }
    }
}

@Composable
private fun PatternCard(type: PatternType, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) AccSoft else Panel2)
            .border(1.dp, if (selected) Acc else LineC, RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PatternThumb(type, if (selected) Acc2 else Dim)
        Spacer(Modifier.height(7.dp))
        Text(label, color = if (selected) Acc2 else Sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

@Composable
fun NumField(
    label: String,
    value: Double,
    suffix: String,
    min: Double,
    max: Double,
    width: Dp = 84.dp,
    onValue: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(fmt(value)) }
    Column {
        Text("$label, $suffix", color = Sub, fontSize = 10.5.sp)
        Spacer(Modifier.height(3.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t
                val v = t.replace(',', '.').toDoubleOrNull()
                if (v != null && v >= min && v <= max) onValue(v)
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = Txt),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Acc,
                unfocusedBorderColor = LineC,
                focusedTextColor = Txt,
                unfocusedTextColor = Txt,
                cursorColor = Acc,
                focusedContainerColor = Panel2,
                unfocusedContainerColor = Panel2,
            ),
            modifier = Modifier.width(width),
        )
    }
}

@Composable
fun patternLabel(type: PatternType, rotationDeg: Double): String {
    val base = stringResource(
        when (type) {
            PatternType.GRID -> R.string.pat_grid
            PatternType.HALF -> R.string.pat_half
            PatternType.THIRD -> R.string.pat_third
            PatternType.HERRINGBONE -> R.string.pat_herring
        }
    )
    return if (rotationDeg != 0.0) "$base ${rotationDeg.toInt()}°" else base
}

@Composable
private fun Line(label: String, value: String, valueColor: Color = Txt) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Sub, fontSize = 12.5.sp)
        Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- хост панелей ----------

@Composable
fun PanelHost(vm: EditorViewModel, maxContentHeight: Dp = 300.dp) {
    val section = vm.panelSection
    // Две ступени вместо ленты из десяти чипов: пять понятных групп, внутри — подразделы.
    // Номера секций прежние (0..9), поэтому сохранённый выбор и все ссылки работают.
    val groups = listOf(
        R.string.grp_room to listOf(2 to R.string.sec_room, 4 to R.string.sec_surfaces),
        R.string.grp_tile to listOf(0 to R.string.sec_tile, 1 to R.string.sec_pattern),
        R.string.grp_furnish to listOf(3 to R.string.sec_furn),
        R.string.grp_calc to listOf(
            10 to R.string.sec_works,
            5 to R.string.sec_calc, 6 to R.string.sec_offcuts,
            7 to R.string.sec_estimate, 8 to R.string.sec_tips,
        ),
        R.string.grp_project to listOf(9 to R.string.sec_project),
    )
    val groupOf = groups.indexOfFirst { g -> g.second.any { it.first == section } }
        .coerceAtLeast(0)
    // последний открытый подраздел каждой группы — возврат в группу помнит место
    val lastSub = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(section) { lastSub[groupOf] = section }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEachIndexed { gi, (titleRes, subs) ->
                Chip(stringResource(titleRes), selected = gi == groupOf) {
                    val target = lastSub[gi] ?: subs.first().first
                    vm.updatePanelSection(
                        if (subs.any { it.first == target }) target else subs.first().first,
                    )
                }
            }
        }
        val subs = groups[groupOf].second
        if (subs.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                subs.forEach { (idx, res) ->
                    Chip(stringResource(res), selected = section == idx) {
                        vm.updatePanelSection(idx)
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Crossfade(targetState = section, label = "section") { sec ->
            Column(Modifier.fillMaxWidth()) {
            when (sec) {
                0 -> TileSection(vm)
                1 -> PatternSection(vm)
                2 -> RoomSection(vm)
                3 -> FurnitureSection(vm)
                4 -> SurfacesSection(vm)
                5 -> CalcSection(vm)
                6 -> OffcutsSection(vm)
                7 -> EstimateSection(vm)
                8 -> TipsSection(vm)
                10 -> WorksSection(vm)
                else -> ProjectSection(vm)
            }
            }
            }
        }
    }
}

// ---------- секции ----------

/**
 * Раскрывающийся блок настроек: свёрнут — заголовок с кратким значением,
 * тап — открылся. Состояние живёт во ViewModel и переживает смену секций.
 */
@Composable
private fun Fold(
    vm: EditorViewModel,
    key: String,
    title: String,
    subtitle: String? = null,
    default: Boolean = false,
    content: @Composable () -> Unit,
) {
    val open = vm.foldOpen(key, default)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .clickable { vm.toggleFold(key, default) }
                .padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    if (open) "▾" else "▸",
                    color = if (open) Acc else Dim,
                    fontSize = 11.sp,
                )
                Text(
                    title,
                    color = if (open) Txt else Sub,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!open && subtitle != null) {
                Text(subtitle, color = Dim, fontSize = 11.sp, maxLines = 1)
            }
        }
        Fade(visible = open) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) { content() }
        }
    }
}

@Composable
private fun PatternSection(vm: EditorViewModel) {
    val types = listOf(
        PatternType.GRID to R.string.pat_grid,
        PatternType.HALF to R.string.pat_half,
        PatternType.THIRD to R.string.pat_third,
        PatternType.HERRINGBONE to R.string.pat_herring,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.take(2).forEach { (t, res) ->
            PatternCard(t, stringResource(res), vm.pattern.type == t, Modifier.weight(1f)) { vm.setPatternType(t) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.drop(2).forEach { (t, res) ->
            PatternCard(t, stringResource(res), vm.pattern.type == t, Modifier.weight(1f)) { vm.setPatternType(t) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.rotation) + " · ${vm.pattern.rotationDeg.toInt()}°",
        color = Dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Slider(
        value = vm.pattern.rotationDeg.toFloat(),
        onValueChange = { vm.setRotation(it.toDouble()) },
        valueRange = 0f..90f,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Acc,
            inactiveTrackColor = Panel2,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 45, 90).forEach { deg ->
            Chip("$deg°", vm.pattern.rotationDeg.toInt() == deg) { vm.setRotation(deg.toDouble()) }
        }
        Chip(stringResource(R.string.reset_shift)) { vm.resetShift() }
    }
}

private val PRESETS = listOf(
    600.0 to 600.0, 300.0 to 600.0, 800.0 to 800.0, 200.0 to 1200.0, 100.0 to 200.0,
)

private val PALETTE = listOf(
    0xFFC7CCD6, 0xFF98A1AC, 0xFF6C7683, 0xFF3A4658, 0xFF22304A,
    0xFFBFA284, 0xFF8A6D52, 0xFFE7E2D6, 0xFFB7C6BD, 0xFF7A8E9C,
)

@Composable
private fun TileSection(vm: EditorViewModel) {
    val context = LocalContext.current
    MaterialRow(vm)
    Fold(
        vm, "tile.size", stringResource(R.string.fold_size),
        vm.uiTile.widthMm.toInt().toString() + "×" + vm.uiTile.heightMm.toInt() +
            " · " + vm.uiTile.groutMm.toInt() + " " + stringResource(R.string.unit_mm),
        default = true,
    ) {
    val plank = vm.uiMaterial.kind.isPlank
    if (vm.favTiles.isNotEmpty() && !plank) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            vm.favTiles.forEach { t ->
                Chip(
                    "${(t.first / 10).toInt()}×${(t.second / 10).toInt()} ★",
                    vm.uiTile.widthMm == t.first && vm.uiTile.heightMm == t.second &&
                        vm.uiTile.groutMm == t.third,
                ) { vm.applyFavTile(t) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // свои размеры важнее каталога: как только есть избранное — пресеты уходят
        if (vm.favTiles.isEmpty() && !plank) PRESETS.forEach { (w, h) ->
            val label = "${(w / 10).toInt()}×${(h / 10).toInt()}"
            Chip(label, vm.uiTile.widthMm == w && vm.uiTile.heightMm == h) {
                vm.setTileWidth(w)
                vm.setTileHeight(h)
            }
        }
        Chip(
            stringResource(R.string.fav_save),
            vm.favTiles.contains(Triple(vm.uiTile.widthMm, vm.uiTile.heightMm, vm.uiTile.groutMm)),
        ) { vm.toggleFavTile() }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(
            stringResource(if (plank) R.string.plank_len else R.string.width),
            vm.uiTile.widthMm, stringResource(R.string.unit_mm), 30.0, 3000.0,
        ) { vm.setTileWidth(it) }
        NumField(
            stringResource(if (plank) R.string.plank_wid else R.string.length),
            vm.uiTile.heightMm, stringResource(R.string.unit_mm), 30.0, 3000.0,
        ) { vm.setTileHeight(it) }
        NumField(
            stringResource(if (plank) R.string.plank_gap else R.string.grout),
            vm.uiTile.groutMm, stringResource(R.string.unit_mm), 0.0, 30.0,
        ) { vm.setGrout(it) }
    }
    }
    Fold(vm, "tile.color", stringResource(R.string.fold_color)) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PALETTE.forEach { argb ->
            val c = Color(argb)
            val selected = vm.tileImage == null && vm.tileColor == c
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(c)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Acc else LineC, RoundedCornerShape(7.dp))
                    .clickable { vm.setColor(c) },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.paint), vm.paintMode) { vm.togglePaintMode() }
        listOf(0xFF8FB8E8, 0xFFE8DFD2, 0xFF9BC5A6, 0xFFD9A38A, 0xFFB0413E, 0xFF6E7889).forEach { argb ->
            val c = Color(argb)
            val on = vm.paintMode && vm.paintColor == argb.toInt()
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c)
                    .border(
                        if (on) 2.dp else 1.dp,
                        if (on) Acc2 else LineC,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { vm.updatePaintColor(argb.toInt()) },
            )
        }
        if (vm.tileColors.isNotEmpty()) {
            Chip(stringResource(R.string.paint_clear), warn = true) { vm.clearTileColors() }
        }
    }
    if (vm.paintMode) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.paint_hint), color = Sub, fontSize = 10.5.sp)
    }

    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.brush_size), vm.formatBrush) { vm.toggleFormatBrush() }
        val brushes = (
            vm.favTiles.map { Triple(it.first, it.second, it.third) } +
                listOf(
                    Triple(300.0, 300.0, vm.uiTile.groutMm),
                    Triple(300.0, 600.0, vm.uiTile.groutMm),
                    Triple(200.0, 1200.0, vm.uiTile.groutMm),
                    Triple(100.0, 100.0, vm.uiTile.groutMm),
                )
            ).distinctBy { it.first to it.second }.take(7)
        brushes.forEach { b ->
            val on = vm.formatBrush &&
                vm.brushTile.widthMm == b.first && vm.brushTile.heightMm == b.second
            Chip("${(b.first / 10).toInt()}×${(b.second / 10).toInt()}", on) {
                vm.updateBrushTile(TileSpec(b.first, b.second, b.third))
            }
        }
    }
    if (vm.formatBrush) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.brush_size_hint), color = Sub, fontSize = 10.5.sp)
    }
    if (vm.zones.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.zones), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            vm.zones.forEachIndexed { i, z ->
                Chip(
                    stringResource(R.string.zone_n, i + 1) + " · " +
                        z.tile.widthMm.toInt() + "×" + z.tile.heightMm.toInt(),
                    i == vm.activeZone,
                ) { vm.updateActiveZone(if (i == vm.activeZone) -1 else i) }
            }
            if (vm.activeZone >= 0) {
                IconChip(BaIcons.Close, stringResource(R.string.del_zone), warn = true) {
                    vm.deleteActiveZone()
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.zone_note), color = Sub, fontSize = 10.5.sp)
    }

    }
    Fold(vm, "tile.look", stringResource(R.string.fold_look)) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadTileImage(context, uri)
    }
    var artDialog by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccSoft)
            .border(1.dp, Acc.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable {
                artDialog = true
                if (!vm.showArt) vm.toggleArt()
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.tile_art_btn),
            color = Acc2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    if (artDialog) {
        AlertDialog(
            onDismissRequest = { artDialog = false },
            containerColor = Panel2,
            title = { Text(stringResource(R.string.tile_art_btn), color = Txt) },
            text = {
                Column {
                    Text(stringResource(R.string.art_dialog_hint), color = Sub, fontSize = 11.5.sp)
                    Spacer(Modifier.height(10.dp))
                    ArtAreaEditor(vm)
                    Spacer(Modifier.height(10.dp))
                    IconChip(
                        BaIcons.Camera,
                        stringResource(if (vm.tileImage != null) R.string.photo_on else R.string.photo),
                        vm.tileImage != null,
                    ) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { artDialog = false }) {
                    Text(stringResource(R.string.apply), color = Acc2)
                }
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.rotate_tile)) { vm.rotateTile() }
        Chip(stringResource(R.string.variation), vm.variation) { vm.toggleVariation() }
        Chip(stringResource(R.string.show_art), vm.showArt) { vm.toggleArt() }
        IconChip(
            BaIcons.Camera,
            stringResource(if (vm.tileImage != null) R.string.photo_on else R.string.photo),
            vm.tileImage != null,
        ) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (vm.tileImage != null) {
            Chip(stringResource(R.string.clear)) { vm.clearImage() }
        }
    }
    }
    Fold(vm, "tile.decor", stringResource(R.string.fold_decor)) {
        DecorSection(vm)
    }
}

/** Выбор материала пола: от него зависят габарит, узор и как считается закупка. */
@Composable
private fun MaterialRow(vm: EditorViewModel) {
    Text(stringResource(R.string.material), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MATERIALS.forEach { (kind, label) ->
            Chip(stringResource(label), vm.uiMaterial.kind == kind) { vm.setMaterialKind(kind) }
        }
    }
    if (vm.uiMaterial.kind == MaterialKind.NONE) {
        Spacer(Modifier.height(5.dp))
        Text(stringResource(R.string.mat_none_hint), color = Sub, fontSize = 10.5.sp)
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun DecorSection(vm: EditorViewModel) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.sec_decor), color = Acc2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadDecorImage(context, uri)
    }
    Text(stringResource(R.string.art_title), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    ArtAreaEditor(vm)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.art_hint), color = Dim, fontSize = 10.5.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconChip(BaIcons.Camera, stringResource(R.string.decor_photo), vm.decorImage != null) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (vm.decorImage != null) {
            Chip("✓ " + stringResource(R.string.photo_ok), selected = true) { }
        }
        if (vm.decorImage != null) Chip(stringResource(R.string.clear)) { vm.clearDecorImage() }
    }

    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.sec_decor), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            DecorMode.NONE to R.string.decor_none,
            DecorMode.SINGLE to R.string.decor_single,
            DecorMode.PANEL to R.string.decor_panel,
            DecorMode.EVERY_N to R.string.decor_every,
            DecorMode.ALL to R.string.decor_all,
        ).forEach { (m, res) ->
            Chip(stringResource(res), vm.decor.mode == m) { vm.setDecorMode(m) }
        }
        Chip(
            stringResource(R.string.chess),
            vm.decor.mode == DecorMode.EVERY_N && vm.decor.everyN == 2,
        ) {
            vm.setEveryN(2)
            vm.setDecorMode(DecorMode.EVERY_N)
        }
    }

    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.panel_size), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(2 to 2, 3 to 2, 3 to 3, 4 to 3, 5 to 1).forEach { (c2, r2) ->
            Chip(
                c2.toString() + "×" + r2,
                vm.decor.panelCols == c2 && vm.decor.panelRows == r2,
            ) { vm.setPanel(c2, r2) }
        }
        if (vm.panelOn) {
            Chip(stringResource(R.string.panel_off), warn = true) { vm.clearPanel() }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.panel_note), color = Sub, fontSize = 10.5.sp)

    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.anchor_title), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            AnchorMode.ART_CENTER to R.string.anchor_art,
            AnchorMode.TILE_CENTER to R.string.anchor_tile,
            AnchorMode.CORNER to R.string.anchor_corner,
            AnchorMode.FREE to R.string.anchor_free,
        ).forEach { (a, res) ->
            Chip(stringResource(res), vm.anchor == a) { vm.switchAnchor(a) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Line(stringResource(R.string.decor_count), vm.decorIdx.size.toString())
}

/** Рамка области рисунка поверх фото плитки: перетаскивание и изменение размера. */
@Composable
private fun ArtAreaEditor(vm: EditorViewModel) {
    val img = vm.decorImage ?: vm.tileImage
    val art = vm.decor.art
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Panel2)
            .border(1.dp, LineC, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toDouble()
                    val h = size.height.toDouble()
                    if (w <= 0.0 || h <= 0.0) return@awaitEachGesture
                    val start = vm.decor.art
                    val hx = (start.x + start.w) * w
                    val hy = (start.y + start.h) * h
                    val resize = abs(down.position.x.toDouble() - hx) < 60 &&
                        abs(down.position.y.toDouble() - hy) < 60
                    val grabX = down.position.x.toDouble() / w - start.x
                    val grabY = down.position.y.toDouble() / h - start.y
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        val px = (ch.position.x.toDouble() / w).coerceIn(0.0, 1.0)
                        val py = (ch.position.y.toDouble() / h).coerceIn(0.0, 1.0)
                        if (resize) {
                            vm.setArt(ArtRect(start.x, start.y, px - start.x, py - start.y))
                        } else {
                            vm.setArt(ArtRect(px - grabX, py - grabY, start.w, start.h))
                        }
                        ch.consume()
                        if (ev.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (img != null) {
                drawImage(img, dstOffset = IntOffset.Zero, dstSize = IntSize(w.toInt(), h.toInt()))
            } else {
                drawRect(vm.tileColor, topLeft = Offset(0f, 0f), size = Size(w, h))
            }
            val ax = (art.x * w).toFloat()
            val ay = (art.y * h).toFloat()
            val aw = (art.w * w).toFloat()
            val ah = (art.h * h).toFloat()
            val shade = Color(0x8C04060A)
            drawRect(shade, topLeft = Offset(0f, 0f), size = Size(w, ay))
            drawRect(shade, topLeft = Offset(0f, ay + ah), size = Size(w, h - ay - ah))
            drawRect(shade, topLeft = Offset(0f, ay), size = Size(ax, ah))
            drawRect(shade, topLeft = Offset(ax + aw, ay), size = Size(w - ax - aw, ah))
            drawRect(Acc, topLeft = Offset(ax, ay), size = Size(aw, ah), style = Stroke(2f * density))
            drawRect(
                Acc,
                topLeft = Offset(ax + aw - 7f * density, ay + ah - 7f * density),
                size = Size(14f * density, 14f * density),
            )
            val cx = ax + aw / 2
            val cy = ay + ah / 2
            drawLine(Acc2, Offset(cx - 9f * density, cy), Offset(cx + 9f * density, cy), 1.2f * density)
            drawLine(Acc2, Offset(cx, cy - 9f * density), Offset(cx, cy + 9f * density), 1.2f * density)
        }
    }
}

@Composable
private fun RoomSection(vm: EditorViewModel) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.levels_title),
        color = Dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        vm.levelList.forEach { l ->
            Chip(vm.levelTitle(l), l == vm.activeLevel) { vm.switchLevel(l) }
        }
        IconChip(BaIcons.Plus, stringResource(R.string.add_level)) { vm.addLevel() }
        if (vm.levelList.any { it < vm.activeLevel }) {
            Chip(stringResource(R.string.ghost_below), vm.showGhost) { vm.toggleGhost() }
        }
        if (vm.levelList.size > 1) {
            IconChip(BaIcons.Close, stringResource(R.string.del_level), warn = true) {
                vm.deleteActiveLevel()
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.level_hint), color = Sub, fontSize = 10.sp, lineHeight = 14.sp)
    Spacer(Modifier.height(14.dp))
    // быстрый старт: типовой размер одним тапом — новичку не с чего чертить,
    // мастеру на объекте некогда; точные стороны правятся цифрами как обычно
    Text(stringResource(R.string.room_presets), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ROOM_PRESETS.forEach { (res, wh) ->
            Chip(
                stringResource(res) + " " + fmt(wh.first) + "×" + fmt(wh.second),
            ) { vm.applyRoomPreset(wh.first, wh.second) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.rooms_title),
        color = Dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        vm.rooms.forEachIndexed { i, r ->
            Chip(r.name.ifBlank { stringResource(R.string.room_n, i + 1) }, i == vm.activeRoom) {
                vm.switchRoom(i)
            }
        }
        IconChip(BaIcons.Plus, stringResource(R.string.add_room)) { vm.addRoom() }
        if (vm.rooms.size > 1) {
            IconChip(BaIcons.Close, stringResource(R.string.del_room), warn = true) {
                vm.deleteActiveRoom()
            }
        }
    }
    if (vm.rooms.size > 1) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.dock), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(stringResource(R.string.dock_left)) { vm.dockActiveRoom(0) }
            Chip(stringResource(R.string.dock_right)) { vm.dockActiveRoom(1) }
            Chip(stringResource(R.string.dock_up)) { vm.dockActiveRoom(2) }
            Chip(stringResource(R.string.dock_down)) { vm.dockActiveRoom(3) }
        }
    }

    Spacer(Modifier.height(14.dp))
    val planPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadPlanImage(context, uri)
    }
    Fold(vm, "room.under", stringResource(R.string.plan_under)) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconChip(BaIcons.Camera, stringResource(R.string.plan_photo), vm.planImage != null) {
            planPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (vm.planImage != null) {
            Chip(stringResource(R.string.plan_trace), vm.traceMode) { vm.toggleTraceMode() }
            Chip(
                if (vm.ocrNumbers.isEmpty()) {
                    stringResource(R.string.ocr_read)
                } else {
                    stringResource(R.string.ocr_found, vm.ocrNumbers.size)
                },
                vm.ocrNumbers.isNotEmpty(),
            ) { vm.runPlanOcr() }
            Chip(stringResource(R.string.plan_calib), vm.calibMode) { vm.startCalibration() }
            Chip(stringResource(R.string.plan_move), vm.planMove) { vm.togglePlanMove() }
            Chip(stringResource(R.string.plan_clear), warn = true) { vm.clearPlanImage() }
        }
    }
    if (vm.planImage != null) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.25f, 0.45f, 0.7f).forEach { a ->
                Chip(
                    ((a * 100).toInt()).toString() + "%",
                    abs(vm.planAlpha - a) < 0.01f,
                ) { vm.updatePlanAlpha(a) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(
            when {
                vm.calibMode -> R.string.plan_calib_hint
                vm.traceMode -> R.string.plan_trace_hint
                else -> R.string.plan_note
            },
        ),
        color = Sub,
        fontSize = 10.5.sp,
    )

    }
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.wall_thick), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // не фикс: любая толщина цифрой — гипсокартон 7, кирпич 12, несущая 38…
        NumField(
            stringResource(R.string.thick_lbl), vm.wallThicknessM * 100,
            stringResource(R.string.unit_cm), 2.0, 60.0,
        ) { vm.updateWallThickness(it / 100.0) }
        listOf(0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40).forEach { t2 ->
            Chip(
                (t2 * 100).toInt().toString(),
                abs(vm.wallThicknessM - t2) < 0.005,
            ) { vm.updateWallThickness(t2) }
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.draw_points), vm.drawMode) { vm.startDraw() }
        if (vm.room.points.size > 4) {
            Chip(stringResource(R.string.simplify)) { vm.simplifyRoom() }
        }
    }
    Fold(vm, "room.templates", stringResource(R.string.fold_templates)) {
    var w by rememberSaveable { mutableStateOf(4.0) }
    var h by rememberSaveable { mutableStateOf(3.0) }
    Text(stringResource(R.string.rect), color = Sub, fontSize = 11.5.sp)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(stringResource(R.string.width), w, stringResource(R.string.unit_m), 1.0, 30.0) { w = it }
        NumField(stringResource(R.string.length), h, stringResource(R.string.unit_m), 1.0, 30.0) { h = it }
        Box(Modifier.padding(bottom = 6.dp)) {
            Chip(stringResource(R.string.apply)) { vm.applyRect(w, h) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.lshape)) { vm.applyLShape() }
        IconChip(BaIcons.Plus, stringResource(R.string.add_cutout)) { vm.addCutout() }
    }
    }
    val sel = vm.selection
    if (sel is Selection.Vertex && vm.room.points.size > 3) {
        Spacer(Modifier.height(10.dp))
        IconChip(BaIcons.Close, stringResource(R.string.del_point), warn = true) { vm.deleteSelectedVertex() }
    }
    if (sel is Selection.Cut && sel.i in vm.room.cutouts.indices) {
        val c = vm.room.cutouts[sel.i]
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.width), c.w, stringResource(R.string.unit_m), 0.1, 10.0) {
                vm.setSelectedCutW(it)
            }
            NumField(stringResource(R.string.length), c.h, stringResource(R.string.unit_m), 0.1, 10.0) {
                vm.setSelectedCutH(it)
            }
            Box(Modifier.padding(bottom = 6.dp)) {
                IconChip(BaIcons.Close, stringResource(R.string.del_cutout), warn = true) { vm.deleteSelectedCutout() }
            }
        }
    }
    ArcsFold(vm)
    StairsFold(vm)
}

/** Дуги и круглые комнаты: контур гнётся, движок работает с частой ломаной. */
@Composable
private fun ArcsFold(vm: EditorViewModel) {
    val m = stringResource(R.string.unit_m)
    val mm = stringResource(R.string.unit_mm)
    var dia by remember { mutableStateOf(3.0) }
    var axA by remember { mutableStateOf(4.0) }
    var axB by remember { mutableStateOf(2.5) }
    var sag by remember { mutableStateOf(300.0) }
    Spacer(Modifier.height(8.dp))
    Fold(
        vm, "room.arcs", stringResource(R.string.arc_title),
        if (vm.arcRuns.isEmpty()) null else stringResource(R.string.arc_found, vm.arcRuns.size),
    ) {
        Text(stringResource(R.string.arc_hint), color = Sub, fontSize = 10.5.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.arc_dia), dia, m, 0.5, 20.0) { dia = it }
            NumField(stringResource(R.string.arc_axis_a), axA, m, 0.5, 20.0) { axA = it }
            NumField(stringResource(R.string.arc_axis_b), axB, m, 0.5, 20.0) { axB = it }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(stringResource(R.string.arc_round)) { vm.makeRoundRoom(dia) }
            Chip(stringResource(R.string.arc_oval)) { vm.makeOvalRoom(axA, axB) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.arc_pick_wall),
            color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.arc_sag), sag, mm, 10.0, 5000.0) { sag = it }
            Box(Modifier.padding(top = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.arc_bend_out)) { vm.bendSelectedWall(sag) }
                    Chip(stringResource(R.string.arc_bend_in)) { vm.bendSelectedWall(-sag) }
                }
            }
        }
        if (vm.arcRuns.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            vm.arcRuns.forEach { run ->
                Line("R " + fmt(run.radiusM) + " " + m, fmt(run.lengthM) + " " + m)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(
                    R.string.arc_tile_advice,
                    Arcs.maxTileOnArc(vm.arcRuns.minOf { it.radiusM }).toInt(),
                ),
                color = Sub, fontSize = 10.5.sp,
            )
        }
    }
}

/** Ступени и крыльцо: марш на другой этаж, подиум, входные ступени. */
@Composable
private fun StairsFold(vm: EditorViewModel) {
    val pcs = stringResource(R.string.pcs)
    val mm = stringResource(R.string.unit_mm)
    val m = stringResource(R.string.unit_m)
    val m2 = stringResource(R.string.unit_m2)
    Spacer(Modifier.height(8.dp))
    Fold(
        vm, "room.stairs", stringResource(R.string.stairs_title),
        if (vm.stairs.isEmpty()) {
            null
        } else {
            vm.stairs.size.toString() + " · " + vm.stairsPlans.sumOf { it.second.piecesTotal } + " " + pcs
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.add_stairs)) { vm.addStairs() }
            Chip(stringResource(R.string.outdoor), vm.outdoor) { vm.toggleOutdoor() }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(if (vm.outdoor) R.string.outdoor_on_hint else R.string.stairs_hint),
            color = Sub, fontSize = 10.5.sp,
        )
        if (vm.stairs.size > 1) {
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                vm.stairs.forEachIndexed { i, _ ->
                    Chip(stringResource(R.string.stairs_n, i + 1), i == vm.selectedStairsIndex) { vm.selectStairs(i) }
                }
            }
        }
        val st = vm.selectedStairs
        val plan = vm.selectedStairsPlan
        if (st == null || plan == null) {
            if (vm.stairs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.stairs_pick), color = Sub, fontSize = 10.5.sp)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(stringResource(R.string.stairs_width), st.widthM, m, 0.3, 6.0) { vm.setStairsWidth(it) }
                NumField(stringResource(R.string.stairs_count), st.steps.toDouble(), pcs, 1.0, 40.0) { vm.setStairsSteps(it) }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(stringResource(R.string.stairs_tread), st.treadMm, mm, 150.0, 600.0) { vm.setStairsTread(it) }
                NumField(stringResource(R.string.stairs_riser), st.riserMm, mm, 80.0, 300.0) { vm.setStairsRiser(it) }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(stringResource(R.string.stairs_rise), st.riseM, m, 0.1, 6.0) { vm.fitStairsToHeight(it) }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.stairs_rise_hint), color = Sub, fontSize = 10.5.sp)
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(stringResource(R.string.stairs_turn)) { vm.rotateStairs() }
                Chip(stringResource(R.string.stairs_risers), st.risers) { vm.toggleStairsRisers() }
                Chip(stringResource(R.string.stairs_floor_under), !st.cutsFloor) { vm.toggleStairsFloor() }
                Chip(stringResource(R.string.stairs_take_mat)) { vm.stairsTakeRoomMaterial() }
                IconChip(BaIcons.Close, stringResource(R.string.stairs_del), warn = true) { vm.deleteSelectedStairs() }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.stairs_tread_fin), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                STAIR_FINISHES.forEach { (f, res) ->
                    Chip(stringResource(res), st.treadFinish == f) { vm.setStairsTreadFinish(f) }
                }
            }
            if (st.risers) {
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.stairs_riser_fin), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    STAIR_FINISHES.forEach { (f, res) ->
                        Chip(stringResource(res), st.riserFinish == f) { vm.setStairsRiserFinish(f) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.stairs_to), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(stringResource(R.string.stairs_porch), st.toLevel < 0) { vm.setStairsLevel(-1) }
                vm.levelList.forEach { l ->
                    Chip(vm.levelTitle(l), st.toLevel == l) { vm.setStairsLevel(l) }
                }
            }
            Spacer(Modifier.height(9.dp))
            Line(
                stringResource(R.string.stairs_run),
                fmt(st.runM) + " " + m + "  ·  " + fmt(st.riseM) + " " + m,
            )
            Line(stringResource(R.string.stairs_area), fmt(plan.areaM2) + " " + m2)
            if (plan.treadPieces > 0) {
                Line(stringResource(R.string.stairs_tread_pcs), plan.treadPieces.toString() + " " + pcs)
            }
            if (st.treadFinish == StairsFinish.WOOD) {
                Line(
                    stringResource(R.string.stairs_boards),
                    plan.treadPieces.toString() + " × " + (st.widthM * 1000).toInt() + "×" + st.treadMm.toInt() + " " + mm,
                )
            }
            if (st.risers && st.riserFinish == StairsFinish.WOOD) {
                Line(
                    stringResource(R.string.stairs_riser_boards),
                    plan.riserPieces.toString() + " × " + (st.widthM * 1000).toInt() + "×" + st.riserMm.toInt() + " " + mm,
                )
            }
            val cut = vm.selectedStairsCut
            if (cut != null && st.treadFinish == StairsFinish.TILE) {
                Line(stringResource(R.string.stairs_whole), cut.wholeTreadTiles.toString() + " " + pcs)
                if (cut.treadCuts > 0) {
                    Line(
                        stringResource(R.string.stairs_edge),
                        cut.treadCuts.toString() + " × " + cut.treadCutMm.toInt() + " " + mm + "  ·  " +
                            stringResource(R.string.stairs_from_n, cut.treadTiles, cut.perTreadTile),
                    )
                }
            }
            if (plan.riserPieces > 0 && st.riserFinish == StairsFinish.TILE) {
                Line(
                    stringResource(R.string.stairs_riser_pcs),
                    plan.riserPieces.toString() + " " + pcs + "  ·  " +
                        stringResource(R.string.stairs_from_tiles, plan.tilesForRisers, plan.stripsPerTile),
                )
            }
            if (plan.buyPieces > 0) {
                Line(stringResource(R.string.buy), plan.buyPieces.toString() + " " + pcs, Acc2)
            }
            if (plan.cutMm > 1.0 && st.treadFinish == StairsFinish.TILE) {
                Line(stringResource(R.string.stairs_cut), plan.cutMm.toInt().toString() + " " + mm, Warn)
            }
            Line(
                stringResource(R.string.stairs_formula),
                plan.formulaMm.toInt().toString() + " " + mm,
                if (plan.comfy) Txt else Warn,
            )
            if (!plan.comfy) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.stairs_formula_warn), color = Warn, fontSize = 10.5.sp)
            }
            if (plan.treadTooShort) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.stairs_tread_warn), color = Warn, fontSize = 10.5.sp)
            }
            if (plan.riserBad) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.stairs_riser_warn), color = Warn, fontSize = 10.5.sp)
            }
        }
    }
}

private data class FurnPreset(
    val res: Int,
    val w: Double,
    val h: Double,
    val heightM: Double,
    val kind: String,
)

private val FURN_PRESETS = listOf(
    FurnPreset(R.string.furn_kitchen, 3.0, 0.6, 0.9, "kitchen"),
    FurnPreset(R.string.furn_bath, 1.7, 0.7, 0.6, "bath"),
    FurnPreset(R.string.furn_wc, 0.4, 0.7, 0.8, "wc"),
    FurnPreset(R.string.furn_washer, 0.6, 0.6, 0.85, "washer"),
    FurnPreset(R.string.furn_cabinet, 1.2, 0.5, 0.75, "cabinet"),
    FurnPreset(R.string.furn_fridge, 0.6, 0.7, 1.85, "fridge"),
    FurnPreset(R.string.furn_wardrobe, 1.2, 0.6, 2.1, "wardrobe"),
    FurnPreset(R.string.furn_table, 1.2, 0.8, 0.75, "table"),
    FurnPreset(R.string.furn_chair, 0.45, 0.45, 0.85, "chair"),
    FurnPreset(R.string.furn_custom, 1.0, 0.6, 0.85, "box"),
)

@Composable
private fun FurnitureSection(vm: EditorViewModel) {
    var addOpen by remember { mutableStateOf(false) }
    Box {
        IconChip(BaIcons.Plus, stringResource(R.string.add_furn), selected = true) { addOpen = true }
        DropdownMenu(
            expanded = addOpen,
            onDismissRequest = { addOpen = false },
            modifier = Modifier.background(Panel2),
        ) {
            FURN_PRESETS.forEach { fp ->
                val name = stringResource(fp.res)
                DropdownMenuItem(
                    text = { Text(name, color = Txt, fontSize = 13.sp) },
                    onClick = {
                        addOpen = false
                        vm.addFurniture(name, fp.w, fp.h, fp.heightM, fp.kind)
                    },
                )
            }
        }
    }

    val sel = vm.selection
    if (sel is Selection.Furn && sel.i in vm.furniture.indices) {
        val f = vm.furniture[sel.i]
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.width), f.w, stringResource(R.string.unit_m), 0.2, 10.0) {
                vm.setSelectedFurnW(it)
            }
            NumField(stringResource(R.string.length), f.h, stringResource(R.string.unit_m), 0.2, 10.0) {
                vm.setSelectedFurnH(it)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.rotation)) { vm.rotateSelectedFurn() }
            Chip(stringResource(R.string.under_tile), f.coversFinish) { vm.toggleSelectedFurnCover() }
            IconChip(BaIcons.Close, stringResource(R.string.del_furn), warn = true) { vm.deleteSelectedFurniture() }
        }
    }

    if (vm.furniture.isNotEmpty()) {
        val cov = vm.coverage
        Spacer(Modifier.height(12.dp))
        if (cov.decorTiles > 0) {
            Line(
                stringResource(R.string.decor_covered),
                "${cov.coveredPct}%",
                if (cov.decorHidden) Warn else Good,
            )
        }
        Line(stringResource(R.string.hidden_tiles), cov.hiddenTiles.toString())
        if (cov.savedTiles > 0) Line(stringResource(R.string.saved_tiles), "−${cov.savedTiles}", Good)
    }
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.hint_room), color = Dim, fontSize = 10.5.sp)
}

@Composable
private fun OffcutsSection(vm: EditorViewModel) {

    val lay = vm.layout
    val tileAreaM2o = vm.tile.widthMm * vm.tile.heightMm / 1_000_000.0
    val usedM2 = lay.cutPieces.sumOf { it.count * it.aCm * it.bCm } / 10_000.0
    val cutTilesM2 = lay.cutCount * tileAreaM2o
    val wastePct = if (cutTilesM2 > 0) ((cutTilesM2 - usedM2) / cutTilesM2 * 100).coerceIn(0.0, 100.0) else 0.0
    val cutPctO = if (lay.totalCount > 0) lay.cutCount * 100.0 / lay.totalCount else 0.0
    Text(stringResource(R.string.piece_note), color = Sub, fontSize = 10.5.sp)
    Spacer(Modifier.height(8.dp))

    // парование резов: две подрезки из одной плитки — покупать меньше
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Chip(stringResource(R.string.pair_cuts), selected = vm.pairCuts) { vm.togglePairCuts() }
        if (vm.pairCuts && vm.cutPairs.saved > 0) {
            Text(
                stringResource(R.string.pair_saving) + ": −" + vm.cutPairs.saved + " " +
                    stringResource(R.string.pcs),
                color = Good,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (vm.pairCuts && vm.cutPairs.bins.any { it.nums.size >= 2 }) {
        var pairsOpen by remember { mutableStateOf(false) }
        Spacer(Modifier.height(6.dp))
        Chip(
            if (pairsOpen) stringResource(R.string.hide) else stringResource(R.string.pair_show),
            selected = pairsOpen,
        ) { pairsOpen = !pairsOpen }
        if (pairsOpen) {
            vm.cutPairs.bins.filter { it.nums.size >= 2 }.forEach { bin ->
                Text(
                    bin.nums.joinToString(" + ") { "№$it" },
                    color = Sub,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
    if (vm.hiddenTiles.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Line(
            stringResource(R.string.hidden_zone),
            vm.hiddenTiles.size.toString() + " " + stringResource(R.string.pcs) +
                (
                    if (vm.hiddenCutNumbers.isNotEmpty()) {
                        "  ·  №" + vm.hiddenCutNumbers.joinToString(", №")
                    } else {
                        ""
                    }
                    ),
            Good,
        )
        Text(stringResource(R.string.hidden_zone_tip), color = Sub, fontSize = 10.5.sp)
    }
    Spacer(Modifier.height(8.dp))
    Line(
        stringResource(R.string.cut_tiles),
        lay.cutCount.toString() + "  (" + String.format(Locale.getDefault(), "%.0f", cutPctO) + "%)",
        Warn,
    )
    Line(
        stringResource(R.string.waste_share),
        String.format(Locale.getDefault(), "%.2f", (cutTilesM2 - usedM2).coerceAtLeast(0.0)) + " " +
            stringResource(R.string.unit_m2) + "  (" +
            String.format(Locale.getDefault(), "%.0f", wastePct) + "%)",
        Warn,
    )
    Spacer(Modifier.height(10.dp))
    val l = vm.layout
    val tileAreaCm = vm.tile.widthMm * vm.tile.heightMm / 100.0  // см²
    var usable = 0
    var waste = 0
    var offcutArea = 0.0
    l.cutPieces.forEach { p ->
        if (minOf(p.aCm, p.bCm) >= 10.0) usable += p.count else waste += p.count
        offcutArea += p.count * (tileAreaCm - p.aCm * p.bCm).coerceAtLeast(0.0)
    }
    Line(stringResource(R.string.offcut_usable), usable.toString(), Good)
    Line(stringResource(R.string.offcut_waste), waste.toString(), Warn)
    Line(
        stringResource(R.string.offcut_area),
        String.format(Locale.getDefault(), "%.2f", offcutArea / 10000.0) + " " + stringResource(R.string.unit_m2),
    )
    Spacer(Modifier.height(10.dp))
    if (l.cutPieces.isEmpty()) {
        Text(stringResource(R.string.no_cuts), color = Sub, fontSize = 12.sp)
    } else {
        l.cutPieces.take(24).forEach { p -> OffcutRow(p, vm.tile.groutMm, tileAreaCm, selectedRow = vm.highlightCut == (p.aCm to p.bCm), onRowClick = { vm.toggleHighlightCut(p.aCm, p.bCm) }) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pieces_note), color = Dim, fontSize = 10.5.sp)
    }
}

@Composable
private fun OffcutRow(
    p: CutPiece,
    grout: Double,
    tileAreaCm: Double,
    selectedRow: Boolean,
    onRowClick: () -> Unit,
) {
    val reusable = minOf(p.aCm, p.bCm) >= 10.0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selectedRow) Good.copy(alpha = 0.14f) else Panel2)
            .border(
                if (selectedRow) 1.6.dp else 1.dp,
                when {
                    selectedRow -> Good
                    reusable -> Good.copy(alpha = 0.35f)
                    else -> LineC
                },
                RoundedCornerShape(11.dp),
            )
            .clickable { onRowClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 26.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (reusable) Good.copy(alpha = 0.25f) else Warn.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                String.format(Locale.getDefault(), "%.1f", p.aCm) + " × " +
                    String.format(Locale.getDefault(), "%.1f", p.bCm) + " " +
                    stringResource(R.string.unit_cm) +
                    if (tileAreaCm > 0) {
                        "  (" + String.format(
                            Locale.getDefault(),
                            "%.0f",
                            (p.aCm * p.bCm / tileAreaCm * 100).coerceIn(0.0, 100.0),
                        ) + "%)"
                    } else {
                        ""
                    },
                color = Txt,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            p.count.toString() + " " + stringResource(R.string.pcs),
            color = if (reusable) Good else Sub,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SurfacesSection(vm: EditorViewModel) {
    val model = vm.model
    val active = vm.activeSurface
    val m2 = stringResource(R.string.unit_m2)

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.surf_floor), active == "floor") { vm.selectSurface("floor") }
        model.walls.forEachIndexed { i, w ->
            Chip(stringResource(R.string.surf_wall) + " ${i + 1}", active == w.id) { vm.selectSurface(w.id) }
        }
        Chip(stringResource(R.string.surf_ceiling), active == "ceiling") { vm.selectSurface("ceiling") }
    }

    if (active.startsWith("wall-")) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.wall_thick_this),
            color = Dim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val own = vm.wallThickness[active]
            Chip(stringResource(R.string.same_as_all), own == null) {
                vm.updateWallThicknessOf(active, null)
            }
            // индивидуально: своя толщина именно этой стены, цифрой
            NumField(
                stringResource(R.string.thick_lbl),
                (own ?: vm.wallThicknessM) * 100,
                stringResource(R.string.unit_cm), 2.0, 60.0,
            ) { vm.updateWallThicknessOf(active, it / 100.0) }
            listOf(0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40).forEach { t2 ->
                Chip(
                    (t2 * 100).toInt().toString(),
                    own != null && abs(own - t2) < 0.005,
                ) { vm.updateWallThicknessOf(active, t2) }
            }
        }
    }

    val surface = model.surfaces.firstOrNull { it.id == active } ?: return
    val finish = vm.finishOf(active)
    val area = vm.surfaceAreaM2(active)

    Fold(vm, "surf.finish", stringResource(R.string.fold_finish), default = true) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Finish.TILE to R.string.finish_tile,
            Finish.WALLPAPER to R.string.finish_wallpaper,
            Finish.PAINT to R.string.finish_paint,
            Finish.NONE to R.string.finish_none,
        ).forEach { (f, res) ->
            Chip(stringResource(res), finish == f) { vm.setFinish(active, f) }
        }
    }

    Spacer(Modifier.height(12.dp))
    Line(stringResource(R.string.net_area), String.format(Locale.getDefault(), "%.2f", area) + " " + m2)

    when (finish) {
        Finish.TILE -> {
            val lay = vm.surfaceLayout(active)
            Line(stringResource(R.string.full_tiles), lay.fullCount.toString())
            Line(stringResource(R.string.cut_tiles), lay.cutCount.toString(), Warn)
            Line(
                stringResource(R.string.buy),
                ceil(lay.totalCount * (1 + vm.reservePct / 100.0)).toInt().toString() + " " + stringResource(R.string.pcs),
                Acc2,
            )
            Line(
                stringResource(R.string.adhesive),
                String.format(Locale.getDefault(), "%.1f", MaterialCalc.tileAdhesiveKg(area, vm.tile)),
            )
        }

        Finish.WALLPAPER -> {
            val wide = if (surface.kind == SurfaceKind.WALL) {
                model.walls.first { it.id == active }.lengthM
            } else {
                sqrt(area.coerceAtLeast(0.01))
            }
            val high = if (surface.kind == SurfaceKind.WALL) vm.wallHeightM else sqrt(area.coerceAtLeast(0.01))
            val wp = MaterialCalc.wallpaper(wide, high)
            Line(stringResource(R.string.rolls), wp.rolls.toString(), Acc2)
            Line(stringResource(R.string.strips), wp.strips.toString())
            Line(
                stringResource(R.string.repeat),
                String.format(Locale.getDefault(), "%.2f", wp.stripLenM) + " " + stringResource(R.string.unit_m),
            )
        }

        Finish.PAINT -> {
            Line(stringResource(R.string.coats), "2")
            Line(
                stringResource(R.string.liters),
                String.format(Locale.getDefault(), "%.1f", MaterialCalc.paintLiters(area, 2)),
                Acc2,
            )
        }

        Finish.NONE -> Unit
    }

    }
    if (surface.kind == SurfaceKind.WALL) {
        Fold(
            vm, "surf.openings", stringResource(R.string.openings),
            vm.openingsOf(active).size.toString(),
        ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconChip(BaIcons.Plus, stringResource(R.string.opening_window)) {
                vm.addOpening(active, 1.4, 1.4, 0.9, OPENING_WINDOW)
            }
            IconChip(BaIcons.Plus, stringResource(R.string.opening_door)) {
                vm.addOpening(active, 0.9, 2.05, 0.0, OPENING_DOOR)
            }
            IconChip(BaIcons.Plus, stringResource(R.string.opening_balcony)) {
                vm.addOpening(active, 0.8, 2.1, 0.0, OPENING_BALCONY)
            }
            IconChip(BaIcons.Plus, stringResource(R.string.opening_entry)) {
                vm.addOpening(active, 1.0, 2.05, 0.0, OPENING_ENTRY)
            }
            IconChip(BaIcons.Plus, stringResource(R.string.opening_passage)) {
                vm.addOpening(active, 1.2, 2.1, 0.0, OPENING_PASSAGE)
            }
        }
        // раскрытый редактор проёма: тип, отступ от начала стены, размеры
        var openEdit by remember(active) { mutableStateOf(-1) }
        val kindNames = listOf(
            stringResource(R.string.kind_window),
            stringResource(R.string.kind_door),
            stringResource(R.string.kind_balcony),
            stringResource(R.string.kind_entry),
            stringResource(R.string.kind_passage),
        )
        val wallLen = vm.model.walls.firstOrNull { it.id == active }?.lengthM ?: 99.0
        vm.openingsOf(active).forEachIndexed { i, o ->
            val kind = vm.openingKindsOf(active).getOrNull(i) ?: OPENING_WINDOW
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        kindNames.getOrElse(kind) { kindNames[0] } + " · " +
                            String.format(Locale.getDefault(), "%.2f × %.2f", o.w, o.h) +
                            " " + stringResource(R.string.unit_m) + " · ⇤ " +
                            String.format(Locale.getDefault(), "%.2f", o.x),
                        color = Sub,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { openEdit = if (openEdit == i) -1 else i },
                    )
                    Chip(
                        if (openEdit == i) stringResource(R.string.hide) else stringResource(R.string.edit),
                        selected = openEdit == i,
                    ) { openEdit = if (openEdit == i) -1 else i }
                    Spacer(Modifier.width(6.dp))
                    IconChip(BaIcons.Close, "", warn = true) {
                        if (openEdit == i) openEdit = -1
                        vm.deleteOpening(active, i)
                    }
                }
                if (openEdit == i) {
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        kindNames.forEachIndexed { k, name ->
                            Chip(name, selected = kind == k) { vm.setOpeningKind(active, i, k) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        NumField(
                            stringResource(R.string.opening_offset), o.x,
                            stringResource(R.string.unit_m), 0.0, wallLen,
                        ) { vm.updateOpening(active, i, x = it) }
                        NumField(
                            stringResource(R.string.width), o.w,
                            stringResource(R.string.unit_m), 0.1, wallLen,
                        ) { vm.updateOpening(active, i, w = it) }
                        NumField(
                            stringResource(R.string.height), o.h,
                            stringResource(R.string.unit_m), 0.1, vm.wallHeightM,
                        ) { vm.updateOpening(active, i, h = it) }
                        if (kind == OPENING_WINDOW) {
                            NumField(
                                stringResource(R.string.opening_sill), o.y,
                                stringResource(R.string.unit_m), 0.0, vm.wallHeightM,
                            ) { vm.updateOpening(active, i, sill = it) }
                        }
                    }
                }
            }
        }
        }
    }

    Fold(vm, "surf.totals", stringResource(R.string.total_materials)) {
    var tilesTotal = 0
    var rollsTotal = 0
    var litersTotal = 0.0
    model.surfaces.forEach { su ->
        val a = vm.surfaceAreaM2(su.id)
        when (vm.finishOf(su.id)) {
            Finish.TILE -> tilesTotal += vm.surfaceLayout(su.id).totalCount
            Finish.WALLPAPER -> {
                val w = if (su.kind == SurfaceKind.WALL) model.walls.first { it.id == su.id }.lengthM else sqrt(a.coerceAtLeast(0.01))
                val h = if (su.kind == SurfaceKind.WALL) vm.wallHeightM else sqrt(a.coerceAtLeast(0.01))
                rollsTotal += MaterialCalc.wallpaper(w, h).rolls
            }
            Finish.PAINT -> litersTotal += MaterialCalc.paintLiters(a, 2)
            Finish.NONE -> Unit
        }
    }
    if (tilesTotal > 0) Line(
        stringResource(R.string.total_tiles),
        ceil(tilesTotal * (1 + vm.reservePct / 100.0)).toInt().toString() + " " + stringResource(R.string.pcs),
    )
    if (rollsTotal > 0) Line(stringResource(R.string.rolls), rollsTotal.toString())
    if (litersTotal > 0) Line(
        stringResource(R.string.liters),
        String.format(Locale.getDefault(), "%.1f", litersTotal),
    )
    }
}

@Composable
private fun CalcSection(vm: EditorViewModel) {
    val formats = vm.countsByFormat()
    if (formats.size > 1) {
        Text(stringResource(R.string.by_format), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        formats.forEach { (fmt, cnt) ->
            Line(fmt, cnt.toString() + " " + stringResource(R.string.pcs), Acc2)
        }
        Spacer(Modifier.height(12.dp))
    }
    val l = vm.layout
    Line(stringResource(R.string.area), String.format(Locale.getDefault(), "%.2f", l.areaM2) + " " + stringResource(R.string.unit_m2))
    Line(
        stringResource(R.string.perimeter),
        String.format(Locale.getDefault(), "%.2f", polygonPerimeter(vm.room.points)) + " " + stringResource(R.string.unit_m),
    )
    val pp = vm.plankPlan
    val mat = vm.uiMaterial
    Line(stringResource(if (pp != null) R.string.plank_full else R.string.full_tiles), l.fullCount.toString())
    val cutPct = if (l.totalCount > 0) l.cutCount * 100.0 / l.totalCount else 0.0
    Line(
        stringResource(if (pp != null) R.string.plank_cut else R.string.cut_tiles),
        l.cutCount.toString() + "  (" + String.format(Locale.getDefault(), "%.0f", cutPct) + "% " +
            stringResource(R.string.cut_share) + ")",
        Warn,
    )
    Line(stringResource(if (pp != null) R.string.plank_total else R.string.total_tiles), l.totalCount.toString())

    Spacer(Modifier.height(10.dp))
    if (pp == null) {
        val glue = MaterialCalc.tileAdhesiveKg(l.areaM2, vm.tile)
        val groutKg = MaterialCalc.groutKg(l.areaM2, vm.tile)
        Line(stringResource(R.string.need_glue), String.format(Locale.getDefault(), "%.0f", glue) + " " + stringResource(R.string.unit_kg))
        Line(stringResource(R.string.need_grout), String.format(Locale.getDefault(), "%.1f", groutKg) + " " + stringResource(R.string.unit_kg))
    } else {
        Text(stringResource(R.string.plank_title), color = Acc2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Line(stringResource(R.string.plank_rows), pp.rowCount.toString())
        Line(
            stringResource(R.string.plank_need),
            pp.planksWithReserve.toString() + " " + stringResource(R.string.pcs),
            Acc2,
        )
        if (pp.packs > 0) {
            Line(
                stringResource(R.string.plank_packs),
                pp.packs.toString() + " × " + String.format(Locale.getDefault(), "%.2f", mat.packM2) +
                    " " + stringResource(R.string.unit_m2),
                Acc2,
            )
        }
        Line(
            stringResource(R.string.plank_waste),
            String.format(Locale.getDefault(), "%.1f", pp.wastePct) + "%",
            if (pp.wastePct > 12.0) Warn else Txt,
        )
        if (pp.savedPlanks > 0) {
            Line(stringResource(R.string.plank_saved), pp.savedPlanks.toString() + " " + stringResource(R.string.pcs))
        }
        if (pp.leftoversMm.isNotEmpty()) {
            Line(
                stringResource(R.string.plank_left),
                pp.leftoversMm.take(4).joinToString(" · ") { it.toInt().toString() } +
                    " " + stringResource(R.string.unit_mm),
            )
        }
        Line(
            stringResource(R.string.plank_underlay),
            String.format(Locale.getDefault(), "%.2f", pp.floorM2) + " " + stringResource(R.string.unit_m2),
        )
        if (pp.shortLastRows > 0) {
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.plank_short_warn, pp.shortLastRows, mat.minEndMm.toInt()),
                color = Warn, fontSize = 10.5.sp,
            )
        }
        if (pp.tightJoints > 0) {
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.plank_tight_warn, mat.staggerMm.toInt()),
                color = Warn, fontSize = 10.5.sp,
            )
        }
        if (pp.estimated) {
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.plank_hb_hint), color = Sub, fontSize = 10.5.sp)
        }
    }

    // ---------- плинтус: сегменты, распил по хлыстам, советы по остаткам ----------
    Fold(
        vm, "calc.skirt", stringResource(R.string.need_plinth),
        String.format(Locale.getDefault(), "%.1f", vm.skirtPlan.totalM) + " " +
            stringResource(R.string.unit_m),
    ) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.skirt_bars_mode), vm.skirtMode == 0) { vm.switchSkirtMode(0) }
        Chip(stringResource(R.string.skirt_tiles_mode), vm.skirtMode == 1) { vm.switchSkirtMode(1) }
    }
    Spacer(Modifier.height(7.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (vm.skirtMode == 0) {
            NumField(
                stringResource(R.string.skirt_bar_len), vm.skirtBarLenM,
                stringResource(R.string.unit_m), 0.5, 6.0,
            ) { vm.setSkirtBarLen(it) }
        } else {
            NumField(
                stringResource(R.string.skirt_height), vm.skirtHeightMm,
                stringResource(R.string.unit_mm), 30.0, 200.0,
            ) { vm.setSkirtHeight(it) }
        }
    }
    Spacer(Modifier.height(5.dp))
    val sk = vm.skirtPlan
    Line(
        stringResource(R.string.skirt_total),
        String.format(Locale.getDefault(), "%.1f", sk.totalM) + " " + stringResource(R.string.unit_m) +
            "  ·  " + sk.segments.size + " " + stringResource(R.string.skirt_segs),
    )
    if (vm.skirtMode == 0) {
        Line(
            stringResource(R.string.skirt_need_bars),
            sk.bars.size.toString() + " × " +
                String.format(Locale.getDefault(), "%.1f", sk.barLenM) + " " + stringResource(R.string.unit_m) +
                (if (sk.joints > 0) "  ·  " + sk.joints + " " + stringResource(R.string.skirt_joints) else ""),
        )
    } else {
        val tilesN = kotlin.math.ceil(sk.bars.size.toDouble() / vm.skirtStripsPerTile).toInt()
        Line(
            stringResource(R.string.skirt_need_strips),
            sk.bars.size.toString() + "  ·  " + tilesN + " " + stringResource(R.string.skirt_tiles_cnt) +
                " (" + vm.skirtStripsPerTile + " " + stringResource(R.string.skirt_per_tile) + ")",
        )
        val offc = vm.skirtFromOffcuts
        if (offc.first > 0) {
            Line(
                stringResource(R.string.skirt_offcut_tip),
                "≈" + offc.first + "  ·  " +
                    String.format(Locale.getDefault(), "%.1f", offc.second) + " " + stringResource(R.string.unit_m),
                Good,
            )
        }
    }
    var skirtOpen by remember { mutableStateOf(false) }
    Spacer(Modifier.height(5.dp))
    Chip(
        if (skirtOpen) stringResource(R.string.hide) else stringResource(R.string.skirt_plan_btn),
        selected = skirtOpen,
    ) { skirtOpen = !skirtOpen }
    if (skirtOpen) {
        Spacer(Modifier.height(6.dp))
        // строки — заранее: joinToString не inline, @Composable внутри неё нельзя
        val wallShort = stringResource(R.string.skirt_wall_short)
        val restLbl = stringResource(R.string.skirt_rest)
        sk.bars.forEachIndexed { bi, bar ->
            val parts = bar.cuts.joinToString(" + ") { c ->
                val seg = sk.segments.getOrNull(c.segment)
                val wallNo = (seg?.wall ?: 0) + 1
                val suffix = if (seg != null && seg.partsOnWall > 1) {
                    "·" + ('a' + seg.partOfWall)
                } else {
                    ""
                }
                String.format(Locale.getDefault(), "%.2f", c.lenM) +
                    " → " + wallShort + wallNo + suffix
            }
            Text(
                "№" + (bi + 1) + ":  " + parts +
                    (
                        if (bar.restM > 0.02) {
                            "   ·  " + restLbl + " " +
                                String.format(Locale.getDefault(), "%.2f", bar.restM)
                        } else {
                            ""
                        }
                        ),
                color = Sub,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reserve), color = Sub, fontSize = 12.sp)
        listOf(5, 10, 15).forEach { p ->
            Chip("$p%", vm.reservePct == p) { vm.setReserve(p) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Acc.copy(alpha = 0.12f))
            .border(1.dp, Acc, RoundedCornerShape(11.dp))
            .padding(12.dp),
    ) {
        Text(stringResource(R.string.buy), color = Acc2, fontSize = 11.5.sp)
        Text(
            "${vm.buyCount} ${stringResource(R.string.pcs)} ≈ " +
                String.format(Locale.getDefault(), "%.2f", vm.buyM2) + " " + stringResource(R.string.unit_m2),
            color = Txt,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    }
    Fold(vm, "calc.walls", stringResource(R.string.cuts_by_walls)) {
    val rep = vm.cutReport
    rep.edges.forEach { e ->
        Line(
            stringResource(R.string.wall) + " ${e.edgeIndex + 1} · " +
                String.format(Locale.getDefault(), "%.2f", e.lengthM) + " " + stringResource(R.string.unit_m),
            String.format(Locale.getDefault(), "%.1f", e.minStripCm) + " " + stringResource(R.string.unit_cm),
        )
    }
    if (rep.edges.size == 4) {
        val sym = rep.symmetricX && rep.symmetricY
        Line(
            stringResource(R.string.symmetry),
            stringResource(if (sym) R.string.yes else R.string.no),
            if (sym) Good else Warn,
        )
    }
    rep.warnings.forEach { w ->
        val text = when (w.code) {
            "THIN_STRIP" -> stringResource(R.string.w_thin, String.format(Locale.getDefault(), "%.1f", w.valueCm))
            "TAPERED_STRIP" -> stringResource(R.string.w_taper, String.format(Locale.getDefault(), "%.1f", w.valueCm))
            else -> stringResource(R.string.w_asym)
        }
        Text(
            text,
            color = if (w.code == "THIN_STRIP") Warn else Sub,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (w.code == "THIN_STRIP") Warn.copy(alpha = 0.12f) else Panel2)
                .padding(horizontal = 11.dp, vertical = 9.dp),
        )
    }

    }
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.disclaimer), color = Sub, fontSize = 10.5.sp)
}

/** Денежный формат: крупные суммы без копеек. */
fun money(v: Double, cur: String): String =
    String.format(Locale.getDefault(), if (v >= 100) "%.0f" else "%.2f", v) + " " + cur

@Composable
fun surfaceTitle(id: String): String = when (id) {
    "floor" -> stringResource(R.string.surf_floor)
    "ceiling" -> stringResource(R.string.surf_ceiling)
    else -> stringResource(R.string.surf_wall) + " " + id.removePrefix("wall-")
}

@Composable
fun finishTitle(f: Finish): String = stringResource(
    when (f) {
        Finish.TILE -> R.string.finish_tile
        Finish.WALLPAPER -> R.string.finish_wallpaper
        Finish.PAINT -> R.string.finish_paint
        Finish.NONE -> R.string.finish_none
    },
)

@Composable
private fun WorksSection(vm: EditorViewModel) {
    val rows = vm.worksList()
    if (rows.isEmpty()) {
        Text(stringResource(R.string.works_empty), color = Sub, fontSize = 11.5.sp)
        return
    }
    val done = rows.count { vm.workStatusOf(it.key) == 2 }
    Line(
        stringResource(R.string.works_progress),
        "$done / ${rows.size}",
        if (done == rows.size) Good else Txt,
    )
    val statusLabels = listOf(
        stringResource(R.string.work_plan),
        stringResource(R.string.work_doing),
        stringResource(R.string.work_done),
    )
    rows.forEach { rw ->
        val st = vm.workStatusOf(rw.key)
        Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    rw.title,
                    color = if (st == 2) Sub else Txt,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val tone = when (st) {
                    2 -> Good
                    1 -> Acc2
                    else -> Dim
                }
                Text(
                    statusLabels[st],
                    color = tone,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, tone.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { vm.cycleWorkStatus(rw.key) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Text(rw.detail, color = Sub, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.works_hint), color = Sub, fontSize = 10.5.sp)
}

@Composable
private fun EstimateSection(vm: EditorViewModel) {
    val p = vm.prices
    val cur = p.currency

    if (vm.rooms.size > 1) {
        val apt = vm.apartmentPieces()
        Line(
            stringResource(R.string.apt_total) + " · " + vm.rooms.size,
            apt.first.toString() + " " + stringResource(R.string.pcs) +
                if (apt.second > 0) " ≈ " + money(apt.second, cur) else "",
            Acc2,
        )
        Spacer(Modifier.height(10.dp))
    }

    Text(stringResource(R.string.currency), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("₪", "$", "€", "₽").forEach { c ->
            Chip(c, cur == c) { vm.updatePrices { it.copy(currency = c) } }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(stringResource(R.string.price_tile_m2), p.tileM2, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(tileM2 = v) }
        }
        NumField(stringResource(R.string.price_tile_pc), p.tilePc, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(tilePc = v) }
        }
        NumField(stringResource(R.string.price_adhesive_kg), p.adhesiveKg, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(adhesiveKg = v) }
        }
        NumField(stringResource(R.string.price_roll), p.roll, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(roll = v) }
        }
        NumField(stringResource(R.string.price_paint_l), p.paintL, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(paintL = v) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(stringResource(R.string.work_tile_m2), p.workTileM2, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(workTileM2 = v) }
        }
        NumField(stringResource(R.string.work_wall_m2), p.workWallM2, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(workWallM2 = v) }
        }
        NumField(stringResource(R.string.work_paint_m2), p.workPaintM2, cur, 0.0, 999999.0, 104.dp) { v ->
            vm.updatePrices { it.copy(workPaintM2 = v) }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.price_unit_note), color = Dim, fontSize = 10.sp)

    Spacer(Modifier.height(14.dp))
    val costs = vm.surfaceCosts()
    val visible = costs.filter { it.materials + it.work > 0 }
    visible.forEach { sc ->
        Line(
            surfaceTitle(sc.id) + " · " + finishTitle(sc.finish),
            money(sc.materials + sc.work, cur),
        )
    }
    // обрезки: площадь и деньги по цене плитки
    val tileAreaCm = vm.tile.widthMm * vm.tile.heightMm / 100.0
    var offcutCm2 = 0.0
    vm.layout.cutPieces.forEach { pc ->
        offcutCm2 += pc.count * (tileAreaCm - pc.aCm * pc.bCm).coerceAtLeast(0.0)
    }
    val offcutM2 = offcutCm2 / 10000.0
    val tileAreaM2p = vm.tile.widthMm * vm.tile.heightMm / 1_000_000.0
    val tileCostPerM2 = if (p.tilePc > 0) p.tilePc / tileAreaM2p else p.tileM2
    if (offcutM2 > 0.005) {
        Line(
            stringResource(R.string.offcut_area),
            String.format(Locale.getDefault(), "%.2f", offcutM2) + " " + stringResource(R.string.unit_m2),
        )
        if (tileCostPerM2 > 0) {
            Line(stringResource(R.string.offcut_cost), money(offcutM2 * tileCostPerM2, cur), Warn)
        }
    }

    val matSum = costs.sumOf { it.materials }
    val workSum = costs.sumOf { it.work }
    if (matSum + workSum > 0) {
        Spacer(Modifier.height(8.dp))
        Line(stringResource(R.string.materials_cost), money(matSum, cur))
        Line(stringResource(R.string.work_cost), money(workSum, cur))
        Line(stringResource(R.string.grand_total), money(matSum + workSum, cur), Acc2)
    } else {
        Text(stringResource(R.string.prices_hint), color = Dim, fontSize = 10.5.sp)
    }
}

@Composable
private fun TipsSection(vm: EditorViewModel) {
    val list = vm.suggestions
    val cur = vm.layout
    if (list == null) {
        Text(stringResource(R.string.suggest_note), color = Sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        IconChip(BaIcons.Magic, stringResource(R.string.suggest)) { vm.runSuggest() }
        return
    }
    Line(
        patternLabel(vm.pattern.type, vm.pattern.rotationDeg) + " · " + stringResource(R.string.current),
        "${cur.totalCount} · ${cur.cutCount} " + stringResource(R.string.cuts_short),
    )
    Spacer(Modifier.height(6.dp))
    list.forEach { s ->
        SuggestionCard(s, cur.totalCount, cur.cutCount) { vm.applySuggestion(s) }
        Spacer(Modifier.height(6.dp))
    }
    IconChip(BaIcons.Magic, stringResource(R.string.recalc)) { vm.runSuggest() }
}

@Composable
private fun SuggestionCard(
    s: LayoutSuggester.Suggestion,
    curTotal: Int,
    curCuts: Int,
    onUse: () -> Unit,
) {
    val dTotal = s.total - curTotal
    val dCuts = s.cuts - curCuts
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Panel2)
            .border(1.dp, LineC, RoundedCornerShape(11.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(patternLabel(s.type, s.rotationDeg), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${s.total} " + stringResource(R.string.pcs) + delta(dTotal),
                    color = if (dTotal <= 0) Good else Warn,
                    fontSize = 11.5.sp,
                )
                Text(
                    "${s.cuts} " + stringResource(R.string.cuts_short) + delta(dCuts),
                    color = if (dCuts <= 0) Good else Warn,
                    fontSize = 11.5.sp,
                )
            }
        }
        Chip(stringResource(R.string.use), selected = true, onClick = onUse)
    }
}

private fun delta(d: Int): String = when {
    d > 0 -> " (+$d)"
    d < 0 -> " ($d)"
    else -> ""
}

@Composable
private fun ProjectSection(vm: EditorViewModel) {
    Text(stringResource(R.string.autosave_note), color = Sub, fontSize = 10.5.sp)
    Spacer(Modifier.height(10.dp))
    var confirmNew by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.new_project)) { confirmNew = true }
        Chip(stringResource(R.string.reset_all), warn = true) { confirmReset = true }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Panel2,
            title = { Text(stringResource(R.string.reset_all), color = Txt) },
            text = {
                Text(stringResource(R.string.reset_all_confirm), color = Sub, fontSize = 12.5.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetPlacements()
                }) { Text(stringResource(R.string.apply), color = Acc2) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel), color = Sub)
                }
            },
        )
    }
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            containerColor = Panel2,
            title = { Text(stringResource(R.string.new_project), color = Txt) },
            text = { Text(stringResource(R.string.new_confirm), color = Sub, fontSize = 12.5.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNew = false
                    vm.newProject()
                }) { Text(stringResource(R.string.apply), color = Acc2) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) {
                    Text(stringResource(R.string.cancel), color = Sub)
                }
            },
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.projectName,
        onValueChange = { vm.projectName = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.default_name), color = Sub, fontSize = 13.sp) },
        label = { Text(stringResource(R.string.project_name), color = Sub, fontSize = 11.sp) },
        textStyle = TextStyle(fontSize = 13.sp, color = Txt),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Acc,
            unfocusedBorderColor = LineC,
            focusedTextColor = Txt,
            unfocusedTextColor = Txt,
            cursorColor = Acc,
            focusedContainerColor = Panel2,
            unfocusedContainerColor = Panel2,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    IconChip(BaIcons.Save, stringResource(R.string.save), selected = true) { vm.saveProject() }
    Spacer(Modifier.height(10.dp))
    vm.projects.forEach { p ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = Txt, fontSize = 13.sp, maxLines = 1)
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.savedAt)),
                    color = Sub,
                    fontSize = 10.5.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.open)) { vm.loadProject(p.name) }
                IconChip(BaIcons.Close, "", warn = true) { vm.deleteProject(p.name) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.credit),
        color = Sub,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
