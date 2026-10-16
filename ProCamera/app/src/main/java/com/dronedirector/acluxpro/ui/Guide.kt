package com.dronedirector.acluxpro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * KEEP UP TO DATE: whenever a feature is added, changed or removed, update these sections,
 * all three variants below, GUIDE.md, FEATURES.md and the "Unreleased" part of CHANGELOG.md in the same change.
 */
enum class GuideTopic { CLOUD, PHONE_AI, TIPS, GENERAL }

/** Which part of the app the guide was opened from; each gets only the parts that apply to it. */
enum class GuideVariant(val title: String) { CAPTURE("Capture guide"), PHOTO("Photo editor guide"), VIDEO("Video editor guide") }

private class GuideSection(val topic: GuideTopic, val title: String, val icon: ImageVector, val intro: String?, val steps: List<String>, val numbered: Boolean)

private fun cloudSection(v: GuideVariant): GuideSection {
    val what = when (v) {
        GuideVariant.CAPTURE -> "AI upscale (AI 24 / 36 / 48 MP) uses Topaz Labs' upscaler in the cloud."
        GuideVariant.PHOTO -> "AI noise reduction on export uses Topaz Denoise in the cloud."
        GuideVariant.VIDEO -> "AI noise reduction on export uses Topaz Nyx, made for video — minutes instead of up to an hour on the phone."
    }
    val toggle = when (v) {
        GuideVariant.CAPTURE, GuideVariant.PHOTO -> "Turn on \"Photos in the cloud\"" + if (v == GuideVariant.PHOTO) " and pick the noise strength (Normal, Strong, Extreme or Max)." else "."
        GuideVariant.VIDEO -> "Turn on \"Video in the cloud\" and pick the model — Nyx Fast is quickest and cheapest (about \$0.10 per 10 s of 1080p)."
    }
    return GuideSection(
        GuideTopic.CLOUD, "Cloud AI: add your API key", Icons.Filled.Cloud,
        "Optional: runs Topaz Labs' professional AI on fast servers through your own fal.ai account. $what Without it, the AI runs on the phone.",
        listOf(
            "Open fal.ai (tap \"Open fal.ai keys\" below) and sign up or log in.",
            "Add credit to your fal.ai account (Billing). You pay fal.ai per use.",
            "Go to Dashboard → API Keys → Add key, name it (e.g. \"AcluxPro\") and create it.",
            "Copy the key — it's shown only once.",
            "Back here, in this settings menu → CLOUD AI → \"Add your fal.ai key\".",
            "Paste the key and tap SAVE KEY. It's stored encrypted on this phone and only sent to fal.ai.",
            toggle,
            "Keep \"Wi-Fi only\" on to avoid using mobile data.",
            "The AI badge shows upload, processing and download; STOP cancels. If the cloud isn't available, the on-phone AI is used and you're told why.",
        ),
        numbered = true,
    )
}

