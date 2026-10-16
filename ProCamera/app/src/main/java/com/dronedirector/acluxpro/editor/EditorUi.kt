package com.dronedirector.acluxpro.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.acluxpro.camera.Filter
import com.dronedirector.acluxpro.ui.Pro
import com.dronedirector.acluxpro.ui.hintedScroll
import kotlin.math.roundToInt

/** Same look as the camera: dark panels, accent highlights. */
object Ed {
    val Bg = Color(0xFF07080A)
    val Panel = Color(0xFF111317)
    val Raised = Color(0xFF1A1D22)
}

@Composable
fun SectionTitle(text: String, onReset: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.weight(1f))
        if (onReset != null) {
            Text(
                "RESET", style = Pro.Label.copy(fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Labelled slider; tap the label to reset. */
@Composable
fun EdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    default: Float = 0f,
    format: (Float) -> String = { "%+d".format((it * 100).roundToInt()) },
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).clickable { onChange(default) })
            Text(format(value), style = Pro.Value.copy(color = if (value != default) Pro.Amber else Pro.Dim, fontSize = 12.sp))
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.14f)),
        )
    }
}

fun pct(v: Float) = "${(v * 100).roundToInt()}%"
fun ev(v: Float) = "%+.2f EV".format(v)

/** Colour wheel as hue + amount + brightness sliders, with a swatch. */
@Composable
fun WheelControl(title: String, w: Wheel, showLuma: Boolean = true, onChange: (Wheel) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (r, g, b) = hueRgb(w.hue)
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color(r, g, b).copy(alpha = 0.3f + w.amount * 0.7f))))
                    .border(1.dp, Pro.Line, CircleShape)
            )
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text(
                "RESET", style = Pro.Label.copy(fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange(Wheel(w.hue)) }.padding(6.dp),
            )
        }
        HueBar(w.hue) { onChange(w.copy(hue = it)) }
        EdSlider("Amount", w.amount, 0f..1f, format = ::pct) { onChange(w.copy(amount = it)) }
        if (showLuma) EdSlider("Brightness", w.luma) { onChange(w.copy(luma = it)) }
    }
}

@Composable
fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            drawRect(Brush.horizontalGradient((0..6).map { i -> hueRgb(i * 60f).let { (r, g, b) -> Color(r, g, b) } }))
        }
        Slider(
            value = hue, onValueChange = onChange, valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
        )
    }
}

@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Text(
                o, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Pro.Amber else Color.White.copy(alpha = 0.08f))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun LookStrip(selected: Filter, onPick: (Filter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Filter.entries.forEach { f ->
            val sel = f == selected
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(f) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color(f.swatch))
                        .border(if (sel) 3.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, CircleShape)
                )
                Text(if (f == Filter.NONE) "None" else f.label, color = if (sel) Pro.Amber else Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** RGB + luma histogram (scope). */
@Composable
fun Scope(hist: Array<FloatArray>?, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f))) {
        val h = hist ?: return@Canvas
        val colors = listOf(Color(0xCCFF4D4D), Color(0xCC4DFF6A), Color(0xCC4D8BFF), Color(0x99FFFFFF))
        for (ch in 0..3) {
            val path = Path()
            val bins = h[ch]
            path.moveTo(0f, size.height)
            bins.forEachIndexed { i, v -> path.lineTo(i / 63f * size.width, size.height - v * size.height * 0.95f) }
            path.lineTo(size.width, size.height)
            path.close()
            if (ch < 3) drawPath(path, colors[ch].copy(alpha = 0.35f))
            drawPath(path, colors[ch], style = Stroke(1.2f))
        }
        // Clipping guides
        drawLine(Color.White.copy(alpha = 0.15f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
    }
}

/** Settings for an editor: currently whether the app should open straight into it. */
@Composable
fun EditorSettingsDialog(
    editorName: String,
    screen: com.dronedirector.acluxpro.ui.AppScreen,
    extra: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var always by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.dronedirector.acluxpro.ui.StartScreen.get(ctx) == screen)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("$editorName settings", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("STARTUP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Always start in $editorName", color = Color.White, fontSize = 15.sp)
                        Text("Open AcluxPro straight into the $editorName. Turn off to show the start screen again.", color = Pro.Dim, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = always,
                        onCheckedChange = {
                            always = it
                            com.dronedirector.acluxpro.ui.StartScreen.set(ctx, if (it) screen else com.dronedirector.acluxpro.ui.AppScreen.HOME)
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
                    )
                }
                val video = screen == com.dronedirector.acluxpro.ui.AppScreen.VIDEO_EDITOR
                var folder by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SaveFolder.get(ctx, video)) }
                val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
                    if (tree != null) { SaveFolder.set(ctx, video, tree); folder = tree }
                }
                Text("SAVE LOCATION", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
                Text(
                    if (video) "Where exported videos are saved." else "Where edited photos are saved.",
                    color = Pro.Dim, fontSize = 12.sp,
                )
                Text(
                    SaveFolder.label(folder), color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp),
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "CHOOSE FOLDER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { pick.launch(folder) }.padding(6.dp),
                    )
                    if (folder != null) Text(
                        "USE DEFAULT", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { SaveFolder.set(ctx, video, null); folder = null }.padding(6.dp),
                    )
                }
                if (screen != com.dronedirector.acluxpro.ui.AppScreen.EDITOR) {
                    val v = screen == com.dronedirector.acluxpro.ui.AppScreen.VIDEO_EDITOR
                    Text("WATERMARK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Remember watermark", color = Color.White, fontSize = 15.sp)
                            Text(
                                "Keeps your last watermark (text or logo, font, colour, size, position, opacity) and adds it to every new " +
                                    (if (v) "video" else "photo") + " automatically.",
                                color = Pro.Dim, fontSize = 12.sp,
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = WatermarkMemory.isOn(ctx, v),
                            onCheckedChange = { WatermarkMemory.setOn(ctx, v, it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
                        )
                    }
                }
                Text("APP COLOUR", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                com.dronedirector.acluxpro.ui.AccentPicker()
                val gv = if (screen == com.dronedirector.acluxpro.ui.AppScreen.VIDEO_EDITOR) com.dronedirector.acluxpro.ui.GuideVariant.VIDEO else com.dronedirector.acluxpro.ui.GuideVariant.PHOTO
                com.dronedirector.acluxpro.ui.CloudAiSettings(gv)
                com.dronedirector.acluxpro.ui.GuideButton(gv)
                if (extra != null) extra()
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
    )
}
