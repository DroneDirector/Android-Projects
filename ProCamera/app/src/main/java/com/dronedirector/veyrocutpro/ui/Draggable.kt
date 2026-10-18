package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Saved overlay positions, as fractions of the viewfinder so they survive rotation and resizing. */
object OverlayLayout {
    private const val PREFS = "procam_layout"

    fun load(context: Context, key: String): Pair<Float, Float>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("${key}_x")) return null
        return p.getFloat("${key}_x", 0f) to p.getFloat("${key}_y", 0f)
    }

    fun save(context: Context, key: String, fx: Float, fy: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("${key}_x", fx).putFloat("${key}_y", fy).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * Places [content] in the viewfinder at its saved position (or [defaultAlignment] the first time)
 * and lets the user drag it anywhere inside. While dragging it lifts slightly and gets an amber
 * outline. Positions are kept per [key] and per screen orientation.
 */
@Composable
fun DraggableOverlay(
    key: String,
    defaultAlignment: Alignment,
    version: Int,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val storeKey = key + if (landscape) "_land" else "_port"
        val areaW = with(density) { maxWidth.toPx() }
        val areaH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 8.dp.toPx() }

        var childSize by remember { mutableStateOf(IntSize.Zero) }
        val saved = remember(storeKey, version) { OverlayLayout.load(context, storeKey) }
        var x by remember(storeKey, version) { mutableFloatStateOf(Float.NaN) }
        var y by remember(storeKey, version) { mutableFloatStateOf(Float.NaN) }
        var dragging by remember { mutableStateOf(false) }

        // Resolve the starting position once the overlay's size is known.
        if (x.isNaN() && childSize != IntSize.Zero) {
            if (saved != null) {
                x = saved.first * (areaW - childSize.width)
                y = saved.second * (areaH - childSize.height)
            } else {
                val inner = IntSize((areaW - 2 * margin).roundToInt(), (areaH - 2 * margin).roundToInt())
                val p = defaultAlignment.align(childSize, inner, dir)
                x = p.x + margin
                y = p.y + margin
            }
        }
        val maxX = (areaW - childSize.width).coerceAtLeast(0f)
        val maxY = (areaH - childSize.height).coerceAtLeast(0f)
        val lift by animateFloatAsState(if (dragging) 1.06f else 1f, label = "lift")

        Box(
            Modifier
                .offset { IntOffset((if (x.isNaN()) -10_000f else x.coerceIn(0f, maxX)).roundToInt(), (if (y.isNaN()) 0f else y.coerceIn(0f, maxY)).roundToInt()) }
                .onSizeChanged { childSize = it }
                .scale(lift)
                .then(if (dragging) Modifier.border(1.dp, Pro.Amber, RoundedCornerShape(8.dp)) else Modifier)
                .pointerInput(storeKey, version) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val mx = (areaW - childSize.width).coerceAtLeast(1f)
                            val my = (areaH - childSize.height).coerceAtLeast(1f)
                            OverlayLayout.save(context, storeKey, (x.coerceIn(0f, mx) / mx), (y.coerceIn(0f, my) / my))
                        },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        val limX = (areaW - childSize.width).coerceAtLeast(0f)
                        val limY = (areaH - childSize.height).coerceAtLeast(0f)
                        x = (x + drag.x).coerceIn(0f, limX)
                        y = (y + drag.y).coerceIn(0f, limY)
                    }
                },
        ) { content() }
    }
}
