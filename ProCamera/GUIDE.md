# AcluxPro — Guides

The same guides are in the app: open the **settings gear** in Capture, the Photo editor or the Video editor and tap the **guide** button (or **How to add a key** under Cloud AI). Each guide shows only what applies to that part of the app.
See also the [feature list](FEATURES.md) and the [changelog](CHANGELOG.md).

## Cloud AI: add your API key (all three guides)
Optional: runs Topaz Labs' professional AI on fast servers through your own fal.ai account. Without it, the AI runs on the phone.

1. Open [fal.ai](https://fal.ai/dashboard/keys) and sign up or log in.
2. Add credit to your fal.ai account ([Billing](https://fal.ai/dashboard/billing)). You pay fal.ai per use.
3. Go to **Dashboard → API Keys → Add key**, name it (e.g. "AcluxPro") and create it.
4. Copy the key — it's shown only once.
5. In AcluxPro: settings gear → **CLOUD AI** → **Add your fal.ai key**.
6. Paste the key and tap **SAVE KEY**. It's stored encrypted on the phone and only sent to fal.ai.
7. Turn on **Photos in the cloud** (Capture: Topaz upscale · Photo editor: Topaz Denoise, pick Normal / Strong / Extreme / Max) or **Video in the cloud** (Video editor: Topaz Nyx — Nyx Fast is quickest and cheapest, about $0.10 per 10 s of 1080p).
8. Keep **Wi-Fi only** on to avoid mobile data.
9. The AI badge shows upload, processing and download; **STOP** cancels. If the cloud isn't available, the on-phone AI is used and you're told why.

---

## Capture guide
**AI upscale**
- Settings → AI UPSCALE → turn on "AI upscale sizes".
- Pick AI 24 / 36 / 48 MP under SIZE (quick controls or Settings → Photo size).
- Shoot as usual — the bigger copy is made in the background and saved next to the original.
- Standard is faster; High keeps more real detail. Choose whether to keep the original. STOP on the AI badge cancels.

**Capture tips**
- Settings → SLATE & SHOT LOG: set scene, shot, take and notes; they show on the viewfinder, go into photos, and every clip and photo is logged. Export the log as a .csv for Excel / Sheets; the take number goes up after each recording.
- Settings → COPYRIGHT: your name and copyright notice are written into every photo you take and every photo you export (Artist / Copyright in the photo's details).
- SMART → PORTRAIT and AI PORTRAIT: the subject stays sharp and the background is blurred — people, pets, flowers, rocks, branches and other things that stand out. PORTRAIT adds the blur a second after the shot; AI PORTRAIT also shows it live in the viewfinder while you frame (people update fastest; other subjects a few times a second, so the edge can lag a little when you move — "no subject" means nothing is found yet). In both, slide the BACKGROUND BLUR slider above the zoom bar (left f/1.4 = most blur, right f/16 = least) or tap a spot on it; tap AUTO to minimise it and let AcluxPro pick the blur. The saved photo always gets the full-quality blur a second after the shot.
- SMART → PANO: tap the shutter and turn slowly in one direction, keeping the yellow line on the horizon — it finishes by itself at about 180°, or tap to finish early.
- While recording: a red tally border shows you're rolling, and the padlock on the left locks the screen against accidental touches (hold it to unlock). Warnings appear for low storage, low battery and heat.
- Settings → SAVE PHOTOS & VIDEOS TO: internal storage, an SD card / USB drive, or CHOOSE A FOLDER on any USB stick, SSD or card reader. Videos record straight onto the drive; if it's unplugged, AcluxPro saves to internal storage and tells you.
- C1 / C2 / C3 in the quick bar: hold one to save all your current settings (mode, lens, ISO, shutter, white balance, resolution, frame rate, profile, filter…), tap it to bring them back.
- Long press the viewfinder to lock the exposure there; drag the slider under the lock to brighten or darken while it stays locked. Long press again, or tap the small ✕, to unlock.
- Settings → AUDIO → Voice noise reduction (Light / Strong) cleans hiss, hum and background noise from recorded sound.
- Settings → PRIVACY & TITLE → Face blur pixelates faces in the viewfinder, videos and photos. The BLUR switch in the quick bar turns it on / off.
- Settings → PRIVACY & TITLE → Title on videos: type a title, pick lower third / centre / top and how long it shows; it is added when each clip is saved (TITLE switch in the quick bar).
- Settings → PHOTO → Auto-enhance saves an extra, improved copy of each photo next to the original.
- Panels icon (next to the settings gear): hide or show the exposure read-out, histogram, audio meter, level, Pro controls bar and tips. Presets: Full, Minimal, Video. Triple-tap the icon to put everything back as it was.
- Volume keys work as shutter / record.
- Focus magnifier: in PRO / PRO VIDEO open F (focus) and turn the slider — a ×4 zoomed view appears (tap the screen first to choose where).
- Rack focus: in the focus control tap SET A and SET B at two distances, pick 1s / 2s / 4s, then → A / → B glides the focus smoothly while you film.
- Settings → Scope switches the Pro brightness graph between histogram, waveform, RGB parade and vectorscope (with a skin-tone line).
- Settings → VIDEO → Pre-record keeps the 3–10 seconds before you press record.
- Settings → AUDIO: Wind filter cuts wind rumble; USB / wired mics are used automatically; Bluetooth microphone records from a Bluetooth headset. The audio graph shows L / R meters on phones with stereo mics.
- Settings → MONITORING & LENS → Monitoring LUT: import a .cube LUT and see it on the viewfinder (e.g. shoot D-Log, see Rec.709). "Record the LUT" also burns it into videos.
- Shutter as angle (settings) shows the video shutter in degrees; the 180° button in the shutter control sets natural motion blur for your frame rate.
- Anamorphic desqueeze (settings) stretches the viewfinder back to the real shape for anamorphic lenses; desqueeze the clips later in the Video editor.
- PRO and PRO VIDEO give manual ISO, shutter, focus, white balance and exposure.
- Zebras, false colour and focus peaking help judge exposure and focus.
- H.265 = smaller files at the same quality; H.264 plays everywhere.
- D-Log / D-Log M / Flat keep more dynamic range — grade them later in the Video editor.
- Clean feed hides the controls for screen mirroring / HDMI.
- In LIVE, add your stream key once per platform; choose H.264 if your platform doesn't accept H.265.
- Settings → PHOTO SIZE shows what your phone's sensor allows apps to use.

**General**
- APP COLOUR changes the highlight colour.
- "Always start in Capture" opens the app straight into the camera.
- SAVE PHOTOS & VIDEOS TO chooses where captures are saved.

---

## Photo editor guide
**AI noise reduction**
- Detail → AI NOISE REDUCTION → move Strength above Off.
- The preview is cleaned once by the AI; then the slider works instantly.
- Exporting cleans the full-size photo (on the phone, or in the cloud if Cloud AI is on). STOP on the AI badge cancels.

**RAW (DNG) photos**
- Open a DNG (from AcluxPro's Pro mode or any phone / camera that saves DNG) and the RAW badge appears: the editor develops the sensor data itself.
- Light → RAW PROFILE picks the base look: Standard, Flat (most room for grading), Vivid, Portrait, Landscape or Mono.
- Exposure (±5 stops), Highlights, Shadows, Whites and Blacks work on the full-range data — pull Highlights down to bring back bright skies, lift Shadows without the blotches you'd get from a JPEG.
- Color → WHITE BALANCE: As shot, Auto, Daylight, Cloudy, Shade, Tungsten or Fluorescent, then fine-tune with Temperature and Tint.
- The DNG is never changed; exports are JPEG, PNG, WebP or HEIC. RAW exports take a little longer (the full-size photo is developed again at top quality).
- AI erase on a RAW makes a normal copy to work on; after that, the photo is edited like a JPEG.

**Photo editor tips**
- Leave the editor with your phone's back gesture or back button (the top bar has no back arrow, so EXPORT always fits).
- History panel: tap any step to go back to it; + SAVE SNAPSHOT keeps a version you can return to while the photo is open.
- Masks → Colour range / Brightness range: tap the photo to pick a colour or brightness; only those parts are changed (e.g. only the blue sky, only the highlights). Range and Softness fine-tune it.
- Panels icon (next to the settings gear): hide or show the tools panel, copy / paste bar, histogram and before / after buttons — the photo grows into the space and shrinks back when a panel returns. Presets: Full, View, Edit, Color. Triple-tap the icon to reset the whole layout.
- Hold your finger on the photo to see the original.
- Masks → Subject (AI) / Sky (AI) select the subject or the sky for you (on the phone, or more precisely with Cloud AI).
- Effects → BACKGROUND (AI): FIND SUBJECT, then blur the background (portrait look), remove it (transparent PNG) or fill it with a colour.
- Heal → AI ERASE: paint over a person, sign or power line and tap ERASE (Bria Eraser with Cloud AI; on the phone for smaller things).
- Masks: add a Linear gradient, Radial or Brush mask, then use ADJUST INSIDE THE MASK. Drag the handles on the photo (or paint with the brush); red shows where the mask applies.
- Heal: tap dust, blemishes or small objects on the photo — a matching patch from nearby covers each one. Clone stamp: tap the source, then tap where to paint. Drag a solid circle to move a spot, drag its dashed circle to change where it copies from, hold a spot to delete it. UNDO LAST removes the latest.
- Lens: Distortion straightens bulging lines from wide lenses; Vignetting brightens dark corners; Colour fringes removes purple / green outlines.
- Crop & perspective → PERSPECTIVE: Vertical straightens buildings that lean in at the top.
- Export: JPEG, PNG, WebP or HEIC; sizes for Instagram / web / 4K; AI 2× / 4× upscale; keep or remove camera info and location; BATCH EXPORT applies your edit to up to 50 photos.
- Start page → MERGE PHOTOS: HDR merge (bracketed shots), Panorama (overlapping shots, left to right) or Focus stack — the result opens in the editor.
- SPLIT (top-left of the photo) shows before / after side by side — drag to move the divider.
- Presets → MY PRESETS → + SAVE CURRENT LOOK saves your grade; tap a preset to apply it, hold to delete. Presets also work in the Video editor.
- Presets → MY LUTS → + IMPORT adds your own .cube LUTs; use Strength to blend, hold a LUT to delete it.
- Sliders update live while you drag.
- COPY / PASTE SETTINGS moves a look to another photo — or to a video clip.
- SAVE keeps the edit as a project; LOAD opens it again later.
- Hold the divider between photo and controls, then drag to resize; double-tap to reset.
- Undo / redo in the top bar; the chart icon toggles the histogram.

**General**
- "Remember watermark" adds your watermark to every new photo.
- SAVE LOCATION chooses where exported photos go: same as capture, internal storage, an SD card / USB drive, or a folder you pick on a USB stick / SSD / SD card.
- APP COLOUR changes the highlight colour.
- "Always start in Photo editor" opens the app straight into this editor.

---

## Video editor guide
**AI noise reduction**
- Color → Detail → AI NOISE REDUCTION → move Strength above Off.
- Applied when exporting; the preview shows the normal noise reduction.
- On the phone it can take several minutes up to about an hour (tap ⓘ next to it); Cloud AI does it in minutes. STOP on the AI badge cancels.

**Video editor tips**
- The timeline starts at the left edge: drag one finger (or tap the ruler) to move the playhead; two fingers pinch to zoom and slide to scroll. The scissors under the player split at the playhead.
- Tap a cut on the timeline to open Transitions: VIDEO TRANSITIONS has 34 picture transitions; AUDIO TRANSITIONS sets how the sound changes (constant power, exponential, dip to silence and more). On a plain cut the sound fades out and in around the cut. SOUND EFFECTS (same page) puts a sound on the cut: 24 built-in sounds in four groups — hits (impact, boom, trailer hit, braam, drum hit, punch, thunder, heartbeat, sub drop), whooshes (whoosh, swoosh, deep whoosh, swirl, wind gust), builds (riser, reverse cymbal, clock tick build, tape stop) and stylized (glitch, record scratch, laser zap, magic shimmer, camera shutter, TV static) — tap ▶ to listen. MY SOUNDS → + IMPORT SOUND adds your own files (MP3, WAV, M4A…); choose whether each starts on, centres on or ends on the cut.
- The RGB histogram is a pull-out drawer on the right edge of the player: drag the small tab to the left to pull it out, drag it back (or tap the tab) to tuck it away. It never shows in full screen.
- Layers → AI CAPTIONS writes timed captions from the speech in your edit (Whisper via your fal.ai key); pick the language and style.
- Edit → AUTO-REFRAME turns the project into 9:16, 1:1 or 4:5 and makes every clip follow its subject.
- Edit → AI slow-mo (Cloud AI) adds 3 new frames between every pair and plays the clip at ¼ speed — smooth slow motion from normal footage (clips up to 20 s).
- Keyframes: select a layer → KEYFRAMES → ◆ ADD KEYFRAME AT PLAYHEAD, move the playhead, then drag / resize / fade the layer — it glides between keyframes (diamonds on the timeline).
- Motion tracking: select a layer, put its centre on the object at the playhead, then KEYFRAMES → ⌖ TRACK MOTION FROM PLAYHEAD — the layer (text, sticker, blur…) follows the object; tap again to stop. Works best on sharp, high-contrast spots.
- Audio → VOLUME KEYFRAMES rides a clip's volume over time (e.g. dip it under a voiceover).
- Edit → SPEED RAMP gives smooth speed curves (Montage, Hero, Bullet, Flash in / out…); the clip's length follows the curve.
- Edit → Freeze adds a 2 s still of the frame under the playhead; Reverse plays the clip backwards (silent, up to 60 s).
- Edit → Detach audio turns the clip's sound into its own track — move or extend it for J- and L-cuts.
- Audio → VOICEOVER records the microphone at the playhead while the edit plays (muted); it lands on its own audio track.
- Audio → ADD BEAT MARKERS puts a marker on every beat of the music (or tap BEATS on any audio track); with SNAP on, layers lock to them.
- Layers: BLEND MODE (Screen, Multiply, Overlay…) for picture, text and sticker layers; CHROMA KEY removes green / blue screens from video and image layers.
- ◆ MARKER above the timeline drops a marker at the playhead (tap again on it to remove); tap the ruler near a marker to jump to it.
- SNAP makes layers and audio snap to cuts, markers, the playhead and each other while you hold-and-drag them.
- Audio bars and clips show real waveforms, so you can cut on beats and words.
- Color → LUTs → + IMPORT adds your own .cube LUTs; "Use as input conversion" turns a camera maker's log LUT into the clip's input transform. MY PRESETS saves and applies your looks.
- Deliver → PRESETS sets YouTube, Instagram, TikTok and more in one tap; RANGE exports only the part between IN and OUT; SAVE FRAME AS PHOTO saves a still.
- Edit → ANAMORPHIC DESQUEEZE stretches clips from anamorphic lenses back to their real shape.
- Deliver shows the estimated file size; it updates as you change resolution, frame rate, bitrate and audio.
- The buttons at the left of each timeline row mute the video track or an audio track, hide a layer, or lock the row so it can't be moved, trimmed, split or deleted.
- Deliver → Audio only saves just the sound of your edit as an .m4a file.
- Undo and redo sit right next to the scissors under the player and are always visible — they step back and forward through clip, layer and audio changes.
- Give any layer its own name (Layers → selected layer → name) — handy for telling watermarks apart; the watermark text on the video is a separate field.
- Hold a layer or audio bar until it buzzes, then drag: left / right moves it in time, up / down changes the layer order.
- Hold the start or end edge of a layer or audio bar, then slide to shorten or extend it.
- Media → Audio adds music and sound files to the media pool; ADD TO TIMELINE places them at the playhead.
- Edit → Crop & zoom: pinch, drag and twist on the player; Ken Burns animates start → end.
- COPY / PASTE SETTINGS copies a clip's grade, audio and more to other clips (or all clips).
- Heavy clips (HDR, 10-bit, 4K) get a light preview copy for smooth playback; exports use the originals.
- If playback stalls, turn on "Show playback info" in settings and share a screenshot.
- Hold a divider to resize the player, timeline or controls; double-tap to reset.
- Layers → Adjustment: one grade over every clip in its time range (e.g. a look for a whole scene); set its length on the timeline and its strength.
- Deliver → SEND TO DESKTOP EDITOR: save the edit as an EDL or FCPXML to finish it in DaVinci Resolve, Premiere Pro, Avid or Final Cut Pro (copy the original clips to the computer too).
- Color → Masks: up to 3 masks per clip (gradient, radial, colour range, brightness range), each with its own grade; drag the handles on the player.
- Edit → Remove silences: cuts the pauses out of talking clips (this clip or all clips); set the silence level and minimum pause. Undo brings them back.
- Layers → ANIMATION: in / out animations for text and pictures — fade, slide, pop, zoom, bounce, wipe reveal, typewriter — with adjustable length.
- Deliver → LOUDNESS: −14 LUFS (YouTube, Instagram, Spotify), −16 (Apple, podcasts) or −23 (TV) makes every export equally loud.
- Panels icon (next to the settings gear): hide or show the tools panel, transport bar, histogram, timecode and audio waveforms — the player and timeline grow into the space and shrink back when a panel returns. Presets: Full, Cut, Color, Audio. Triple-tap the icon to reset the whole layout (panels and divider sizes).
- SAVE / LOAD projects; "Save & leave" when exiting.

**General**
- "Remember watermark" adds your watermark to every new project.
- SAVE LOCATION chooses where exported videos go: same as capture, internal storage, an SD card / USB drive, or a folder you pick on a USB stick / SSD / SD card.
- APP COLOUR changes the highlight colour.
- "Always start in Video editor" opens the app straight into this editor.
