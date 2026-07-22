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
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.baremodel.core.AnchorMode
import com.baremodel.core.ArtRect
import com.baremodel.core.DecorMode
import com.baremodel.core.LayoutSuggester
import com.baremodel.core.PatternType
import com.baremodel.core.polygonPerimeter
import java.text.DateFormat
import kotlin.math.abs
import java.util.Date
import java.util.Locale

// ---------- атомы ----------

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val border = if (warn) Warn else if (selected) Acc else LineC
    val bg = when {
        warn -> Warn.copy(alpha = 0.14f)
        selected -> Acc.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val fg = if (warn) Warn else if (selected) Acc2 else Sub
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
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
fun PanelHost(vm: EditorViewModel) {
    var section by rememberSaveable { mutableStateOf(0) }
    val titles = listOf(
        R.string.sec_tile, R.string.sec_pattern, R.string.sec_decor, R.string.sec_room,
        R.string.sec_calc, R.string.sec_tips, R.string.sec_project,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            titles.forEachIndexed { i, res ->
                Chip(stringResource(res), selected = section == i) { section = i }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            when (section) {
                0 -> TileSection(vm)
                1 -> PatternSection(vm)
                2 -> DecorSection(vm)
                3 -> RoomSection(vm)
                4 -> CalcSection(vm)
                5 -> TipsSection(vm)
                else -> ProjectSection(vm)
            }
        }
    }
}

// ---------- секции ----------

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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadTileImage(context, uri)
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PRESETS.forEach { (w, h) ->
            val label = "${(w / 10).toInt()}×${(h / 10).toInt()}"
            Chip(label, vm.tile.widthMm == w && vm.tile.heightMm == h) {
                vm.setTileWidth(w)
                vm.setTileHeight(h)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(stringResource(R.string.width), vm.tile.widthMm, stringResource(R.string.unit_mm), 30.0, 2000.0) {
            vm.setTileWidth(it)
        }
        NumField(stringResource(R.string.length), vm.tile.heightMm, stringResource(R.string.unit_mm), 30.0, 2000.0) {
            vm.setTileHeight(it)
        }
        NumField(stringResource(R.string.grout), vm.tile.groutMm, stringResource(R.string.unit_mm), 0.0, 30.0) {
            vm.setGrout(it)
        }
    }
    Spacer(Modifier.height(10.dp))
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
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.variation), vm.variation) { vm.toggleVariation() }
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

@Composable
private fun DecorSection(vm: EditorViewModel) {
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
    }

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
        if (img == null) {
            Text(
                stringResource(R.string.no_photo),
                color = Dim,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun RoomSection(vm: EditorViewModel) {
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
}

@Composable
private fun CalcSection(vm: EditorViewModel) {
    val l = vm.layout
    Line(stringResource(R.string.area), String.format(Locale.getDefault(), "%.2f", l.areaM2) + " " + stringResource(R.string.unit_m2))
    Line(
        stringResource(R.string.perimeter),
        String.format(Locale.getDefault(), "%.2f", polygonPerimeter(vm.room.points)) + " " + stringResource(R.string.unit_m),
    )
    Line(stringResource(R.string.full_tiles), l.fullCount.toString())
    Line(stringResource(R.string.cut_tiles), l.cutCount.toString(), Warn)
    Line(stringResource(R.string.total_tiles), l.totalCount.toString())
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
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.cuts_by_walls), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
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

    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.disclaimer), color = Sub, fontSize = 10.5.sp)
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
