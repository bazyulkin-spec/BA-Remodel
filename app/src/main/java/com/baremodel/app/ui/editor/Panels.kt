package com.baremodel.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.Good
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.LayoutSuggester
import com.baremodel.core.PatternType
import com.baremodel.core.polygonPerimeter
import java.text.DateFormat
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
        R.string.sec_tile, R.string.sec_pattern, R.string.sec_room,
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
                .heightIn(max = 270.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            when (section) {
                0 -> TileSection(vm)
                1 -> PatternSection(vm)
                2 -> RoomSection(vm)
                3 -> CalcSection(vm)
                4 -> TipsSection(vm)
                else -> ProjectSection(vm)
            }
        }
    }
}

// ---------- секции ----------

@Composable
private fun PatternSection(vm: EditorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.pat_grid), vm.pattern.type == PatternType.GRID) {
            vm.setPatternType(PatternType.GRID)
        }
        Chip(stringResource(R.string.pat_half), vm.pattern.type == PatternType.HALF) {
            vm.setPatternType(PatternType.HALF)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.pat_third), vm.pattern.type == PatternType.THIRD) {
            vm.setPatternType(PatternType.THIRD)
        }
        Chip(stringResource(R.string.pat_herring), vm.pattern.type == PatternType.HERRINGBONE) {
            vm.setPatternType(PatternType.HERRINGBONE)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.rotation) + ": ${vm.pattern.rotationDeg.toInt()}°",
        color = Sub,
        fontSize = 11.5.sp,
    )
    Slider(
        value = vm.pattern.rotationDeg.toFloat(),
        onValueChange = { vm.setRotation(it.toDouble()) },
        valueRange = 0f..90f,
        colors = SliderDefaults.colors(
            thumbColor = Acc,
            activeTrackColor = Acc,
            inactiveTrackColor = LineC,
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
        Chip(
            "📷 " + stringResource(if (vm.tileImage != null) R.string.photo_on else R.string.photo),
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
        Chip(stringResource(R.string.add_cutout)) { vm.addCutout() }
    }
    val sel = vm.selection
    if (sel is Selection.Vertex && vm.room.points.size > 3) {
        Spacer(Modifier.height(10.dp))
        Chip("✕ " + stringResource(R.string.del_point), warn = true) { vm.deleteSelectedVertex() }
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
                Chip("✕ " + stringResource(R.string.del_cutout), warn = true) { vm.deleteSelectedCutout() }
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
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.disclaimer), color = Sub, fontSize = 10.5.sp)
}

@Composable
private fun TipsSection(vm: EditorViewModel) {
    val list = vm.suggestions
    val cur = vm.layout
    if (list == null) {
        Text(stringResource(R.string.suggest_note), color = Sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Chip("✨ " + stringResource(R.string.suggest)) { vm.runSuggest() }
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
    Chip("✨ " + stringResource(R.string.recalc)) { vm.runSuggest() }
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
    Chip("💾 " + stringResource(R.string.save), selected = true) { vm.saveProject() }
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
                Chip("✕", warn = true) { vm.deleteProject(p.name) }
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
