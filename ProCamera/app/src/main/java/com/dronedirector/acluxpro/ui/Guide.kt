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
            "Settings → SLATE & SHOT LOG: set scene, shot, take and notes; they show on the viewfinder, go into photos, and every clip and photo is logged. Export the log as a .csv for Excel / Sheets; the take number goes up after each recording.",
            "Settings → COPYRIGHT: your name and copyright notice are written into every photo you take and every photo you export (Artist / Copyright in the photo's details).",
            "SMART → PORTRAIT and AI PORTRAIT: the subject stays sharp and the background is blurred — people, pets, flowers, rocks, branches and other things that stand out. PORTRAIT adds the blur a second after the shot; AI PORTRAIT also shows it live in the viewfinder while you frame (people update fastest; other subjects a few times a second, so the edge can lag a little when you move — \"no subject\" means nothing is found yet). In both, slide the BACKGROUND BLUR slider above the zoom bar (left f/1.4 = most blur, right f/16 = least) or tap a spot on it; tap AUTO to minimise it and let AcluxPro pick the blur. The saved photo always gets the full-quality blur a second after the shot.",
            "SMART → PANO: tap the shutter and turn slowly in one direction, keeping the yellow line on the horizon — it finishes by itself at about 180°, or tap to finish early.",
            "While recording: a red tally border shows you're rolling, and the padlock on the left locks the screen against accidental touches (hold it to unlock). Warnings appear for low storage, low battery and heat.",
            "Settings → SAVE PHOTOS & VIDEOS TO: internal storage, an SD card / USB drive, or CHOOSE A FOLDER on any USB stick, SSD or card reader. Videos record straight onto the drive; if it's unplugged, AcluxPro saves to internal storage and tells you.",
            "C1 / C2 / C3 in the quick bar: hold one to save all your current settings (mode, lens, ISO, shutter, white balance, resolution, frame rate, profile, filter…), tap it to bring them back.",
            "Long press the viewfinder to lock the exposure there; drag the slider under the lock to brighten or darken while it stays locked. Long press again, or tap the small ✕, to unlock.",
            "Settings → AUDIO → Voice noise reduction (Light / Strong) cleans hiss, hum and background noise from recorded sound.",
            "Settings → PRIVACY & TITLE → Face blur pixelates faces in the viewfinder, videos and photos. The BLUR switch in the quick bar turns it on / off.",
            "Settings → PRIVACY & TITLE → Title on videos: type a title, pick lower third / centre / top and how long it shows; it is added when each clip is saved (TITLE switch in the quick bar).",
            "Settings → PHOTO → Auto-enhance saves an extra, improved copy of each photo next to the original.",
            "Panels icon (next to the settings gear): hide or show the exposure read-out, histogram, audio meter, level, Pro controls bar and tips. Presets: Full, Minimal, Video. Triple-tap the icon to put everything back as it was.",
            "Focus magnifier: in PRO / PRO VIDEO open F (focus) and turn the slider — a ×4 zoomed view appears (tap the screen first to choose where).",
            "Rack focus: in the focus control tap SET A and SET B at two distances, pick 1s / 2s / 4s, then → A / → B glides the focus smoothly while you film.",
            "Settings → Scope switches the Pro brightness graph between histogram, waveform, RGB parade and vectorscope (with a skin-tone line).",
            "Settings → VIDEO → Pre-record keeps the 3–10 seconds before you press record.",
            "Settings → AUDIO: Wind filter cuts wind rumble; USB / wired mics are used automatically; Bluetooth microphone records from a Bluetooth headset. The audio graph shows L / R meters on phones with stereo mics.",
            "Settings → MONITORING & LENS → Monitoring LUT: import a .cube LUT and see it on the viewfinder (e.g. shoot D-Log, see Rec.709). \"Record the LUT\" also burns it into videos.",
            "Shutter as angle (settings) shows the video shutter in degrees; the 180° button in the shutter control sets natural motion blur for your frame rate.",
            "Anamorphic desqueeze (settings) stretches the viewfinder back to the real shape for anamorphic lenses; desqueeze the clips later in the Video editor.",
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
            "Masks → Subject (AI) / Sky (AI) select the subject or the sky for you (on the phone, or more precisely with Cloud AI).",
            "Effects → BACKGROUND (AI): FIND SUBJECT, then blur the background (portrait look), remove it (transparent PNG) or fill it with a colour.",
            "Heal → AI ERASE: paint over a person, sign or power line and tap ERASE (Bria Eraser with Cloud AI; on the phone for smaller things).",
            "Masks: add a Linear gradient, Radial or Brush mask, then use ADJUST INSIDE THE MASK. Drag the handles on the photo (or paint with the brush); red shows where the mask applies.",
            "Heal: tap dust, blemishes or small objects on the photo — a matching patch from nearby covers each one. Clone stamp: tap the source, then tap where to paint. Drag a solid circle to move a spot, drag its dashed circle to change where it copies from, hold a spot to delete it. UNDO LAST removes the latest.",
            "Lens: Distortion straightens bulging lines from wide lenses; Vignetting brightens dark corners; Colour fringes removes purple / green outlines.",
            "Crop & perspective → PERSPECTIVE: Vertical straightens buildings that lean in at the top.",
            "Export: JPEG, PNG, WebP or HEIC; sizes for Instagram / web / 4K; AI 2× / 4× upscale; keep or remove camera info and location; BATCH EXPORT applies your edit to up to 50 photos.",
            "Start page → MERGE PHOTOS: HDR merge (bracketed shots), Panorama (overlapping shots, left to right) or Focus stack — the result opens in the editor.",
            "SPLIT (top-left of the photo) shows before / after side by side — drag to move the divider.",
            "Presets → MY PRESETS → + SAVE CURRENT LOOK saves your grade; tap a preset to apply it, hold to delete. Presets also work in the Video editor.",
            "Presets → MY LUTS → + IMPORT adds your own .cube LUTs; use Strength to blend, hold a LUT to delete it.",
            "Sliders update live while you drag.",
            "COPY SETTINGS / PASTE SETTINGS moves a look to another photo — or to a video clip.",
            "SAVE keeps the edit as a project; LOAD opens it again later.",
            "Hold the divider between the photo and the controls, then drag to resize. Double-tap to reset.",
            "History panel: tap any step to go back to it; + SAVE SNAPSHOT keeps a version you can return to while the photo is open.",
            "Masks → Colour range / Brightness range: tap the photo to pick a colour or brightness; only those parts are changed (e.g. only the blue sky, only the highlights). Range and Softness fine-tune it.",
            "Panels icon (next to the settings gear): hide or show the tools panel, copy / paste bar, histogram and before / after buttons — the photo grows into the space and shrinks back when a panel returns. Presets: Full, View, Edit, Color. Triple-tap the icon to reset the whole layout.",
            "Undo / redo are in the top bar; the histogram toggles with the chart icon.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "\"Remember watermark\" adds your watermark to every new photo automatically.",
            "SAVE LOCATION chooses where exported photos go: same as capture, internal storage, an SD card / USB drive, or a folder you pick on a USB stick / SSD / SD card.",
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
            "Tap a cut on the timeline to open Transitions: VIDEO TRANSITIONS has 34 picture transitions; AUDIO TRANSITIONS sets how the sound changes (constant power, exponential, dip to silence and more). On a plain cut the sound fades out and in around the cut. SOUND EFFECTS (same page) puts a sound on the cut: 24 built-in sounds in four groups — hits (impact, boom, trailer hit, braam, drum hit, punch, thunder, heartbeat, sub drop), whooshes (whoosh, swoosh, deep whoosh, swirl, wind gust), builds (riser, reverse cymbal, clock tick build, tape stop) and stylized (glitch, record scratch, laser zap, magic shimmer, camera shutter, TV static) — tap ▶ to listen. MY SOUNDS → + IMPORT SOUND adds your own files (MP3, WAV, M4A…); choose whether each starts on, centres on or ends on the cut.",
            "The RGB histogram is a pull-out drawer on the right edge of the player: drag the small tab to the left to pull it out, drag it back (or tap the tab) to tuck it away. It never shows in full screen.",
            "Layers → AI CAPTIONS writes timed captions from the speech in your edit (Whisper via your fal.ai key); pick the language and style.",
            "Edit → AUTO-REFRAME turns the project into 9:16, 1:1 or 4:5 and makes every clip follow its subject.",
            "Edit → AI slow-mo (Cloud AI) adds 3 new frames between every pair and plays the clip at ¼ speed — smooth slow motion from normal footage (clips up to 20 s).",
            "Keyframes: select a layer → KEYFRAMES → ◆ ADD KEYFRAME AT PLAYHEAD, move the playhead, then drag / resize / fade the layer — it glides between keyframes (diamonds on the timeline).",
            "Motion tracking: select a layer, put its centre on the object at the playhead, then KEYFRAMES → ⌖ TRACK MOTION FROM PLAYHEAD — the layer (text, sticker, blur…) follows the object; tap again to stop. Works best on sharp, high-contrast spots.",
            "Audio → VOLUME KEYFRAMES rides a clip's volume over time (e.g. dip it under a voiceover).",
            "Edit → SPEED RAMP gives smooth speed curves (Montage, Hero, Bullet, Flash in / out…); the clip's length follows the curve.",
            "Edit → Freeze adds a 2 s still of the frame under the playhead; Reverse plays the clip backwards (silent, up to 60 s).",
            "Edit → Detach audio turns the clip's sound into its own track — move or extend it for J- and L-cuts.",
            "Audio → VOICEOVER records the microphone at the playhead while the edit plays (muted); it lands on its own audio track.",
            "Audio → ADD BEAT MARKERS puts a marker on every beat of the music (or tap BEATS on any audio track); with SNAP on, layers lock to them.",
            "Layers: BLEND MODE (Screen, Multiply, Overlay…) for picture, text and sticker layers; CHROMA KEY removes green / blue screens from video and image layers.",
            "◆ MARKER above the timeline drops a marker at the playhead (tap again on it to remove); tap the ruler near a marker to jump to it.",
            "SNAP makes layers and audio snap to cuts, markers, the playhead and each other while you hold-and-drag them.",
            "Audio bars and clips show real waveforms, so you can cut on beats and words.",
            "Color → LUTs → + IMPORT adds your own .cube LUTs; \"Use as input conversion\" turns a camera maker's log LUT into the clip's input transform. MY PRESETS saves and applies your looks.",
            "Deliver → PRESETS sets YouTube, Instagram, TikTok and more in one tap; RANGE exports only the part between IN and OUT; SAVE FRAME AS PHOTO saves a still.",
            "Edit → ANAMORPHIC DESQUEEZE stretches clips from anamorphic lenses back to their real shape.",
            "Deliver shows the estimated file size; it updates as you change resolution, frame rate, bitrate and audio.",
            "The buttons at the left of each timeline row mute the video track or an audio track, hide a layer, or lock the row so it can't be moved, trimmed, split or deleted.",
            "Deliver → Audio only saves just the sound of your edit as an .m4a file.",
            "Undo and redo sit right next to the scissors under the player and are always visible — they step back and forward through clip, layer and audio changes.",
            "Give any layer its own name (Layers → selected layer → name) — handy for telling watermarks apart; the watermark text on the video is a separate field.",
            "Hold a layer or audio bar on the timeline until it buzzes, then drag: left / right moves it in time, up / down changes the layer order.",
            "Hold the start or end edge of a layer or audio bar, then slide to shorten or extend it.",
            "Media → Audio adds music and sound files to the media pool; ADD TO TIMELINE places them at the playhead.",
            "Edit → Crop & zoom: pinch, drag and twist on the player; Ken Burns animates start → end.",
            "COPY / PASTE SETTINGS copies a clip's grade, audio and more to other clips (or all clips).",
            "Heavy clips (HDR, 10-bit, 4K) get a light preview copy for smooth playback; exports use the originals.",
            "If playback stalls, turn on \"Show playback info\" here and share a screenshot.",
            "Hold a divider to resize the player, timeline or controls; double-tap to reset.",
            "Layers → Adjustment: one grade over every clip in its time range (e.g. a look for a whole scene); set its length on the timeline and its strength.",
            "Deliver → SEND TO DESKTOP EDITOR: save the edit as an EDL or FCPXML to finish it in DaVinci Resolve, Premiere Pro, Avid or Final Cut Pro (copy the original clips to the computer too).",
            "Color → Masks: up to 3 masks per clip (gradient, radial, colour range, brightness range), each with its own grade; drag the handles on the player.",
            "Edit → Remove silences: cuts the pauses out of talking clips (this clip or all clips); set the silence level and minimum pause. Undo brings them back.",
            "Layers → ANIMATION: in / out animations for text and pictures — fade, slide, pop, zoom, bounce, wipe reveal, typewriter — with adjustable length.",
            "Deliver → LOUDNESS: −14 LUFS (YouTube, Instagram, Spotify), −16 (Apple, podcasts) or −23 (TV) makes every export equally loud.",
            "Panels icon (next to the settings gear): hide or show the tools panel, transport bar, histogram, timecode and audio waveforms — the player and timeline grow into the space and shrink back when a panel returns. Presets: Full, Cut, Color, Audio. Triple-tap the icon to reset the whole layout (panels and divider sizes).",
            "SAVE / LOAD projects; \"Save & leave\" when exiting.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "\"Remember watermark\" adds your watermark to every new project automatically.",
            "SAVE LOCATION chooses where exported videos go: same as capture, internal storage, an SD card / USB drive, or a folder you pick on a USB stick / SSD / SD card.",
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
