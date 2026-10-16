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
 * GUIDE.md, FEATURES.md and the "Unreleased" part of CHANGELOG.md in the same change.
 */
enum class GuideTopic { CLOUD, PHONE_AI, CAPTURE, PHOTO, VIDEO, GENERAL }

private class GuideSection(val topic: GuideTopic, val title: String, val icon: ImageVector, val intro: String?, val steps: List<String>, val numbered: Boolean)

private val SECTIONS = listOf(
    GuideSection(
        GuideTopic.CLOUD, "Cloud AI: add your API key", Icons.Filled.Cloud,
        "Cloud AI runs Topaz Labs' professional AI on fast servers through your own fal.ai account. It's optional — without it the AI runs on the phone.",
        listOf(
            "Open fal.ai in your browser (or tap \"Open fal.ai keys\" below) and sign up or log in.",
            "Add credit to your fal.ai account (Billing). You pay fal.ai per use — e.g. about \$0.10 per 10 s of 1080p video with Nyx Fast.",
            "Go to Dashboard → API Keys → Add key. Give it a name like \"AcluxPro\" and create it.",
            "Copy the key (it's shown only once).",
            "In AcluxPro open the settings gear (Capture, Photo editor or Video editor) → CLOUD AI → \"Add your fal.ai key\".",
            "Paste the key and tap SAVE KEY. It's stored encrypted on this phone and only sent to fal.ai.",
            "Turn on \"Photos in the cloud\" (Topaz upscale + Topaz Denoise) and/or \"Video in the cloud\" (Topaz Nyx noise reduction).",
            "Optional: pick the photo noise strength and the video model (Nyx Fast is quickest and cheapest), and keep \"Wi-Fi only\" on to avoid using mobile data.",
            "Use the AI features as usual — the AI badge shows upload, processing and download, and STOP cancels the job. If the cloud isn't available, the app uses the on-phone AI instead and tells you why.",
        ),
        numbered = true,
    ),
    GuideSection(
        GuideTopic.PHONE_AI, "AI on the phone", Icons.Filled.AutoAwesome, null,
        listOf(
            "AI upscale: Capture settings → AI UPSCALE → turn on \"AI upscale sizes\", then pick AI 24 / 36 / 48 MP under SIZE. The bigger copy is made in the background and saved next to the original.",
            "AI noise reduction: Photo editor → Detail, or Video editor → Color → Detail → AI NOISE REDUCTION. Tap the ⓘ ! next to it in the video editor for timing info.",
            "Video AI noise reduction on the phone can take several minutes up to about an hour — Cloud AI does it in minutes.",
            "Tap STOP on the AI badge at any time to cancel.",
        ),
        numbered = false,
    ),
    GuideSection(
        GuideTopic.CAPTURE, "Capture tips", Icons.Filled.CameraAlt, null,
        listOf(
            "Use the volume keys as a shutter / record button.",
            "PRO and PRO VIDEO give manual ISO, shutter, focus, white balance and exposure.",
            "Turn on zebras, false colour and focus peaking to judge exposure and focus.",
            "H.265 records smaller files at the same quality; H.264 plays everywhere.",
            "D-Log / D-Log M / Flat profiles keep more dynamic range — grade them later in the Video editor (Color → Input).",
            "Clean feed hides the controls for screen mirroring or HDMI.",
            "In LIVE, add your stream key once per platform; choose H.264 if your platform doesn't accept H.265.",
        ),
        numbered = false,
    ),
    GuideSection(
        GuideTopic.PHOTO, "Photo editor tips", Icons.Filled.Photo, null,
        listOf(
            "Hold your finger on the photo to see the original (before).",
            "Sliders update live — drag them and watch the photo change.",
            "COPY SETTINGS / PASTE SETTINGS moves your look to another photo — or to a video clip.",
            "SAVE keeps the edit as a project; LOAD opens it again later.",
            "Hold the divider between the photo and the controls, then drag to resize. Double-tap it to reset.",
            "Turn on \"Remember watermark\" in settings to add your watermark to every photo automatically.",
        ),
        numbered = false,
    ),
    GuideSection(
        GuideTopic.VIDEO, "Video editor tips", Icons.Filled.Movie, null,
        listOf(
            "Pinch the timeline to zoom; tap the scissors under the player to split at the playhead.",
            "Tap a cut on the timeline to choose a transition (34 to pick from).",
            "Edit → Crop & zoom: pinch, drag and twist right on the player. Ken Burns animates from a start to an end framing.",
            "Heavy clips (HDR, 10-bit, 4K) get a light preview copy automatically for smooth playback; exports always use the originals.",
            "If playback stalls, turn on \"Show playback info\" in settings and send a screenshot.",
            "Hold a divider to resize the player, timeline or controls; double-tap to reset.",
            "SAVE / LOAD projects, and use \"Save & leave\" when exiting.",
        ),
        numbered = false,
    ),
    GuideSection(
        GuideTopic.GENERAL, "General", Icons.Filled.Tune, null,
        listOf(
            "Change the app colour in any settings menu (APP COLOUR).",
            "\"Always start in …\" in settings opens the app straight into Capture, the Photo editor or the Video editor.",
            "Choose where photos and exports are saved in the editors' settings (SAVE LOCATION).",
            "The Exit button on the start screen closes the app completely.",
            "What's new in each version: see the release notes on the Releases page.",
        ),
        numbered = false,
    ),
)

/** In-app guide: how to add the Cloud AI key, plus tips for every part of the app. */
@Composable
fun GuideDialog(open: GuideTopic? = null, onDismiss: () -> Unit) {
    val expanded = remember { mutableStateListOf<GuideTopic>().apply { add(open ?: GuideTopic.CLOUD) } }
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0C0F)).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Guide & tips", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("GET MORE OUT OF ACLUXPRO", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp, letterSpacing = 2.sp))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    SECTIONS.forEach { s ->
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
