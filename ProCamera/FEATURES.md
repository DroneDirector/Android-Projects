# VeyroCut Pro — Features

VeyroCut Pro is a pro camera, photo editor and video editor for Android phones and tablets (Android 8.0+).
App ID `com.dronedirector.veyrocutpro`. Downloads are on the [Releases page](https://github.com/DroneDirector/Android-Projects/releases); see the [changelog](CHANGELOG.md) for what changed in each version. Open-source parts and licences: [third-party notices](THIRD_PARTY_NOTICES.md).

---

## Start screen
- **Capture** and **Editor** tiles over an animated aperture background, in the app colour.
- **Editor hub** to choose the Photo editor or the Video editor.
- **Startup choice**: always open in Capture, the Photo editor or the Video editor (set in each one's settings; untick to see the start screen again).
- App **version** shown bottom-left; animated **Exit** button bottom-right closes the app.
- **Open-source licences** and the **privacy policy** in every settings gear ([PRIVACY.md](PRIVACY.md)).
- Separate **guides** for Capture, the Photo editor and the Video editor (Cloud AI key setup, AI features, tips), opened from each settings gear — also in [GUIDE.md](GUIDE.md).

- **Simple / Pro layout**: everyday modes and tools only, or everything (Capture settings and both editors' settings).
- **Backup & restore**: one .vcbackup file with all settings, presets, LUTs, watermarks, sounds and projects.
- **Quick tour** of the camera gestures on first start (replay from Settings).
- **App shortcuts** (long-press the icon: Video, Pro photo, Photo editor, Video editor) and a **Quick Settings camera tile**.
- **Open from other apps**: Share / Edit with VeyroCut Pro opens the photo or video editor directly.

- **Languages**: English, Danish (Dansk) and Faroese (Føroyskt), following the phone or chosen in settings.

## Capture
### Modes
- **Photo**, **Pro** (photo), **Video**, **Pro Video** and **Live** streaming.
- Smart modes: **Portrait** (background blur for people, pets, flowers, rocks, branches…; slider f/1.4 – f/16, or AUTO), **AI Portrait** (the same, plus the blur shown live in the viewfinder), **Panorama** (gyroscope-guided ~180°, cylindrical stitching), **Time-lapse**, **Hyperlapse**, **Slow-mo**, **Super slow-mo**, **Burst**.

### Photo
- Photo **size per camera** (megapixel choice), aspect **4:3 / 16:9 / 1:1**.
- Android 12+ **full-resolution sensor mode** when the phone allows it, plus a read-out of what the sensor offers to apps.
- **AI upscale** (optional, off by default): AI 24 / 36 / 48 MP sizes in the size picker. An on-device AI model (ESRGAN, runs on the phone, no internet) rebuilds each photo at the bigger size in the background; Standard or High quality; keep or replace the original; animated progress badge with a **Stop** button.
- **JPEG, RAW (DNG) and RAW+JPEG** in Pro.
- Scenes: **Standard, HDR, Night, Bokeh, Auto+** (where the phone supports them).
- **Burst** (×3/×5/×10), **self-timer**, **interval** shooting, **exposure bracketing** (±1 / ±2).
- **Film looks**: Natural, Vivid, Chrome, Warm, Cool, Cinema, Fade, Sepia, Mono, Noir, Teal & Orange, Print Film, Bleach Bypass, Golden Hour, Cold Thriller, Vintage 8mm.
- Flash **Off / Auto / On / Torch**; shutter sound on/off; volume keys as shutter.

### Video
- **4K, 1080p, 720p, 480p**; high-bitrate option.
- Codec choice **H.264 or H.265 (HEVC)** — also for time-lapse / hyperlapse; **10-bit HLG**.
- Picture profiles: **Standard, Flat, D-Log, D-Log M**.
- **Stabilization choice**: Off, **In-app (crop)** — the camera's own stabilization — or **Gyroflow**: the gyroscope and accelerometer are recorded into a .gcsv log per clip (optical / electronic stabilization off) for gimbal-smooth results in Gyroflow on a computer.
- **Motion blur (electronic ND)**: blends ×2 / ×4 / ×8 frames for cinematic motion blur in bright light, in preview and recording.
- **Pre-record**: keeps 3, 5 or 10 seconds from before you press record.
- **Cinema clip naming** (camera / reel / clip) or date & time.
- **Clean feed** (hide the on-screen controls for an HDMI / screen mirror).

### Long exposure
- **LONG EXP** smart mode: **Light trails** (lighten stacking), **Silky water** (averaging) and **Night sky** (averaging + brightening, less noise); 2–30 s, live build-up preview, finish early with the shutter.

### Remote monitor
- **Remote monitor & control** from any browser on the same Wi-Fi or hotspot (PIN-protected address): live picture, shutter / record, zoom, Photo / Video / Pro Video, autofocus, AE lock, recording time and exposure read-out.

### Pro controls
- **Slate & shot log**: scene / shot / take and notes, viewfinder badge, photo description, CSV shot log with camera settings, automatic take numbers.
- **Safe areas**: action-safe 90 % and title-safe 80 % frames.
- **Tally border**, **touch lock** while recording, and **storage / battery / heat warnings**.
- **Custom set-ups C1 / C2 / C3**: hold to save all current settings, tap to recall.
- **Autofocus with hold-for-manual-focus** in every mode (photo, pro, video, pro video and the smart modes): tap to focus, hold 2 s for manual focus with a slim vertical focus slider on the right edge of the feed (∞ to closest, live distance tag, focus magnifier while dragging), ✕ returns to autofocus.
- **Hold-and-release exposure lock** (about ½ s) with an EV slider under the lock; do it again or tap ✕ to unlock.
- **Shutter angle** read-out for video with a one-tap **180°** button.
- **Monitoring LUT**: your own .cube LUT on the viewfinder (optionally recorded into videos).
- **Focus magnifier** (×4) while focusing manually; **rack focus** between two stored points (1 / 2 / 4 s).
- **Scopes**: histogram, **waveform**, **RGB parade** or **vectorscope** with skin-tone line.
- **Anamorphic desqueeze** for the viewfinder (1.33×, 1.5×, 1.55×, 1.8×, 2×).
- Manual **ISO, shutter, focus, white balance** (presets 3200K–7500K and more), **exposure compensation** and **zoom**.
- Analysis: **zebras**, **false colour**, **focus peaking**, **histogram** (also in Pro Video when the phone can't add an analysis stream).
- **Composition grids** (3×3, phi, 4×4, crosshair) and **frame guides** (2.39, 1.85, 4:3, 1:1, 9:16).

### Audio
- **Manual input gain** with limiter (applied to finished clips).
- **Headphone monitoring** with live gain, limiter and volume; audio level graph.
- **Left / right level meters** (stereo microphones), **wind filter**, Bluetooth headset microphone option; USB / wired mics used automatically.
- **Voice noise reduction** (Light / Strong): cleans hiss, hum and background noise from recorded sound.

### Privacy & titles
- **Face blur**: faces are pixelated live in the viewfinder, in videos and in photos (on-device face detection). Quick BLUR switch.
- **Title on videos**: your text burned into each clip — lower third, centre or top, for 3 / 5 / 10 s or the whole clip, with a fade / slide in and out; quick TITLE switch and viewfinder preview.
- **Auto-enhance** photos: an extra, improved copy of each photo (exposure, contrast, white balance, vibrance); the original is kept.
- Panels menu toggles for the BLUR and TITLE switches and the title preview.

### Live streaming
- Stream to **YouTube, TikTok, Twitch, Facebook** or any **RTMP / RTMPS / SRT** server; **H.264 or H.265**.

### Gallery
- **Trash** (Android 11+): deleted items are kept 30 days and can be restored; empty trash.
- **Culling**: 1–5 stars, pick / reject, rating filters and delete-all-rejects.
- Full-screen in-app gallery for photos and videos; **Favourites**; separate **RAW** tab with badges.
- **Colour grading** for photos and videos straight from the gallery.

### Settings
- **Copyright**: your name and copyright written into every photo (camera and photo editor exports).
- **External storage**: save to phone storage, an SD card / USB drive, or a folder you pick on any USB stick, SSD or SD card (videos record straight onto the drive; falls back to internal storage if it is unplugged); **app colour** (accent) picker; layout and control options.
- **Panels menu** (icon next to the gear): show / hide the exposure read-out, histogram, audio meter, level, Pro controls bar and tips; presets **Full / Minimal / Video**; **triple-tap** the icon to restore the original layout.

---

## Photo editor
- Open from the gallery, files or recent VeyroCut Pro photos; **saved projects** row on the start page.
- **Skip to the editor** from the start page: open the editor without picking a photo first, then add one from inside (manual, every time).
- Panels: **Presets, Light, Curve, Color, HSL Mixer, Grading, Detail, Effects, Crop & format, Watermark, Export**.
- **RAW (DNG) develop**: the editor reads the sensor data itself (uncompressed and lossless-compressed DNGs, lens-shading maps, the camera's colour matrix) — exposure ±5 stops, highlights, shadows, whites, blacks and white balance work on the full-range data with a soft highlight roll-off; **RAW profiles** (Standard, Flat, Vivid, Portrait, Landscape, Mono) and **white balance presets** (As shot, Auto, Daylight, Cloudy, Shade, Tungsten, Fluorescent); full-quality demosaic on export; RAW files in a batch export are developed too.
- **Full DNG support**: uncompressed, lossless-JPEG, lossy-JPEG and Deflate DNGs; 8–32-bit integer and 16 / 24 / 32-bit floating-point (HDR) data with all TIFF / DNG predictors; Bayer, X-Trans, Quad Bayer and any other RGB colour filter pattern, plus linear (demosaiced) DNGs; strips or tiles; linearisation tables, per-row / column black levels, active area, default crop and DefaultUserCrop; all OpcodeList1 / 2 / 3 corrections (bad pixels, map table / polynomial, per-row / column delta and scale, lens-shading gain maps, WarpRectilinear distortion + lateral chromatic aberration, FixVignetteRadial); full colour model (dual-illuminant ColorMatrix / CameraCalibration / ForwardMatrix interpolated by the white, AnalogBalance, AsShotNeutral / AsShotWhiteXY); the embedded camera profile (ProfileHueSatMap, ProfileLookTable, ProfileToneCurve, BaselineExposureOffset); RAW INFO panel. (JPEG XL DNGs from the newest iPhones aren't decodable on Android yet.)
- **File-type tags** on the photo and video editors' start-page thumbnails (DNG RAW, JPEG, PNG, HEIC / MP4, MOV, MKV…); not shown inside the editors.
- **DJI drone RAW**: DNGs from DJI drones and Zenmuse cameras with the drone's own lens corrections applied (OpcodeList3 vignetting gain maps, WarpRectilinear distortion / colour fringing, or the DewarpData calibration), the drone's embedded colour profile and tone curve, drone name from the XMP product name or camera code, and flight data in RAW INFO (height, altitude, GPS, gimbal pitch / yaw, heading, speed); HDR and panorama merges develop DNG brackets from the RAW data.
- **Light**: exposure, contrast, highlights, shadows, whites, blacks.
- **Point curves** (RGB, R, G, B) with draggable points.
- **Parametric tone curve** (highlights, lights, darks, shadows) with contrast pivot.
- **Color**: temperature, tint, saturation, vibrance, dehaze, fade.
- **HSL mixer** (8 colour bands: hue, saturation, luminance).
- **Colour grading wheels** for shadows, midtones and highlights; lift / gamma / gain.
- **Detail**: sharpen, noise reduction, **AI noise reduction** (built-in NAFNet model), clarity, texture, vignette, grain.
- **Crop & format**: common aspect ratios, rotate, flip, straighten, zoom.
- **Perspective** correction (vertical / horizontal keystone).
- **Lens corrections**: distortion, vignetting, colour fringes.
- **Masks**: linear gradient, radial and brush, each with its own light and colour settings.
- **Heal / spot removal**: tap to remove dust, blemishes and small objects; **clone stamp** for exact copies from a source you pick.
- **AI masks**: subject and sky selected automatically.
- **Colour-range and brightness-range masks**: tap the photo to pick a colour or brightness (e.g. only the blues, only the highlights), with range and softness.
- **Background (AI)**: portrait blur, transparent background or colour fill behind the subject.
- **Sky replacement (AI)**: 5 built-in skies or your own picture, with strength and light matching.
- **AI erase**: paint over bigger objects to remove them.
- **Merge photos**: HDR (exposure fusion), panorama stitching, focus stacking.
- **Watermark**: text or logo, font, colour, size, position, opacity; optional **Remember watermark**.
- **Text & stickers**: any number of text and emoji layers with font, colour, shadow / box, size, position and opacity.
- **History & snapshots**: jump to any step, save named versions and switch between them.
- Live RGB **histogram** as a pull-out drawer on the right edge (same as the video editor) with waveform, RGB parade and vectorscope, **hold for before**, **undo / redo**.
- **Before / after split view** with a draggable divider.
- **My LUTs**: import .cube LUTs, blend by strength.
- **My presets**: save your look and reuse it (also in the video editor).
- **Real-time preview** while dragging sliders.
- **Copy / Paste settings** (choose what to paste) — also between photos and video clips.
- **LOAD / SAVE projects** next to Export.
- Export **JPEG or PNG**, quality and size choice; choose the **save folder** (default DCIM/VeyroCutPro).
- Also **WebP** and **HEIC**; Instagram / web / 4K sizes; **AI upscale** 2× / 4× in the export; keep or remove **camera info and location**.
- **IPTC title, caption & keywords** in every export, and an optional **Lightroom / Camera Raw .xmp sidecar** with the edit.
- **Batch export**: apply the current edit to up to 50 photos at once.
- **Resizable panels** (hold the divider, then drag; double-tap to reset).
- **Panels menu** (icon next to the gear): show / hide the tools panel, copy / paste bar, histogram and before / after buttons — the photo fills freed space and returns to its saved size; presets **Full / View / Edit / Color**; **triple-tap** the icon to restore the original layout. The histogram button (bar-chart icon) choice is now remembered.

---

## Video editor
### Media & timeline
- **Skip to the editor** from the start page: open an empty timeline without picking a video first (manual, every time).
- Add clips from the gallery or files (MP4, MKV, MOV, WebM, SD card / USB).
- Export to internal storage, an SD card / USB drive or a picked folder on any external drive (own SAVE LOCATION, or the same as capture).
- **4:2:2 / 4:4:4 footage**: detected from the stream, decoded with whichever decoder on the phone can read it (hardware, then Android's software decoder), plays directly (with the right decoder) or from a proxy, export from the original.
- **Media pool** with codec, HDR / 10-bit badges; delete from the pool with confirmation.
- **Multi-track timeline** starting at the left edge: filmstrips, layer rows and audio rows; drag the playhead or tap the ruler; two-finger pinch zoom and scroll; follows the playhead while playing.
- Under the player: **mute, zoom out, show whole timeline, zoom in, split, undo, redo**, previous / play / next, full screen.
- **Trim, split, duplicate, reorder, speed, delete** clips.
- **Real audio waveforms** on clips and audio tracks.
- **Markers** with notes (list on the Media page, jump to any marker) and **snapping** (cuts, markers, playhead, other items).
- **Speed ramps** (8 curves), **freeze frame**, **reverse**, **detach audio** for J- and L-cuts.
- **Beat markers** found from the music.
- **Remove silences**: cut the pauses out of one clip or all clips (silence level and minimum pause adjustable; undo restores).
- **Track headers**: mute the video track or any audio track, show / hide layers, **lock** tracks against edits.
- **Hold-and-drag on the timeline**: move layers and audio in time, drag layers up / down to reorder, hold an edge to trim or extend.
- **Audio in the media pool** (MP3, WAV, AAC…): add to the timeline at the playhead — the first becomes the music track, more become extra audio tracks with volume and fades.
- Gapless clip-to-clip playback; **full-screen player** with auto-hiding controls.
- **Proxy media**: a Proxy section next to Media — only media added there plays from a proxy; Proxy ON / OFF switch; 360p, 540p, 720p or 1080p (H.264 or HEVC), remade in the background when the quality changes; background generation with progress and cancel; proxies linked to their originals by a stable media ID and checked for matching length, frame rate, orientation and audio, so switching never shifts edits; missing, unfinished or failing proxies fall back to the original (never a blank frame); lighter timeline thumbnails and filmstrips from proxies; exports always use the originals.
- Grade preview drawn on screen (Android 13+) for smooth, instant playback; exports use the full pipeline.

### Pro editing
- **Multicam**: 2–4 angles synced by sound, live switching between them while playing; the cut lands on the timeline as normal clips.
- **Sync by sound**: external recorder / wireless mic audio lined up with a clip automatically (waveform cross-correlation).
- **Trim tools**: slip, roll and slide by 1 or 10 frames; normal trims ripple (magnetic main track).
- **Nested (compound) clips**: join several clips into one and open them up again.
- **Drone telemetry**: DJI .SRT flight data (altitude, speed, GPS, camera settings) as a live read-out matched to trim and speed.
- **Auto-save & crash recovery** every 20 seconds; auto-saved edits keep access to their media, and RECOVER checks the edit first and leaves out files that can no longer be read.
- **Crash reports**: after an unexpected close, the next start shows a short report (no photos or videos) to share or copy.

### Transitions
Two sub-menus on the Transitions page: **Video transitions** and **Audio transitions**.

**Video transitions (50)**
- **Basic**: cross dissolve, dip to black, dip to white, flash, blur dissolve.
- **Wipes & shapes**: wipe left / right / up / down, diagonal, iris, clock, blinds, barn door, mosaic.
- **Motion**: push left / right / up / down, whip pan left / right, zoom in, zoom out, spin.
- **Stylized**: light leak, ink bleed, sparkle dissolve, glitch, pixelate, chromatic, ripple, light speed, swirl.
- **Cinematic** (13, original shaders written for VeyroCut Pro): film burn, anamorphic flare, lens warp, letterbox close, aperture (iris blades), smoke, shatter, kaleidoscope, cube turn (3D), card flip (3D), sun rays, neon edges, ember drift.
- **More glitches** (Stylized): Glitch 2 · Datamosh, Glitch 3 · VHS rewind, Glitch 4 · Pixel sort.
- Animated previews with your own clips, adjustable duration, apply to all cuts.

**Audio transitions (8)**
- Match video (default), hard cut, constant gain, constant power, exponential fade, dip to silence, quick out / slow in, slow out / quick in — each with a small volume-curve picture.
- Work over the video transition, or on a plain cut as a fade out / in around the cut (adjustable 0.1–3 s); in the preview and the export; apply to all cuts.

**Sound effects on cuts (24)**
- **Hits & impacts**: cinematic impact, cinematic boom, trailer hit, braam, drum hit, punch, thunder, heartbeat, sub drop.
- **Whooshes & air**: whoosh, fast swoosh, deep whoosh, swirl, wind gust.
- **Builds & risers**: riser, reverse cymbal, clock tick build, tape stop.
- **Stylized & sci-fi**: glitch, record scratch, laser zap, magic shimmer, camera shutter, TV static.
- Made by the app itself (no downloaded audio). Impacts land on the cut; risers and reverse cymbals build up to it. Effect volume, ▶ listen, heard in the preview and mixed into the export.
- **My sounds**: import your own transition sounds (MP3, WAV, M4A, AAC, OGG, FLAC); they're copied into the app, can be used on any cut, and can start on, centre on or end on the cut. ✕ removes one.

### Color
- Log input transforms: **D-Log, D-Log M, S-Log3, V-Log, LogC3, HLG, Flat**.
- Wheels (lift / gamma / gain), primaries, curves, HSL, detail, effects, looks; scope drawer (histogram, waveform, RGB parade, vectorscope); copy grade to all clips.
- **My LUTs** (.cube) as a creative look or as the log → Rec.709 input conversion; **my presets** shared with the photo editor.
- **AI noise reduction** per clip (built-in NAFNet), applied on export with a stoppable progress badge.
- **Colour masks** (up to 3 per clip): gradient, radial, colour range and brightness range, each with its own grade; handles on the player; live preview and export.

### Crop, zoom & stabilization
- **Crop & zoom on the player** (pinch, drag, twist) with thirds grid; zoom 0.25×–4×, position, rotate, flip.
- **Ken Burns** animation from a start framing to an end framing.
- **Anamorphic desqueeze** per clip (preview and export).
- **Auto-reframe** (AI) to 9:16, 1:1 or 4:5 — the picture follows the subject.
- **AI slow motion** by frame interpolation (RIFE via Cloud AI).
- **Stabilization**: motion analysis with smooth or locked (tripod) modes, smoothness and auto-crop.

### Layers
- **Text, stickers, watermark, logo, image and video (picture-in-picture)** layers with timing, position, size and opacity.
- **Unlimited video layers, above or below the main video**: + VIDEO on the timeline asks where the new video goes; above = on top (picture-in-picture or full frame), below = behind your clips (shows where the main video doesn't cover the frame). Switch later under PLACEMENT; reorder by dragging rows; rows above V1 for layers on top, under V1 for layers below (marked ↑ / ↓); same stacking in the preview and the export.
- **Keyframes** for layer position, size and opacity (smooth easing; diamonds on the timeline).
- **Motion tracking**: a layer follows a moving object from the playhead (on-device patch tracking → position keyframes).
- **Adjustment layers**: a grade over everything under them for their time range, with strength (up to 3 at a time).
- **Blend modes** (Screen, Multiply, Overlay, Add, Lighten, Darken, Soft light, Difference) for picture, text and sticker layers.
- **Chroma key** (green / blue / black / white screen) for video and image layers, with tolerance and edge softness.
- **AI captions** from the speech in the edit (Whisper via Cloud AI), boxed / clean / big styles.
- **Layer animations**: in / out — fade, slide up / down / left / right, pop, zoom, bounce, wipe reveal, typewriter — with adjustable length.
- **Rename layers** (e.g. name your watermarks) — names show in the layer list and on the timeline.

### Audio
- **Sound libraries** (Media page): built-in **Soundtracks** — 26 original cinematic tracks owned by the developer and cleared for use in your videos — with listen-before-adding preview; **Nature sounds** section ready for more. Tracks behave like imported music (timeline, waveform, fades, ducking, export).
- **Music track** with start point, volume, fades and **ducking** under clips.
- **Voiceover** recording onto its own audio track.
- **Volume keyframes** per clip.
- Per clip: volume, pan, channels (stereo / mono / left / right / swap), fades.
- **Voice noise reduction** per clip (Light / Strong, on export).
- **3-band EQ**, low cut, voice enhance, **compressor, noise gate, limiter**; presets (Voice, Interview, Wind cut, Warm, Bright, Bass boost, Radio, Loud & punchy); live **level meter**.

### Deliver
- **Audio only** export to Music/VeyroCutPro as AAC .m4a, WAV (16 / 24-bit, 32-bit float), AIFF 24-bit, FLAC (lossless) or Opus .ogg — all licence-free formats; WAV / AIFF / FLAC are written by the app's own encoder from the finished mix (lossless).
- **Track switches & trim handles**: per-row mute, eye (hide a layer from the feed and the export) and lock; bracket-style trim handles on both ends of every V1 clip, layer and audio bar — hold and drag to lengthen or shorten within the video, with a live length read-out.
- **Timeline scroll wheel**: a slim glowing wheel in its own window on the timeline's right edge (as tall as the timeline, growing with the tracks, never covering a track) scrolls the tracks up and down (drag or tap), with an "upscale" effect — pixel blocks resolving and a scan line following the scroll.
- **Keep HDR** (HLG / HDR10, 10-bit HEVC) or convert to SDR.
- Resolution up to **4K UHD**, frame rate, **H.264 / HEVC**, bitrate up to very high, audio on/off.
- Project formats: Original, 16:9, 9:16, 1:1, 4:5, 4:3 and more; fill or fit.
- **Export presets** (YouTube 4K / 1080p, Instagram Reel / 4:5, TikTok, Square, small file).
- **Export a range** (in / out) and **save a frame as a photo**.
- **Estimated file size** shown live as you change the settings.
- **Loudness normalisation**: −14 / −16 / −23 LUFS (measured per BS.1770, then the whole mix is set to the target, with a limiter).
- Choose the **save folder** (default DCIM/VeyroCutPro).
- **Animated GIF** export (320–640 px, 10–15 fps, up to 20 s) with grade and layers.
- **Subtitles (.srt)** from the AI captions.
- **Send to desktop editor**: EDL (CMX 3600: cuts, dissolves, speed) and FCPXML 1.9 (cuts, markers) for DaVinci Resolve, Premiere Pro, Avid and Final Cut Pro.

### Projects & workflow
- **LOAD / SAVE projects** next to Export; **Save & leave** when exiting; saved projects on the start page.
- **Copy / Paste settings** between clips (or from a photo), optionally onto every clip.
- **Remember watermark**; **resizable panels**; **playback info** read-out for troubleshooting.
- **Panels menu** (icon next to the gear): show / hide the tools panel, transport bar, histogram, timecode and audio waveforms — the player and timeline grow into freed space and return to their saved sizes; presets **Full / Cut / Color / Audio**; **triple-tap** the icon to restore the original layout.

---

## Cloud AI (optional)
- Bring your own **fal.ai** account (API key stored encrypted on the phone; only sent to fal.ai).
- **Photos**: Topaz Labs upscale (Standard V2) for AI 24/36/48 MP and Topaz Denoise (Normal / Strong / Extreme / Max) for AI noise reduction.
- **Video**: Topaz **Nyx** video noise reduction (Nyx Fast, Nyx, Nyx HF, Nyx XL) when exporting — minutes instead of up to an hour on the phone; results cached so re-exports don't pay twice.
- **Wi-Fi only** option; animated badge shows upload / processing / download with **Stop**; automatic fallback to the on-phone AI.
- Also used for: **AI captions** (Whisper), **AI slow motion** (RIFE), **AI erase** (Bria Eraser), **subject / sky selection** (BiRefNet / EVF-SAM2).
- Found in Capture settings and in both editors' settings.

## Look & feel
- Same dark "Pro" design across Capture and both editors.
- **App colour** picker available in Capture settings and in both editors' settings.
