package com.zynelabs.nhlauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NH look: near-black "terminal glass", chamfered (diagonal-cut) corners, hairline frames,
 * monospace capital labels, one selectable neon accent. No rounded cards, no amber.
 */
object NH {
    var accentIdx by mutableIntStateOf(0)
    var themeIdx by mutableIntStateOf(0)
    var panelIdx by mutableIntStateOf(1)
    private val th: ThemeDef get() = Themes.all[themeIdx.coerceIn(0, Themes.all.lastIndex)]
    val Bg: Color get() = th.bg
    val Panel: Color get() = th.panel
    val Panel2: Color get() = th.panel2
    val Line: Color get() = th.line
    val Text: Color get() = th.text
    val Sub: Color get() = th.sub
    /** Panel fill as drawn on screen: solid, glass or nearly clear (so the wallpaper shows through). */
    val PanelFill: Color get() = th.panel.copy(alpha = when (panelIdx) { 0 -> 1f; 1 -> 0.72f; else -> 0.38f })
    val Green = Color(0xFF2EF28C)
    val Red = Color(0xFFFF4D5E)
    val Acc: Color get() = Accent.all[accentIdx.coerceIn(0, Accent.all.lastIndex)].color
    val OnAcc: Color get() = Accent.all[accentIdx.coerceIn(0, Accent.all.lastIndex)].on
    val Mono = FontFamily(
        Font(R.font.dejavu_sans_mono, FontWeight.Normal),
        Font(R.font.dejavu_sans_mono_bold, FontWeight.Bold),
    )
}

fun chamfer(c: Dp = 8.dp): Shape = CutCornerShape(topStart = 0.dp, topEnd = c, bottomEnd = 0.dp, bottomStart = c)
fun chamferAll(c: Dp = 6.dp): Shape = CutCornerShape(c)

@Composable
fun Txt(
    text: String, size: Float = 13f, color: Color = NH.Text, bold: Boolean = false, letter: Float = 0f,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE,
) {
    androidx.compose.foundation.text.BasicText(
        text, modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontFamily = NH.Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, letterSpacing = letter.sp),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** A hairline-framed panel with a diagonal cut corner. */
@Composable
fun Panel(
    modifier: Modifier = Modifier, accent: Boolean = false, onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = chamfer(10.dp)
    var m = modifier.fillMaxWidth().clip(shape).background(NH.PanelFill).border(BorderStroke(1.dp, if (accent) NH.Acc else NH.Line), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m) { content() }
}

@Composable
fun HudButton(label: String, modifier: Modifier = Modifier, primary: Boolean = true, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = chamfer(9.dp)
    val bg = when { !enabled -> NH.Panel2; danger -> Color(0x22FF4D5E); primary -> NH.Acc; else -> NH.Panel2 }
    val fg = when { !enabled -> NH.Sub; danger -> NH.Red; primary -> NH.OnAcc; else -> NH.Text }
    val bd = when { danger -> NH.Red; primary && enabled -> NH.Acc; else -> NH.Line }
    Box(
        modifier.height(44.dp).clip(shape).background(bg).border(BorderStroke(1.dp, bd), shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label.uppercase(), 12.5f, fg, bold = true, letter = 1.2f) }
}

@Composable
fun SectionTitle(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("//", 12f, NH.Acc, bold = true)
        Spacer(Modifier.width(6.dp))
        Txt(text.uppercase(), 11.5f, NH.Sub, bold = true, letter = 1.6f)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(NH.Line))
    }
}

/** Segmented progress bar (40 little blocks) instead of a smooth rounded bar. */
@Composable
fun SegBar(progress: Float, modifier: Modifier = Modifier, segments: Int = 36) {
    val acc = NH.Acc
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = if (progress < 0f) 0 else (progress.coerceIn(0f, 1f) * segments).toInt()
        for (i in 0 until segments) {
            val on = i < lit
            drawRect(if (on) acc else Color(0xFF14232C), Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

@Composable
fun HudSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val shape = chamferAll(4.dp)
    Box(
        Modifier.width(46.dp).height(24.dp).clip(shape).background(if (on) NH.Acc.copy(alpha = 0.22f) else NH.Panel2)
            .border(BorderStroke(1.dp, if (on) NH.Acc else NH.Line), shape).clickable { onChange(!on) },
    ) {
        Box(
            Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(width = 18.dp, height = 16.dp)
                .background(if (on) NH.Acc else NH.Sub),
        )
    }
}

@Composable
fun SettingRow(title: String, sub: String? = null, trailing: @Composable () -> Unit = {}, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, 13.5f, NH.Text, bold = true)
            if (sub != null) { Spacer(Modifier.height(3.dp)); Txt(sub, 11.5f, NH.Sub) }
        }
        trailing()
    }
}

/** Thin 1dp divider. */
@Composable
fun HLine() { Box(Modifier.fillMaxWidth().height(1.dp).background(NH.Line)) }

/** Simple capital label shown in a small box, e.g. "ARM64". */
@Composable
fun Tag(text: String, color: Color = NH.Sub) {
    Box(Modifier.border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), chamferAll(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Txt(text.uppercase(), 9.5f, color, bold = true, letter = 1f)
    }
}

@Composable
fun Scanlines(modifier: Modifier = Modifier) {
    Canvas(modifier.then(Modifier.fillMaxWidth())) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(Color(0x0AFFFFFF), Offset(0f, y), Size(size.width, 1f))
            y += step
        }
    }
}


/** A row of selectable chips (several per row). */
@Composable
fun Chips(options: List<String>, selected: Int, perRow: Int = 3, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.withIndex().chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, label) in row) {
                    val on = i == selected
                    val shape = chamferAll(5.dp)
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(shape).background(if (on) NH.Acc.copy(alpha = 0.22f) else NH.Panel2)
                            .border(BorderStroke(1.dp, if (on) NH.Acc else NH.Line), shape).clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) { Txt(label.uppercase(), 10.5f, if (on) NH.Acc else NH.Text, bold = true, letter = 0.8f, maxLines = 1) }
                }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
