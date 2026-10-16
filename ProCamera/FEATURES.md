# AcluxPro — Features

AcluxPro is a pro camera, photo editor and video editor for Android phones and tablets (Android 8.0+).
App ID `com.dronedirector.acluxpro`. Downloads are on the [Releases page](https://github.com/DroneDirector/Android-Projects/releases); see the [changelog](CHANGELOG.md) for what changed in each version.

---

## Start screen
- **Capture** and **Editor** tiles over an animated aperture background, in the app colour.
- **Editor hub** to choose the Photo editor or the Video editor.
- **Startup choice**: always open in Capture, the Photo editor or the Video editor (set in each one's settings; untick to see the start screen again).
- App **version** shown bottom-left; animated **Exit** button bottom-right closes the app.

## Capture
### Modes
- **Photo**, **Pro** (photo), **Video**, **Pro Video** and **Live** streaming.
- Smart modes: **Panorama**, **Time-lapse**, **Hyperlapse**, **Slow-mo**, **Super slow-mo**, **Burst**.

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
- **Stabilization** (off by default).
- **Cinema clip naming** (camera / reel / clip) or date & time.
- **Clean feed** (hide the on-screen controls for an HDMI / screen mirror).

### Pro controls
- Manual **ISO, shutter, focus, white balance** (presets 3200K–7500K and more), **exposure compensation** and **zoom**.
- Analysis: **zebras**, **false colour**, **focus peaking**, **histogram** (also in Pro Video when the phone can't add an analysis stream).
- **Composition grids** (3×3, phi, 4×4, crosshair) and **frame guides** (2.39, 1.85, 4:3, 1:1, 9:16).

### Audio
- **Manual input gain** with limiter (applied to finished clips).
- **Headphone monitoring** with live gain, limiter and volume; audio level graph.

### Live streaming
- Stream to **YouTube, TikTok, Twitch, Facebook** or any **RTMP / RTMPS / SRT** server; **H.264 or H.265**.

### Gallery
- Full-screen in-app gallery for photos and videos; **Favourites**; separate **RAW** tab with badges.
- **Colour grading** for photos and videos straight from the gallery.

### Settings
- Save to phone storage or SD card; **app colour** (accent) picker; layout and control options.

---

## Photo editor (Lightroom-style)
- Open from the gallery, files or recent AcluxPro photos; **saved projects** row on the start page.
- Panels: **Presets, Light, Curve, Color, HSL Mixer, Grading, Detail, Effects, Crop & format, Watermark, Export**.
- **Light**: exposure, contrast, highlights, shadows, whites, blacks.
- **Parametric tone curve** (highlights, lights, darks, shadows) with contrast pivot.
- **Color**: temperature, tint, saturation, vibrance, dehaze, fade.
- **HSL mixer** (8 colour bands: hue, saturation, luminance).
- **Colour grading wheels** for shadows, midtones and highlights; lift / gamma / gain.
- **Detail**: sharpen, noise reduction, **AI noise reduction** (built-in NAFNet model), clarity, texture, vignette, grain.
- **Crop & format**: common aspect ratios, rotate, flip, straighten, zoom.
- **Watermark**: text or logo, font, colour, size, position, opacity; optional **Remember watermark**.
- Live **histogram**, **hold for before**, **undo / redo**.
- **Real-time preview** while dragging sliders.
- **Copy / Paste settings** (choose what to paste) — also between photos and video clips.
- **LOAD / SAVE projects** next to Export.
- Export **JPEG or PNG**, quality and size choice; choose the **save folder** (default DCIM/AcluxPro).
- **Resizable panels** (hold the divider, then drag; double-tap to reset).

---

## Video editor (DaVinci Resolve-style)
### Media & timeline
- Add clips from the gallery or files (MP4, MKV, MOV, WebM, SD card / USB).
- **Media pool** with codec, HDR / 10-bit badges; delete from the pool with confirmation.
- **Multi-track timeline**: filmstrips, layer rows and music row, scrubbing playhead, pinch zoom.
- Under the player: **mute, zoom out, show whole timeline, zoom in, split**, previous / play / next, full screen.
- **Trim, split, duplicate, reorder, speed, delete** clips.
- Gapless clip-to-clip playback; **full-screen player** with auto-hiding controls.
- **Preview copies** (optimized media) for heavy HDR / 10-bit / 4K clips, made in the background; choose Auto / Always / Off, H.264 or HEVC, 720p or 1080p.
- Grade preview drawn on screen (Android 13+) for smooth, instant playback; exports use the full pipeline.

### Transitions (34)
- **Basic**: cross dissolve, dip to black, dip to white, flash, blur dissolve.
- **Wipes & shapes**: wipe left / right / up / down, diagonal, iris, clock, blinds, barn door, mosaic.
- **Motion**: push left / right / up / down, whip pan left / right, zoom in, zoom out, spin.
- **Stylized**: light leak, ink bleed, sparkle dissolve, glitch, pixelate, chromatic, ripple, light speed, swirl.
- Animated previews with your own clips, adjustable duration, apply to all cuts; audio crossfades.

### Color
- Log input transforms: **D-Log, D-Log M, S-Log3, V-Log, LogC3, HLG, Flat**.
- Wheels (lift / gamma / gain), primaries, curves, HSL, detail, effects, looks; scope; copy grade to all clips.
- **AI noise reduction** per clip (built-in NAFNet), applied on export with a stoppable progress badge.

### Crop, zoom & stabilization
- **Crop & zoom on the player** (pinch, drag, twist) with thirds grid; zoom 0.25×–4×, position, rotate, flip.
- **Ken Burns** animation from a start framing to an end framing.
- **Stabilization**: motion analysis with smooth or locked (tripod) modes, smoothness and auto-crop.

### Layers
- **Text, stickers, watermark, logo, image and video (picture-in-picture)** layers with timing, position, size and opacity.

### Audio
- **Music track** with start point, volume, fades and **ducking** under clips.
- Per clip: volume, pan, channels (stereo / mono / left / right / swap), fades.
- **3-band EQ**, low cut, voice enhance, **compressor, noise gate, limiter**; presets (Voice, Interview, Wind cut, Warm, Bright, Bass boost, Radio, Loud & punchy); live **level meter**.

### Deliver
- Resolution up to **4K UHD**, frame rate, **H.264 / HEVC**, bitrate up to very high, audio on/off.
- Project formats: Original, 16:9, 9:16, 1:1, 4:5, 4:3 and more; fill or fit.
- Choose the **save folder** (default DCIM/AcluxPro).

### Projects & workflow
- **LOAD / SAVE projects** next to Export; **Save & leave** when exiting; saved projects on the start page.
- **Copy / Paste settings** between clips (or from a photo), optionally onto every clip.
- **Remember watermark**; **resizable panels**; **playback info** read-out for troubleshooting.

---

## Cloud AI (optional)
- Bring your own **fal.ai** account (API key stored encrypted on the phone; only sent to fal.ai).
- **Photos**: Topaz Labs upscale (Standard V2) for AI 24/36/48 MP and Topaz Denoise (Normal / Strong / Extreme / Max) for AI noise reduction.
- **Video**: Topaz **Nyx** video noise reduction (Nyx Fast, Nyx, Nyx HF, Nyx XL) when exporting — minutes instead of up to an hour on the phone; results cached so re-exports don't pay twice.
- **Wi-Fi only** option; animated badge shows upload / processing / download with **Stop**; automatic fallback to the on-phone AI.
- Found in Capture settings and in both editors' settings.

## Look & feel
- Same dark "Pro" design across Capture and both editors.
- **App colour** picker available in Capture settings and in both editors' settings.
