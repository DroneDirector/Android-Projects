package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fold-out sections for the editors' tool panels. Inside a [FoldColumn] every [SectionHeader] becomes a
 * card (title, a line saying what's inside, arrow) that opens or closes everything below it up to the
 * next header. The "what's inside" line is collected from the controls' own labels (sliders, switch and
 * option titles), so it always matches the section — also for features added later.
 */
object Fold {
    private val open = mutableStateMapOf<String, Boolean>()
    fun isOpen(key: String) = open[key] ?: false
    fun toggle(key: String) { open[key] = !isOpen(key) }
}

/** Collects the labels of the controls under each header while a [FoldColumn] composes. */
class FoldCollector {
    var scope = "fold"
    internal val summaries = mutableStateMapOf<String, String>()
    private val pending = LinkedHashMap<String, MutableList<String>>()
    private var current: String? = null
    private var collecting = false

    internal fun startPass() { pending.clear(); current = null; collecting = true }
    internal fun endPass() { collecting = false; current = null }
    internal fun begin(key: String) { if (collecting) { current = key; pending.getOrPut(key) { mutableListOf() } } }

    /** A control's label (already translated). */
    fun collect(label: String) {
        if (!collecting) return
        val k = current ?: return
        val l = pending[k] ?: return
        val t = label.trim().trimEnd(':')
        if (t.length < 2 || t.length > 34 || l.size >= 7 || l.any { it.equals(t, true) }) return
        // Values change while a slider moves — never let them into the summary (it would jump).
        if (t.endsWith('.') || !t.any { it.isLetter() } || t.any { it.isDigit() } || t.contains('%') || t.contains('±')) return
        l += t
    }

    internal fun publish() {
        for ((k, v) in pending) {
            // An open section keeps the summary it had, so nothing moves while its controls are used.
            if (Fold.isOpen(k) && summaries.containsKey(k)) continue
            val s = v.joinToString(", ") { readable(it) }
            if (summaries[k] != s) summaries[k] = s
        }
    }
}

val LocalFold = compositionLocalOf<FoldCollector?> { null }

private data class FoldId(val key: String)

/** "COLOUR GRADING" → "Colour grading" (keeps short codes like LUT, HSL, AI, RAW, 3D). */
internal fun readable(s: String): String {
    if (s.any { it.isLowerCase() }) return s
    val keep = setOf("AI", "LUT", "LUTS", "HSL", "RGB", "RAW", "DNG", "HDR", "EQ", "XMP", "OTIO", "EDL", "TIFF", "JPEG", "PNG", "ICC", "ND", "UV",
        "SRT", "VTT", "GIF", "4K", "HLG", "PQ", "LUFS", "MP4", "MOV", "AVIF", "WEBP", "HEIC", "CMYK", "B&W", "TV", "ISO", "EV", "WB", "FX", "SFX", "C1", "C2", "C3", "FPS", "AB")
    val words = s.split(' ')
    return words.mapIndexed { i, w ->
        val core = w.trim('(', ')', ',', '·', ':', '—', '-', '/', '…', '.')
        when {
            core.uppercase() in keep || core.any { it.isDigit() } -> w
            i == 0 -> w.lowercase().replaceFirstChar { it.uppercase() }
            else -> w.lowercase()
        }
    }.joinToString(" ")
}

/**
 * A section header: a fold-out card inside a [FoldColumn], the plain amber title elsewhere.
 * [onReset] adds a small RESET on the right.
 */
@Composable
fun SectionHeader(text: String, onReset: (() -> Unit)? = null) {
    val c = LocalFold.current
    if (c == null) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.weight(1f))
            if (onReset != null) Text("RESET", style = Pro.Label.copy(fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        return
    }
    val key = c.scope + "|" + text
    c.begin(key)
    val open = Fold.isOpen(key)
    val summary = c.summaries[key].orEmpty()
    Row(
        Modifier.layoutId(FoldId(key)).fillMaxWidth().padding(top = 6.dp, bottom = if (open) 4.dp else 0.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))
            .clickable { Fold.toggle(key) }.padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(readable(I18n.tr(text)), color = if (open) Pro.Amber else Color.White, fontSize = 15.sp)
            // Fixed size: always two lines' room, so the header's height never changes.
            if (summary.isNotEmpty()) androidx.compose.material3.Text(summary, color = Pro.Dim, fontSize = 12.sp, maxLines = 2, minLines = 2, lineHeight = 16.sp,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (onReset != null && open) Text("RESET", style = Pro.Label.copy(fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 6.dp))
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = Pro.Dim)
    }
}

private class HAlign(val a: Alignment.Horizontal) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@HAlign
}

private object FoldScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this.then(HAlign(alignment))
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/**
 * Drop-in for Column in the tool panels: sections under a closed [SectionHeader] are left out of the
 * layout until the header is opened. [scope] keeps the open / closed state apart per editor.
 */
@Composable
fun FoldColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scope: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val parent = LocalFold.current
    val c = remember { FoldCollector() }
    c.scope = scope ?: parent?.scope ?: "fold"
    CompositionLocalProvider(LocalFold provides c) {
        Layout(
            content = {
                c.startPass(); FoldScope.content(); c.endPass()
                SideEffect { c.publish() }
            },
            modifier = modifier,
        ) { measurables, constraints ->
            val gap = verticalArrangement.spacing.roundToPx()
            val cc = constraints.copy(minWidth = 0, minHeight = 0)
            var hidden = false
            val placed = ArrayList<Placeable?>(measurables.size)
            for (m in measurables) {
                val id = m.layoutId as? FoldId
                if (id != null) hidden = !Fold.isOpen(id.key)
                placed += if (id == null && hidden) null else m.measure(cc)
            }
            val visible = placed.filterNotNull()
            val w = (visible.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
            val h = (visible.sumOf { it.height } + gap * (visible.size - 1).coerceAtLeast(0)).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(w, h) {
                var y = 0
                placed.forEachIndexed { i, p ->
                    if (p == null) return@forEachIndexed
                    val a = (measurables[i].parentData as? HAlign)?.a ?: horizontalAlignment
                    p.placeRelative(a.align(p.width, w, LayoutDirection.Ltr), y)
                    y += p.height + gap
                }
            }
        }
    }
}
