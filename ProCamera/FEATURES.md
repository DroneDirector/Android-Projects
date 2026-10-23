# AlcorCut Pro — Features

AlcorCut Pro is a pro camera, photo editor and video editor for Android phones and tablets (Android 8.0+).
App ID `com.dronedirector.veyrocutpro`. Downloads are on the [Releases page](https://github.com/DroneDirector/Android-Projects/releases); see the [changelog](CHANGELOG.md) for what changed in each version. Open-source parts and licences: [third-party notices](THIRD_PARTY_NOTICES.md).

---

## Start screen
- **Home-screen shortcuts** (start screen → gear, off by default): separate home-screen icons that open Capture, the Photo editor or the Video editor directly.
- **Capture** and **Editor** tiles over an animated aperture background, in the app colour.
- **Editor hub** to choose the Photo editor or the Video editor.
- **Startup choice**: always open in Capture, the Photo editor or the Video editor (set in each one's settings; untick to see the start screen again).
- App **version** shown bottom-left; animated **Exit** button bottom-right closes the app.
- **Open-source licences** and the **privacy policy** in every settings gear ([PRIVACY.md](PRIVACY.md)).
- Separate **guides** for Capture, the Photo editor and the Video editor (Cloud AI key setup, AI features, tips), opened from each settings gear — also in [GUIDE.md](GUIDE.md).

- **Simple / Pro layout**: everyday modes and tools only, or everything (Capture settings and both editors' settings).
- **Backup & restore**: one .vcbackup file with all settings, presets, LUTs, watermarks, sounds and projects.
- **Quick tour** of the camera gestures on first start (replay from Settings).
- **App shortcuts** (long-press the icon: Pro video, Pro photo, Photo editor, Video editor) and a **Quick Settings camera tile**.
- **Open from other apps**: Share / Edit with AlcorCut Pro opens the photo or video editor directly.

- **Languages**: English, Danish (Dansk) and Faroese (Føroyskt), following the phone or chosen in settings.
- **First-launch setup**: the AI models and soundtracks (about 700 MB) are downloaded once on first launch, with a progress screen (asks first on mobile data, resumes after a dropped connection, can carry on in the background); one small APK per phone type.

## Capture
### Modes
- **Photo**, **Pro** (photo), **Video**, **Pro Video** and **Live** streaming.
- Smart modes: **Portrait** (background blur for people, pets, flowers, rocks, branches…; slider f/1.4 – f/16, or AUTO), **AI Portrait** (the same, plus the blur shown live in the viewfinder), **Panorama** (gyroscope-guided ~180°, cylindrical stitching), **Time-lapse**, **Hyperlapse**, **Slow-mo**, **Super slow-mo**, **Burst**.

### Photo
- Photo **size per camera** (megapixel choice), aspect **4:3 / 16:9 / 1:1**.
- **Pro photo file format remembered**: Always JPEG, Always RAW + JPEG or Always RAW only (Settings → PHOTO; one at a time).
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

### Stop motion
- **Stop motion** (photo modes): each photo is the next frame, onion skin over the viewfinder, frame strip (play, hold, delete), 6–24 fps, MAKE VIDEO.

### Teleprompter
- **Teleprompter** over the camera (selfie or back camera): your own text, scrolling at your speed or starting with the recording; bold, italic, underline, narrow, alignment, size, text and background colours with opacity, mirror for teleprompter glass; drag to move, drag the corner to resize, shrink to a small bar, close with ✕; opens by itself with the selfie camera.

### Pro controls
- **Pro read-out bar**: live ISO, shutter, focus, white balance, EV and zoom — tap any to change it — plus the exposure mode (**auto, shutter priority, ISO priority, manual**; Android 15+ uses the camera's own priority modes, older phones are metered continuously by the app) and, in video, resolution · frame rate · bitrate.
- **Fn button**: one assignable button (tap to run, hold to choose) — zebras, peaking, false colour, next scope, AE / AWB lock, split focus / exposure, pull to A / B, frame guide, safe areas, log assist, flip, torch, next lens, focus magnifier, clean feed.
- **Pull points**: A / B points for focus (rack focus), zoom and exposure, glided over 1–8 s, separately or all together.
- **Separate focus and exposure points**: tap = focus, double-tap = exposure (amber sun ring).
- **Record in cinema shapes**: 2.76 (Ultra Panavision), 2.39, 1.85, 4:3, 1:1, 9:16 — the recording is cropped, not just guided.
- **Bitrate tiers**: Economy, Standard, High, Extreme (Extreme and 24-bit audio only on phones strong enough — memory, processor, graphics and the video encoder are checked). **Flip picture** (left ↔ right, viewfinder and recording).
- **NTSC rates**: 23.976 / 29.97 / 59.94 fps by conforming 24 / 30 / 60 fps clips after recording (picture and sound together).
- **Set-up sync**: C1–C3 synced between devices through one file in a cloud folder you choose.
- **Watch remote** (Wear OS — Galaxy Watch 4+, Pixel Watch): live picture, record / shutter, photo ↔ video, zoom.
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
- **24-bit audio**: uncompressed recording saved as a 24-bit WAV next to each clip (the clip gets the same sound).
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
- **Select several** (hold one): share, delete (to the trash), favourite, pick or reject them together.
- **Trash** (Android 11+): deleted items are kept 30 days and can be restored; empty trash.
- **Culling**: 1–5 stars, pick / reject, rating filters and delete-all-rejects.
- Full-screen in-app gallery for photos and videos; **Favourites**; separate **RAW** tab with badges.
- **Colour grading** for photos and videos straight from the gallery.

- **Grade** (photos and videos): looks, light (exposure, contrast, highlights, shadows, whites, blacks), colour, toning, and for photos **Detail** (clarity, texture, dehaze, sharpening, vignette).
### Settings
- **Side buttons**: volume up / down as shutter / record, zoom, or off (normal volume).
- **External monitor layouts** (DeX / HDMI desktop only): 16:9 / 16:10, 21:9 and 32:9 layouts with media / filmstrip, viewer and tools columns; phones and tablets keep their own layouts and switch back when unplugged.
- **Autosave** (photo and video editors): interval Off / 1 / 2 / 5 / 10 / 15 / 30 min and 1–5 kept copies, listed under LOAD → AUTOSAVES.
- **Samsung DeX ready**: resizable windows, wide layouts, mouse and keyboard; the app keeps your edit when you connect a keyboard / mouse or move between the phone and a monitor.
- **Mouse support**: AlcorCut's own glowing pointer (halo, trail, click ripple, orbiting spark), or a plain pointer, or the normal Android arrow. Choose the **pointer colour** and, separately, the **scroll wheel colour** in each screen's gear settings. Wheel, right-click, middle-click and double-click actions in Capture, the Photo editor and the Video editor, plus drag from the media strip to the timeline.
- **Copyright**: your name and copyright written into every photo (camera and photo editor exports).
- **External storage**: save to phone storage, an SD card / USB drive, or a folder you pick on any USB stick, SSD or SD card (videos record straight onto the drive; falls back to internal storage if it is unplugged); **app colour** (accent) picker; layout and control options.
- **Panels menu** (icon next to the gear): show / hide the exposure read-out, histogram, audio meter, level, Pro controls bar and tips; presets **Full / Minimal / Video**; **triple-tap** the icon to restore the original layout.

---

## Photo editor
- Open from the gallery, files or recent AlcorCut Pro photos; **saved projects** row on the start page.
- **Skip to the editor** from the start page: open the editor without picking a photo first, then add one from inside (manual, every time).
- **Pro develop**:
  - **Library**: all phone photos with stars, flags, colour labels, keywords, albums, smart albums, on-device AI content search, EXIF info, compare (2, linked zoom) and survey (up to 12) views, virtual copies, **contact sheet PDF** (A4 / Letter, 3–6 per row, names and stars), **print layout PDF** (exact print sizes, borders, crop marks), **collage** (5 templates, 2–9 photos), and multi-photo editing in the filmstrip.
  - **Upright** (Auto, Level, Vertical, Full), **dust spot** visualise and auto-heal, **panorama** projections (cylindrical / spherical / perspective), boundary warp and AI edge fill.
  - **Export**: **AVIF** (phone's AV1 encoder, Android 14+) and **JPEG XL**, TIFF 16 / 8-bit, sRGB / Display P3 / Adobe RGB with embedded profile, output sharpening (screen / matte / glossy), file-name templates, export presets, **export recipes** (several presets in one go), a matte-print soft proof and **soft proof with a printer / paper ICC profile** (own colour engine: lut8 / lut16 / lutAtoB / lutBtoA, perceptual or relative colorimetric, paper white).
  - **DNG camera profiles (.dcp)**: import and use instead of the RAW file's own colour (matrices, forward matrices, hue / sat maps, look table, tone curve).
  - **Merges**: HDR, panorama, focus stack, **noise stack**, **remove passers-by** (median of 3–8 shots), **super resolution** (sub-pixel aligned hand-held shots → 2× size) and **star stack** (aligned on the stars, shift + rotation).
  - **Generative expand (Cloud AI)**: wider canvas with the surroundings painted in (Bria Expand via fal.ai; trained on licensed data).
  - **Retouch**: **Liquify** (push, pinch, bloat, twirl, restore), **frequency separation** (tone smoothing + texture, whole photo or AI face / body skin) and **AI Relight** (light direction, shadows, warmth from the depth map).
  - **Crop guides**: thirds, golden ratio, golden spiral, golden triangles, diagonals, grid (turnable).
  - **Masks**: Object (AI) by tapping, Depth range (AI), and add / subtract / intersect combinations (colour, brightness, depth, subject, sky).
  - **Lens Blur** from AI depth: tap-to-focus, focus range, blur amount, bokeh shapes (circle, hexagon, octagon, ring, cat's eye) and bokeh boost.
  - **60 built-in presets** (Portrait, Landscape, B&W, Film, Cinematic, Vintage, Urban, Food, Travel, Moody) with live thumbnails and an **Amount** slider.
  - **.xmp** preset and sidecar import, and .xmp preset export.
  - **Auto tone**, **Auto white balance**, a **WB eyedropper** and **clipping warnings**.
  - **Point Color** and **Calibration** (shadows tint, red / green / blue primaries).
  - Colour grading with **Global, Blending and Balance**.
  - Pro sharpening (**Radius, Detail, Masking** + mask preview) and **Defringe** (purple / green).
  - **Colour noise** reduction (chroma smoothing, strongest in the shadows; keeps real colour edges).
  - **Post-crop vignette** (Midpoint, Roundness, Feather, Highlights) and **grain** (Size, Roughness).
  - **Zoom / 1:1 loupe** (pinch, double-tap, Ctrl + wheel).
  - **AI Remove**: tap people, cars or signs (the AI selects each), or paint over wires, and the AI (LaMa on the phone, or Bria Eraser with Cloud AI) paints in what was behind them at the photo's full size.
  - **People masks (AI)**: face skin, body skin, eye whites, irises, eyebrows, lips, teeth, hair and clothes of everyone in the photo, plus one-tap **portrait looks** (smooth skin, whiten teeth, brighten eyes, iris enhance, lip colour, hair shine, brows). **Texture** and **Clarity** inside every mask.
  - **Keyboard shortcuts** (Bluetooth / USB keyboards, Samsung DeX): photo navigation, stars, flags, colour labels, before / after, zoom, panels, undo / redo, copy / paste the look, save and export; ? shows the list.
  - **Ultra HDR export** (Android 14+): JPEGs with a gain map, so highlights glow brighter on HDR screens (Subtle / Standard / Strong).
  - **AI culling** in the filmstrip: rejects blurry shots and closed eyes, groups similar shots (bursts) and picks the best of each, with notes on the thumbnails.
  - **Gamut warning** for glossy or matte prints, **reference view** with **match look**, **batch rename** of photo files from a template, and **noise reduction inside masks**.
  - Lens **auto colour fringes** (measured) and **lens profiles** by focal length; Library **Stacks**, **People** (by number of faces) and **Map** (offline world map, Natural Earth) views; **colour chart calibration** (24-patch chart → colour matrix); **Refine edge (hair)** for AI masks.
  - **Targeted adjustment**: drag on the photo to change the tone curve or the HSL mixer (hue / saturation / luminance) for exactly the tone or colour under your finger.
- **Multi-import + filmstrip**: pick up to 100 photos at once from the gallery or files; a filmstrip under the photo switches between them, and every photo keeps its own edit automatically. Star ratings, Pick / Reject flags, colour labels, filters (All, ★3+, Picks, Edited, No rejects), multi-select **Sync edit** (colour, light, detail, lens, RAW develop), batch rate / flag, **export selected** (each with its own edit) and remove. "Continue filmstrip" on the start page; hide it from the Panels menu. Switching photos never shows a black viewer: the last render (kept in memory and on disk), the saved thumbnail, or the DNG's embedded camera preview colour-matched to the editor's own develop shows instantly while the RAW renders, then fades over; neighbouring photos are decoded and rendered ahead. Filmstrip thumbnails are cached in memory and on disk, and RAW thumbnails use the DNG's embedded preview, so the strip fills instantly.
- Panels: **Presets, Light, Curve, Color, HSL Mixer, Grading, Detail, Effects, Crop & format, Watermark, Export**.
- **RAW (DNG) develop**: the editor reads the sensor data itself (uncompressed and lossless-compressed DNGs, lens-shading maps, the camera's colour matrix) — exposure ±5 stops, highlights, shadows, whites, blacks and white balance work on the full-range data with a soft highlight roll-off; **RAW profiles** (Standard, Flat, Vivid, Portrait, Landscape, Mono) and **white balance presets** (As shot, Auto, Daylight, Cloudy, Shade, Tungsten, Fluorescent); full-quality demosaic on export; RAW files in a batch export are developed too.
- **Full DNG support**: uncompressed, lossless-JPEG, lossy-JPEG and Deflate DNGs; 8–32-bit integer and 16 / 24 / 32-bit floating-point (HDR) data with all TIFF / DNG predictors; Bayer, X-Trans, Quad Bayer and any other RGB colour filter pattern, plus linear (demosaiced) DNGs; strips or tiles; linearisation tables, per-row / column black levels, active area, default crop and DefaultUserCrop; all OpcodeList1 / 2 / 3 corrections (bad pixels, map table / polynomial, per-row / column delta and scale, lens-shading gain maps, WarpRectilinear distortion + lateral chromatic aberration, FixVignetteRadial); full colour model (dual-illuminant ColorMatrix / CameraCalibration / ForwardMatrix interpolated by the white, AnalogBalance, AsShotNeutral / AsShotWhiteXY); the embedded camera profile (ProfileHueSatMap, ProfileLookTable, ProfileToneCurve, BaselineExposureOffset); RAW INFO panel. JPEG XL compressed DNGs (DNG 1.7 — newer Samsung Galaxy phones and iPhones) and black-and-white (monochrome) DNGs.
- **File-type tags** on the photo and video editors' start-page thumbnails (DNG RAW, JPEG, PNG, HEIC / MP4, MOV, MKV…); not shown inside the editors.
- **RAW looks** (top of the Light panel, a folded menu): Default (the app's own RAW look, unchanged), Match camera picture (tone curve and colour strength fitted to the preview stored in the file), and fold-out sub-menus — Phones (Pixel, Galaxy, iPhone style), Drones (DJI camera picture, Parrot, Autel style), Cameras (Canon, Sony, Nikon, Fujifilm, Leica, Ricoh, Panasonic, Olympus / OM style), Action & 360° (YI) — each a scrollable row of small previews of the photo. The looks were measured by the RAW test from how each maker renders its own RAW files. Saved with the edit, synced and kept in presets.
- **Other cameras' RAW formats**: Sony ARW, Canon CR2 / CR3, Nikon NEF, Fujifilm RAF (X-Trans), Olympus / OM System ORF, Panasonic RW2, Pentax PEF, Samsung SRW, Hasselblad 3FR / FFF, Phase One IIQ and more open in the photo editor, read by LibRaw (LGPL 2.1, its own replaceable library) and developed by the app's own RAW engine; the starting brightness is matched to the camera's own picture; embedded camera previews show instantly. Nikon High Efficiency RAW and Sigma Foveon files show a clear "not supported yet" message.
- **Automatic RAW test**: every change to the RAW code develops 60+ sample files from drones (DJI, Hasselblad, Autel, Parrot, Yuneec, FIMI), cameras (Sony, Canon, Nikon, Fujifilm, Olympus, Panasonic, Pentax, Leica, Ricoh, Sigma) and phones (iPhone, Pixel, Galaxy, Xiaomi, OnePlus, Huawei) and compares each with the camera's own preview (public-domain samples from raw.pixls.us, used only for testing).
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
- **AI Remove**: tap or paint over bigger objects to remove them.
- **Merge photos**: HDR (exposure fusion), panorama stitching, focus stacking.
- **Watermark**: text or logo, font, colour, size, position, opacity; optional **Remember watermark**.
- **Text & stickers**: any number of text and emoji layers with font, colour, shadow / box, size, position and opacity.
- **History & snapshots**: jump to any step, save named versions and switch between them.
- Live RGB **histogram** as a pull-out drawer on the right edge (same as the video editor) with waveform, RGB parade and vectorscope, **hold for before**, **undo / redo**.
- **Before / after split view** with a draggable divider.
- **My LUTs**: import .cube LUTs, blend by strength.
- **My presets**: save your look and reuse it (also in the video editor).
- **Real-time preview** while dragging sliders; the viewer shows the photo at full screen resolution (full size when zoomed in).
- **Copy / Paste settings** (choose what to paste) — also between photos and video clips.
- **LOAD / SAVE projects** next to Export; SAVE lists your saved projects to overwrite one, or save as new.
- Export **JPEG or PNG**, quality and size choice; choose the **save folder** (default DCIM/AlcorCutPro).
- Also **WebP** and **HEIC**; Instagram / web / 4K sizes; **AI upscale** 2× / 4× in the export; keep or remove **camera info and location**.
- **IPTC title, caption & keywords** in every export, and an optional **.xmp sidecar** (Camera Raw settings) with the edit.
- **Batch export**: apply the current edit to up to 50 photos at once; photo exports (single, batch and export selected) keep running in other apps or with the screen off, with a progress notification.
- **Resizable panels** (hold the divider, then drag; double-tap to reset).
- **Panels menu** (icon next to the gear): show / hide the tools panel, copy / paste bar, histogram and before / after buttons — the photo fills freed space and returns to its saved size; presets **Full / View / Edit / Color**; **triple-tap** the icon to restore the original layout. The histogram button (bar-chart icon) choice is now remembered.

---

## Video editor
### Media & timeline
- **Delete from the timeline**: a bin on the selected clip; choose Close gap (ripple) or Leave gap (black of the same length).
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

- **Media strip** (always on screen above the timeline, can be hidden in the Panels menu): every imported clip, used on the timeline or not. Mouse-hover or hold-and-slide **skimming**, an amber **USED ×n** mark, **1–5 star ratings and reject**, filters (All, Unused, Used, ★3+, No rejects, bins), multi-select (append, rate, reject, remove), Append / Insert / Overwrite / Source viewer, **+ Clips** (up to 100 at once) and **+ Folder** (every video in a folder and its sub-folders). With a mouse, drag a clip straight onto the timeline.
### Pro editing
- **Text-based editing**: the edit as text, transcribed on the phone (whisper.cpp, MIT; Whisper model downloaded once — Fast 57 MB / Accurate 181 MB; 99 languages incl. Danish and Faroese). Tap words to cut them, REMOVE FILLERS (um / uh / øh), APPLY CUTS splits the clips without leaving dead air; undo restores; hold a word to jump there.
- **Clip keyframes**: zoom, position, rotation and opacity over time, Smooth / Linear / Hold easing or a **Bézier curve** (graph editor with presets), diamonds on the timeline, edit on the player at the playhead; preview and export.
- **Scene cut detection**: finds the camera cuts in a long clip or finished film and splits it into shots.
- **Multi-select**: tick clips to delete (ripple), mute, change speed, duplicate or copy a grade in one go.
- **Auto-edit to the beat**: rebuilds the timeline from your clips with a cut on every 1st / 2nd / 4th / 8th beat.
- **Fit music to video**: the song ends exactly with the video on its real ending — a beat-aligned middle part is cut out or repeated.
- **Before / after split** on the player (Color page, Android 13+) with a draggable divider.
- **Project version history**: a version on every save and every 10 minutes (newest 30), RESTORE any of them.
- **YouTube chapters** from timeline markers, copied ready for the description.
- **Rolling credits**: Credits layer and a Roll animation for any text layer.
- **Keyboard shortcuts** (Bluetooth / USB): J / K / L, Space, frame steps, cut to cut, split, ripple delete, markers, in / out, undo / redo, save.
- **Timeline scroll wheels**: a vertical wheel (tracks up / down) and a horizontal wheel along the bottom (through the edit), each in its own window, with the AI-upscale effect.
- **Shot match**: one tap sets a clip's exposure, white balance, contrast and saturation to match another clip as graded.
- **Film emulation**: 35mm / 16mm / Super 8 looks, halation, bloom, grain (amount and size), gate weave.
- **Lens correction**: barrel / pincushion distortion, colour fringing and vignette fix, with action cam, drone and ultra-wide presets.
- **Deflicker** for timelapses, hyperlapses and flickering lights (scene cuts left alone).
- **Background removal & Magic Mask**: people found automatically (ML Kit), or Magic Mask — tap / box any object and it is tracked through the clip (MobileSAM, Apache 2.0, via ONNX Runtime, MIT; own optical-flow tracking with close-up re-fitting, occlusion and scene-cut handling); blur, recolour, remove or invert; edges refined against the full picture; live preview matches the export; cut-outs for video layers.
- **Generators**: black, colour, gradient, noise and colour-bars clips, and a film countdown leader (3–10).
- **Split-screen templates** (2 side by side, 2 stacked, 3 columns, 2 × 2) and **picture in picture**; video layers get a frame shape (crop to fill), rounded corners, border and drop shadow.
- **Morph cut**: own optical-flow engine draws new in-between frames for jump cuts — the frames are rendered through each clip's own grade and effects at full size.
- **Smarter filler removal**: um / uh, repeats & stutters, and optional filler phrases (you know, like) — the transcript now keeps what was really said.
- **Auto podcast / interview switching** (Multicam → AUTO: WHO'S TALKING): cuts to whoever is speaking from each angle's own microphone, with an optional wide shot for cross-talk and pauses.
- **Auto highlights**: a 15 s – 2 min reel of the liveliest moments (sound bursts, movement, lively talk, sharp shots, faces), cut between words or on the music's beat.
- **Optical-flow slow motion** on the phone (own motion engine): ½ / ¼ / ⅛ speed or the clip's own ramp with new in-between frames.
- **Motion blur** from the motion (90° / 180° / 360° shutter).
- **Corner pin & planar tracking**: put any layer in perspective on a screen, sign or wall and keep it stuck there as the camera moves (own tracker: Lucas–Kanade + RANSAC homography).
- **Shape layers** (rectangle, rounded, ellipse, line, arrow, triangle, star, heart; filled or outline) and **Lottie animation layers** (.json / .zip; lottie-android, Apache 2.0).
- **Shape (pen) masks** on clips: a smooth curve with a soft edge and its own grade, planar-tracked to follow what it covers.
- **Object removal (AI)**: tap anything and it's removed from the whole clip — filled from the real background in nearby frames (own optical flow), with LaMa (Apache 2.0) painting only what's never seen.
- **AI depth** (Depth Anything V2 Small, Apache 2.0): lens blur with real depth of field, fog / haze by distance, or a darker, calmer background.
- **AI voiceover from text**: 12 natural English voices (Kokoro 82M, Apache 2.0; misaki dictionaries), made on the phone and added as an audio track.
- **Studio voice (AI)**: DeepFilterNet3 (MIT / Apache 2.0) voice clean-up; **de-click** and **de-reverb** (own code).
- **Caption translation** on the phone (Google ML Kit) into 19 languages.
- AI models are downloaded the first time a feature needs them (Depth 99 MB, Object removal 208 MB, Voiceover 105 MB), so the app stays smaller.
- **Word-by-word captions** from the on-phone transcript: Highlight, Box, One word or Two lines.
- **Animated title templates**: lower third, big title, cinematic, location, chapter, subscribe, end card.
- **Source viewer & three-point editing**: mark in / out, then INSERT, OVERWRITE or APPEND at the playhead.
- **Bins & colour labels** in the media pool and timeline; **relink missing media**; **project archive** (.zip with all media) to move or back up a project.
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
- **Cinematic** (13, original shaders written for AlcorCut Pro): film burn, anamorphic flare, lens warp, letterbox close, aperture (iris blades), smoke, shatter, kaleidoscope, cube turn (3D), card flip (3D), sun rays, neon edges, ember drift.
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
- **Voice noise reduction** per clip (Light / Strong / **Voice isolation (AI)** — RNNoise, BSD-3, on the phone; on export).
- **3-band EQ**, low cut, voice enhance, **compressor, noise gate, limiter**; presets (Voice, Interview, Wind cut, Warm, Bright, Bass boost, Radio, Loud & punchy); live **level meter**.
- **Parametric EQ**: up to 8 bands (bell, shelves, low / high cut) dragged over a live spectrum; **de-esser**.
- **AI dubbing**: captions spoken as one audio track at their times (English on the phone with Kokoro; other languages via Cloud AI, ElevenLabs Multilingual v2), original voice ducked or muted.
- **Dialogue leveller**: evens out speech (quiet words up, loud words down, pauses left alone).
- **Mixer**: a strip per track (clip sound, music, extra tracks) with live meter, level (dB), pan, mute and solo, plus master level — in the preview and the export.
- **LUFS meter** (EBU R128 / BS.1770): momentary, short-term and integrated loudness while playing.

### Deliver
- **Background export**: keeps running in other apps or with the screen off (foreground service, progress notification, "finished" notification).
- **Background tasks notification**: one notification in the pull-down shade lists every background task with its progress — photo and video exports, edit sync, proxies, AI noise reduction, AI upscale, AI cull, Cloud AI and first-launch downloads.
- **Render queue**: several export settings of the same edit rendered one after another (✓ per finished job, stop any time).
- **Audio only** export to Music/AlcorCutPro as AAC .m4a, WAV (16 / 24-bit, 32-bit float), AIFF 24-bit, FLAC (lossless) or Opus .ogg — all licence-free formats; WAV / AIFF / FLAC are written by the app's own encoder from the finished mix (lossless).
- **Track switches & trim handles**: per-row mute, eye (hide a layer from the feed and the export) and lock; bracket-style trim handles on both ends of every V1 clip, layer and audio bar — hold and drag to lengthen or shorten within the video, with a live length read-out.
- **Timeline scroll wheel**: a slim glowing wheel in its own window on the timeline's right edge (as tall as the timeline, growing with the tracks, never covering a track) scrolls the tracks up and down (drag or tap), with an "upscale" effect — pixel blocks resolving and a scan line following the scroll.
- **Keep HDR** (HLG / HDR10, 10-bit HEVC) or convert to SDR.
- Resolution up to **4K UHD**, frame rate, **H.264 / HEVC**, bitrate up to very high, audio on/off.
- Project formats: Original, 16:9, 9:16, 1:1, 4:5, 4:3 and more; fill or fit.
- **Export presets** (YouTube 4K / 1080p, Instagram Reel / 4:5, TikTok, Square, small file).
- **Export a range** (in / out) and **save a frame as a photo**.
- **Estimated file size** shown live as you change the settings.
- **Loudness normalisation**: −14 / −16 / −23 LUFS (measured per BS.1770, then the whole mix is set to the target, with a limiter).
- Choose the **save folder** (default DCIM/AlcorCutPro).
- **Animated GIF** export (320–640 px, 10–15 fps, up to 20 s) with grade and layers.
- **Subtitles** from the captions as **.srt** or **WebVTT (.vtt)**; **import .srt / .vtt** files as caption layers.
- **Review copy**: burned-in timecode, watermark text and marker notes on screen.
- **Render cache**: heavy AI effects (noise reduction, background, depth) rendered once for smooth playback; export always from the originals.
- **Safe zones** on the viewer: TikTok, Reels, Shorts (approximate) and TV action / title safe — never exported.
- **Smart render (fast copy)**: cut-only edits are copied from the clips without re-encoding — no quality loss, much faster (each clip starts on its nearest earlier key frame).
- **Send to desktop editor**: EDL (CMX 3600: cuts, dissolves, speed) and FCPXML 1.9 (cuts, markers) and **OpenTimelineIO .otio** (cuts, dissolves, speed, markers) for desktop editing software.

### Projects & workflow
- **Several timelines per project**: new, duplicate, rename and switch between cuts of the same project (shared media strip).
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
- **Folded settings menus**: the gear menus in Capture, the photo editor and the video editor group every setting in fold-out sections (e.g. Photo, Video, Audio, AI, Saving & backup, App); each line shows a short list of what is inside.
- **Quick tours**: Capture, the photo editor and the video editor each have a short first-run tour (skip, don't show again, or replay from the gear → App).
- **Fold-out tool sections**: in the photo and video editors every tool panel section opens and closes (title + a line listing what's inside), inside the sliding tabs; gear → Layout & look sets how they start (remember, always closed, all open).
- **Gyro stabilisation** in the video editor from Capture's motion file (.gcsv), lined up with the picture automatically.
- **HDR levels in nits** (HLG / HDR10): nits waveform from the real 10-bit frame, 100 / 203 / 1000-nit guides, peak / average / above-203 / above-1000.
- **Track groups (VCA)** in the mixer: Dialogue, Music, Effects — group fader and group mute.
- **Export a look as a .cube LUT** (33-point) from the photo or video editor, also added to My LUTs.
- **Luma key** for video and picture layers (remove dark / remove bright, level, softness).
- **Keep settings after uninstall** (optional): settings, presets and LUTs saved to Documents/AlcorCutPro/Settings; restore from the first screen after reinstalling.
