# AcluxPro — Changelog

All notable changes per released version. The full feature list is in [FEATURES.md](FEATURES.md).
Downloads: [Releases page](https://github.com/DroneDirector/Android-Projects/releases).

## Unreleased

## 1.0.155 — 2026-10-16
- Privacy policy (PRIVACY.md) linked from every settings gear; Google Play data-safety answers prepared (DATA_SAFETY.md).
- Open-source licences: listed in every settings gear (© Open-source licences) with the full Apache 2.0 and MIT texts; THIRD_PARTY_NOTICES.md on GitHub. Trademark note added.

## 1.0.152 — 2026-10-16
- Video editor: own LUTs now have their own tab — Color → LUTs → + IMPORT (.cube files).

## 1.0.150 — 2026-10-16
- Video editor: AI captions — timed caption layers from speech (Whisper via your fal.ai account), 9 languages incl. Faroese and Danish.
- Video editor: auto-reframe to 9:16, 1:1 or 4:5 — clips follow their subject (on-phone AI).
- Video editor: AI slow motion — frame interpolation (RIFE via Cloud AI).
- Photo editor: AI subject and sky masks; background blur (portrait), removal (transparent PNG) or colour; AI erase for objects (Bria Eraser via Cloud AI, or on the phone).

## 1.0.147 — 2026-10-16
- Photo editor: masks — linear gradient, radial and brush — each with its own exposure, contrast, highlights, shadows, temperature, tint, saturation and dehaze.
- Photo editor: heal / spot removal, perspective (vertical / horizontal keystone), lens corrections (distortion, vignetting, colour fringes).
- Photo editor export: WebP and HEIC, Instagram / web / 4K sizes, AI 2× / 4× upscale, keep or remove camera info and location, batch export of up to 50 photos.
- Photo editor: merge photos — HDR (exposure fusion), panorama stitching and focus stacking.

## 1.0.142 — 2026-10-16
- Capture: focus magnifier while focusing manually, and rack focus (set A / B, glide in 1–4 s).
- Capture: waveform, RGB parade and vectorscope as alternatives to the histogram.
- Capture: pre-record (keeps 3, 5 or 10 seconds from before you press record).
- Capture: wind filter, Bluetooth microphone option with a list of connected microphones, and left / right level meters.

## 1.0.139 — 2026-10-16
- Video editor: keyframes for layers (position, size, opacity) with diamonds on the timeline, and volume keyframes for clips.
- Video editor: speed ramps (Montage, Hero, Bullet, Flash in / out, Jump cut, Slow in / out) in preview and export.
- Video editor: freeze frame, reverse clip, detach audio (for J- and L-cuts).
- Video editor: voiceover recording straight onto an audio track.
- Video editor: chroma key (green / blue screen) for video and image layers; blend modes for picture, text and sticker layers.
- Video editor: beat markers from the music.

## 1.0.136 — 2026-10-16
- LUTs: import your own .cube LUTs — Photo editor (Presets), Video editor (Color → Looks, as a look or as the log input conversion) and Capture (monitoring LUT on the viewfinder, optionally recorded).
- My presets: save your look (grade + detail) and apply it in the photo or the video editor.
- Photo editor: before / after split view with a draggable divider.
- Video editor timeline: real audio waveforms for clips and audio tracks; markers; snapping to cuts, markers, the playhead and other items.
- Video editor Deliver: one-tap export presets (YouTube, Instagram, TikTok…), export a range (in / out), save the current frame as a photo.
- Capture: shutter shown as an angle with a one-tap 180° button; anamorphic desqueeze for the viewfinder (1.33× – 2×). Video editor: anamorphic desqueeze per clip.

## 1.0.132 — 2026-10-16
- Video editor: Undo and Redo are always visible under the player (next to Split); the zoom buttons scroll on narrow screens instead.

## 1.0.130 — 2026-10-16
- Video editor Deliver: Audio only — save just the sound of the edit as an .m4a file (Music/AcluxPro or your save folder).
- Video editor timeline: track headers on the left — mute the video track or any audio track, show / hide layers, and lock tracks so they can't be moved, trimmed, split or deleted.
- Video editor Deliver: estimated file size of the export, updating as you change resolution, frame rate, bitrate and audio.
- Video editor: Undo and Redo buttons next to Split under the player (clips, layers, audio).
- Video editor: rename layers — give a watermark (or any layer) its own name, shown in the layer list and on the timeline; the watermark text is labelled separately.

## 1.0.126 — 2026-10-16
- Video editor timeline: hold a layer or audio bar to move it in time or drag it up / down to reorder layers; hold its start / end edge to trim or extend.
- Video editor media pool: add audio files (Media → Audio) and place them on the timeline at the playhead — music track plus any number of extra audio tracks (volume, fades) in preview and export.
- Video editor timeline: the edit now starts at the left edge (like desktop editors); the playhead follows your finger, tap the ruler to jump, two fingers zoom and scroll; the view follows the playhead while playing.

## 1.0.123 — 2026-10-16
- Guides: separate Capture, Photo editor and Video editor guides, each showing only what applies there (Cloud AI key setup, AI features, tips). Open them from each settings gear (guide button, or "How to add a key" under Cloud AI). Also on GitHub as GUIDE.md.

## 1.0.119 — 2026-10-16
- Cloud AI (optional): use your own fal.ai account to run Topaz Labs' professional models on fast cloud servers — photos: Topaz upscale (Standard V2) and Topaz Denoise; video: Topaz Nyx noise reduction (Nyx Fast / Nyx / HF / XL). Key stored encrypted; Wi-Fi-only option; falls back to the on-phone AI.
- Cloud AI progress (upload, processing, download) in the AI badge with Stop.

## 1.0.116 — 2026-10-16
- Video AI noise reduction: export-time info (several minutes up to about 1 hour, depending on hardware) moved behind a "!" button.

## 1.0.113 — 2026-10-16
- Release notes and changelog: every release now lists what's new, with links to the full feature list.
- H.265 (HEVC): labelled H.265 everywhere; time-lapse and hyperlapse follow the H.265 setting; H.264 / H.265 choice for live streaming.
- AI noise reduction (NAFNet, built in) for photos and video clips — Detail panels; photos clean up in the preview, video clips when exporting.
- AI progress badge with a Stop button — for AI upscaling in Capture and AI noise reduction in both editors.

## 1.0.108 — 2026-10-16
- Editors: resizable panels — hold a divider, then drag; double-tap to reset; remembered per orientation.
- Editors: app colour picker in the photo and video editor settings.

## 1.0.104 — 2026-10-16
- Start screen: animated Exit button (bottom-right) that closes the app.

## 1.0.101 — 2026-10-16
- Video editor: grade and detail preview drawn on screen (Android 13+), so the players decode plain video — fixes playback timeouts and makes slider changes instant.

## 1.0.99 — 2026-10-16
- Video editor: fixed black / stalled second clip (a colour table was shared between the two players).
- Video editor: "Show playback info" read-out in settings.

## 1.0.97 — 2026-10-16
- Video editor: play always responds; bounded stall recovery; "making preview copy" overlay; faster clip loading.
- Play at the end of the timeline restarts from the beginning.

## 1.0.94 — 2026-10-16
- Editors: Copy / Paste settings between clips and photos (choose what to paste; paste on all clips).
- Editors: real-time preview while dragging sliders (photo and video).

## 1.0.91 — 2026-10-16
- Photo / video curve: wider, stronger region sliders so Lights and Highlights clearly work.

## 1.0.89 — 2026-10-15
- AI upscale: fixed striped output; faster (bigger tiles, flat areas skipped, all CPU cores).
- AI upscale: animated progress badge with a clear percentage.

## 1.0.84 — 2026-10-15
- Camera: optional on-device AI upscale (ESRGAN) — AI 24 / 36 / 48 MP in the photo size picker.

## 1.0.81 — 2026-10-15
- Camera: Android 12+ full-resolution photo sizes where allowed; read-out of what the sensor offers to apps.

## 1.0.79 — 2026-10-15
- Editors: save projects and reopen them; LOAD and SAVE buttons next to Export; saved projects on the start page.

## 1.0.76 — 2026-10-15
- Video editor: fixed a stall at transitions; frozen playback recovers automatically.

## 1.0.74 — 2026-10-15
- Video editor: 34 transitions (preview and export).
- Video editor: crop / zoom on the player with Ken Burns animation; stabilization.
- Video editor: preview copies for HDR / 10-bit / 4K clips; playback watchdog; codec and resolution choice.
- Video editor: timeline tools (split, zoom, fit) under the player.
- Video editor: advanced audio — EQ, compressor, noise gate, limiter, channels, pan, fades, ducking, level meter.
- Editors: choose the save folder; remember watermark; delete media from the pool (with confirmation).

## 1.0.67 — 2026-10-15
- Startup choice: always start in Capture, Photo editor or Video editor.
- Video editor: gapless clip-to-clip playback.

## 1.0.64 — 2026-10-15
- Video editor: multi-track timeline with filmstrips, scrubbing playhead, zoom / fit and split.

## 1.0.62 — 2026-10-15
- Video editor: full-screen player with auto-hiding controls and mute.
- Video editor: video and image layers (picture-in-picture).
- Editors: music track; text, sticker, watermark and logo layers; video effects; image detail; project formats; photo watermark.
- Video editor: more reliable preview; stabilization off by default in Capture; version shown on the start screen.

## 1.0.54 — 2026-10-15
- New start screen (Capture / Editor), Photo editor and Video editor with log footage input, colour pages and 4K export.

## 1.0.52 — 2026-10-15
- Gallery: RAW (DNG) tab and badges.

## 1.0.50 — 2026-10-15
- Gallery: colour grading for photos and videos; favourites.

## 1.0.47 — 2026-10-15
- Zebras, false colour, peaking and histogram work in Pro Video on phones without an extra analysis stream.

## 1.0.41 — 2026-10-15
- Film looks, false colour, H.264 / HEVC choice, audio gain with limiter, cinema clip naming, clean feed.
- Headphone monitoring with live gain, limiter and volume.
- Photo modes fill the screen edge to edge.

## 1.0.35 — 2026-10-14
- Preview follows phone rotation correctly in all modes.
- Settings: choose the app's highlight colour.

## 1.0.30 — 2026-10-14
- Gallery covers the whole screen; videos play at full size.
- Settings: shutter sound on / off.

## 1.0.27 — 2026-10-14
- Full-screen video viewfinder and in-app gallery.

## 1.0.23 — 2026-10-14
- Renamed to AcluxPro (new app ID and launcher icon).
- Smart modes: time-lapse, hyperlapse, slow-mo, super slow-mo, burst.

## 1.0.14 — 2026-10-14 (as ProCam)
- Live mode fills the screen; photo megapixel choice per camera.

## 1.0.10 — 2026-10-14 (as ProCam)
- Live streaming (YouTube, TikTok, Twitch, Facebook, custom) and D-Log profiles.
- Pro modes, filters, audio graph, panorama, storage choice.

## 1.0.1 — 2026-10-14 (as ProCam)
- First version: pro camera app for Android phones and tablets.