private fun sectionsFor(v: GuideVariant): List<GuideSection> = when (v) {
    GuideVariant.CAPTURE -> listOf(
        cloudSection(v),
        GuideSection(GuideTopic.PHONE_AI, "AI upscale", Icons.Filled.AutoAwesome, null, listOf(
            "Settings → AI UPSCALE → turn on \"AI upscale sizes\".",
            "Pick AI 24 / 36 / 48 MP under SIZE (quick controls or Settings → Photo size).",
            "Shoot as usual — the bigger copy is made in the background and saved next to the original.",
            "Standard is faster; High keeps more real detail. Choose whether to keep the original.",
            "Tap STOP on the AI badge to cancel.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "Capture tips", Icons.Filled.CameraAlt, null, listOf(
            "Use the volume keys as a shutter / record button.",
            "PRO and PRO VIDEO give manual ISO, shutter, focus, white balance and exposure.",
            "Zebras, false colour and focus peaking help judge exposure and focus.",
            "H.265 records smaller files at the same quality; H.264 plays everywhere.",
            "D-Log / D-Log M / Flat keep more dynamic range — grade them later in the Video editor.",
            "Clean feed hides the controls for screen mirroring or HDMI.",
            "In LIVE, add your stream key once per platform; choose H.264 if your platform doesn't accept H.265.",
            "Settings → PHOTO SIZE shows what your phone's sensor allows apps to use.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "Change the app colour under APP COLOUR.",
            "\"Always start in Capture\" opens the app straight into the camera.",
            "Choose where photos and videos are saved under SAVE PHOTOS & VIDEOS TO.",
        ), numbered = false),
    )
    GuideVariant.PHOTO -> listOf(
        cloudSection(v),
        GuideSection(GuideTopic.PHONE_AI, "AI noise reduction", Icons.Filled.AutoAwesome, null, listOf(
            "Detail → AI NOISE REDUCTION → move Strength above Off.",
            "The preview is cleaned once by the AI; after that the slider works instantly.",
            "Exporting cleans the full-size photo (on the phone, or in the cloud if Cloud AI is on).",
            "Tap STOP on the AI badge to cancel.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "Photo editor tips", Icons.Filled.Photo, null, listOf(
            "Hold your finger on the photo to see the original.",
            "Sliders update live while you drag.",
            "COPY SETTINGS / PASTE SETTINGS moves a look to another photo — or to a video clip.",
            "SAVE keeps the edit as a project; LOAD opens it again later.",
            "Hold the divider between the photo and the controls, then drag to resize. Double-tap to reset.",
            "Undo / redo are in the top bar; the histogram toggles with the chart icon.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "\"Remember watermark\" adds your watermark to every new photo automatically.",
            "SAVE LOCATION chooses where exported photos go.",
            "Change the app colour under APP COLOUR.",
            "\"Always start in Photo editor\" opens the app straight into this editor.",
        ), numbered = false),
    )
    GuideVariant.VIDEO -> listOf(
        cloudSection(v),
        GuideSection(GuideTopic.PHONE_AI, "AI noise reduction", Icons.Filled.AutoAwesome, null, listOf(
            "Color → Detail → AI NOISE REDUCTION → move Strength above Off.",
            "It's applied when exporting; the preview shows the normal noise reduction.",
            "On the phone it can take several minutes up to about an hour (tap ⓘ next to it); Cloud AI does it in minutes.",
            "Tap STOP on the AI badge to cancel.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "Video editor tips", Icons.Filled.Movie, null, listOf(
            "The timeline starts at the left edge: drag one finger (or tap the ruler) to move the playhead; two fingers pinch to zoom and slide to scroll. The scissors under the player split at the playhead.",
            "Tap a cut on the timeline to choose one of 34 transitions.",
            "Deliver shows the estimated file size; it updates as you change resolution, frame rate, bitrate and audio.",
            "The buttons at the left of each timeline row mute the video track or an audio track, hide a layer, or lock the row so it can't be moved, trimmed, split or deleted.",
            "Deliver → Audio only saves just the sound of your edit as an .m4a file.",
            "Undo and redo sit next to the scissors under the player — they step back and forward through clip, layer and audio changes.",
            "Give any layer its own name (Layers → selected layer → name) — handy for telling watermarks apart; the watermark text on the video is a separate field.",
            "Hold a layer or audio bar on the timeline until it buzzes, then drag: left / right moves it in time, up / down changes the layer order.",
            "Hold the start or end edge of a layer or audio bar, then slide to shorten or extend it.",
            "Media → Audio adds music and sound files to the media pool; ADD TO TIMELINE places them at the playhead.",
            "Edit → Crop & zoom: pinch, drag and twist on the player; Ken Burns animates start → end.",
            "COPY / PASTE SETTINGS copies a clip's grade, audio and more to other clips (or all clips).",
            "Heavy clips (HDR, 10-bit, 4K) get a light preview copy for smooth playback; exports use the originals.",
            "If playback stalls, turn on \"Show playback info\" here and share a screenshot.",
            "Hold a divider to resize the player, timeline or controls; double-tap to reset.",
            "SAVE / LOAD projects; \"Save & leave\" when exiting.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "\"Remember watermark\" adds your watermark to every new project automatically.",
            "SAVE LOCATION chooses where exported videos go.",
            "Change the app colour under APP COLOUR.",
            "\"Always start in Video editor\" opens the app straight into this editor.",
        ), numbered = false),
    )
}

/** In-app guide: how to add the Cloud AI key, plus tips for every part of the app. */
@Composable
fun GuideDialog(variant: GuideVariant, open: GuideTopic? = null, onDismiss: () -> Unit) {
    val expanded = remember { mutableStateListOf<GuideTopic>().apply { add(open ?: GuideTopic.CLOUD) } }
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0C0F)).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(variant.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("GET MORE OUT OF ACLUXPRO", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp, letterSpacing = 2.sp))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    sectionsFor(variant).forEach { s ->
                        val isOpen = s.topic in expanded
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF15171C))
                                .border(1.dp, if (isOpen) Pro.Amber.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { if (isOpen) expanded.remove(s.topic) else expanded.add(s.topic) }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(s.icon, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(22.dp))
                                }
                                Text(s.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = Pro.Dim)
                            }
                            AnimatedVisibility(isOpen) {
                                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                    s.intro?.let { Text(it, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                                    s.steps.forEachIndexed { i, step ->
                                        Row(Modifier.padding(vertical = 4.dp)) {
                                            Box(
                                                Modifier.size(24.dp).clip(CircleShape).background(if (s.numbered) Pro.Amber else Color.Transparent),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(if (s.numbered) "${i + 1}" else "•", color = if (s.numbered) Color.Black else Pro.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(step, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                        }
                                    }
                                    if (s.topic == GuideTopic.CLOUD) Row(Modifier.padding(top = 8.dp)) {
                                        Text(
                                            "OPEN FAL.AI KEYS", style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
                                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { uri.openUri("https://fal.ai/dashboard/keys") }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                        Text(
                                            "BILLING", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).border(1.dp, Pro.Amber, RoundedCornerShape(50))
                                                .clickable { uri.openUri("https://fal.ai/dashboard/billing") }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).padding(bottom = 24.dp))
                }
            }
        }
    }
}
