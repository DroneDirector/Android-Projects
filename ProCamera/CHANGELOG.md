# AlcorCut Pro — Changelog

All notable changes per released version. The full feature list is in [FEATURES.md](FEATURES.md).
Downloads: [Releases page](https://github.com/DroneDirector/Android-Projects/releases).

## Unreleased

## 1.0.808 — 2026-10-30
- **Connect to PC** (Capture → gear → Remote & output): use this phone as the camera in AlcorCut Pro on Windows, over Wi-Fi or a USB cable with USB tethering. The PC sees the live picture and works the camera; photos and videos are taken on the phone at full quality and copied to the PC. Protected by a 4-digit PIN.

## 1.0.806 — 2026-10-29
- **What's new as fold-outs**: each of the last three versions has its own fold-out — the latest update open, the earlier ones closed — on the start screen and in Capture and both editors.

## 1.0.803 — 2026-10-29
- **What's new for the last three versions**: start screen → gear → What's new lists the changes of the last three releases for the whole app; gear → App → ★ WHAT'S NEW in Capture, the Photo editor and the Video editor shows only the changes for that part.

## 1.0.801 — 2026-10-29
- **What's new, any time**: start screen → gear → What's new shows the latest changes again (handy if the card after an update was closed by mistake).

## 1.0.799 — 2026-10-29
- **Search in Settings**: a search field at the top of the Capture settings and both editors' gear — type "zebra", "proxy" or "location" and only the matching groups show, already open.
- **What's new**: after an update, a short card shows what changed in that version.
- **Hold for help**: hold a button (toolbar icons, chips, Capture quick bar) for a one-line explanation of what it does.
- **Haptic feedback** (gear → Appearance): a light tick on the shutter, record, switches, timeline snapping and when a slider passes its centre. Can be turned off.
- **Undo instead of "are you sure?"**: moving a photo or video to the Trash and a panel's RESET show a short bar with UNDO.
- **Capture picks up where you left off**: it opens in the last mode and lens. LAST in the quick bar (next to C1–C3) brings back one of your last three set-ups.
- **Heads-up before recording**: when space, battery or heat would cut the clip short ("about 9 min of 4K left"), you're told before it starts.
- **One-handed layout** (gear → Appearance, off by default): Right hand or Left hand — on a phone held upright, Capture's top bar moves down and the shutter sits under your thumb.
- **Swipe between photos** in the photo editor (left / right on the whole photo), with each edit kept; **NEXT UNEDITED →** jumps to the next photo you haven't edited.
- **Recently used tools** in the photo editor: the sections you opened last, one tap away above the panels.
- **Keep editing while exporting** (video editor): KEEP EDITING shrinks the render to a small bar; it carries on with the screen off or in another app, and the notification has a Share button when it's done.
- **Timeline view remembered** for each project: zoom, scroll position and the open page.
- **Missing media**: opening a project with clips that have moved offers FIND THEM, which looks for them by name on the phone and links them again.
- **Safe mode**: if the app closes twice in a row while starting, the next start offers safe mode (start screen, no last project or camera set-up).
- **Recent projects** sit right under the catalog card on the photo and video editors' start pages.
- **Merge photos in the photo editor's toolbar**: MERGE PHOTOS ▾ has every merge (HDR, panorama, focus stack, long exposure…) without going back to the start page.
- **CineForm export** (video editor → Deliver → Codec → CineForm (.mov)): a professional editing codec — 10-bit 4:2:2, every frame stands alone, uncompressed sound — for finishing on a computer. Qualities Medium, High, Film scan 1 and Film scan 2. Made with the open-source CineForm SDK (Apache 2.0 / MIT).

## 1.0.794 — 2026-10-29
- **Photo layers with blend modes** (photo editor → Text → PHOTO LAYERS): put another photo or a texture on top — a sky, film dust, paper, a second exposure — with Normal, Multiply, Screen, Overlay, Soft light and more, plus opacity, size and position.
- **HDR without ghosts** (Merge → HDR): people, leaves or water that moved between the bracketed shots are taken from one shot only, so there are no see-through doubles.
- **Long exposure from a video** (Merge → Long exposure from a video): silky water and clouds, or light trails, from one clip — no ND filter needed.
- **Colour samplers** (SAMPLERS under the photo): tap up to four points to read their R, G, B values live while you edit.
- **Focus mask** (FOCUS under the photo): the sharpest parts of the photo are shown in green.
- **Edit a photo in the photo editor from the video editor** (Edit → PHOTO → ✎ EDIT IN THE PHOTO EDITOR): when you export, the edited photo replaces the one on the timeline.
- **Client review** (Deliver → CLIENT REVIEW): a review page with your video that plays in any browser; your client adds notes at exact times and sends them back, and the notes become markers on the timeline.
- **Smart bins** in the media strip: Photos, Drone, 4K+, Vertical, Under 10 s and Log / HDR filter themselves.
- **Capture: anti-flicker** (Settings → Video): 50 Hz, 60 Hz, auto or off, against banding under mains lights.
- **Capture: save location** (Settings → Photo): where each photo and video was taken is stored in the file.
- **Capture: front + back camera** (Settings → Video): the back camera with the front camera in a corner, in one video, on phones that can run both together.
- **Capture: focus bracketing** (Settings → Photo): 3, 4 or 6 shots focused from close to far; the photo editor stacks them into one sharp photo when you open it.
- **Capture: timecode** (Settings → Video): videos carry a time-of-day timecode, so editing programs line up clips from several cameras.
- **Capture: editing proxy** (Settings → Video): a small copy of each clip is made after recording, so the video editor plays it smoothly straight away.
- **Capture: macro** (Settings → Photo): switches to the lens that focuses closest and focuses near.

## 1.0.791 — 2026-10-28
- **Photos in the video editor stay photos**: add them from the gallery, files, the NAS, the camera, Pro import or the photo library (Library window → Photos) — onto the timeline or into the media strip. They show still (or with a slow zoom you choose), marked PHOTO / 📷, and Edit → PHOTO sets how long each shows.
- **Name people** (photo and video library): faces are recognised on the phone and grouped by person; name them, tap one to see all their photos or videos, and search by name.
- **Recommended presets**: Presets (photo) and Color → Looks (video) suggest looks that suit what's in the picture, with a reason under each.
- **Remove reflections** (Heal → AI REMOVE): for photos through glass — the soft reflected picture fades, the scene stays sharp.
- **Find wires** (Heal → AI REMOVE): power lines and cables are found and selected; REMOVE takes them out.
- **Keyword tree and suggestions** (photo and video library): nested keywords (Places › Faroe Islands › Tórshavn), a Keywords view with counts, and suggestions from what the AI sees.
- **Tethered shooting** (filmstrip + → Tethered camera): each new shot from a camera on USB comes straight into the filmstrip, opened with your preset.
- **Watched folders** (photo and video library): new photos and videos landing in chosen folders are copied in by themselves, with metadata and develop presets; new videos also go into the video editor's media strip.
- **Photo book** (Library → select → Photo book): cover, page sizes, 1–4 photos a page or mixed, borders, white or black pages, page numbers — a print-ready PDF.
- **Content credentials** (Export / Deliver): exported photos and videos can carry a signed C2PA record of who made them and that they were edited in AlcorCut Pro (Android 9+).
- Layers on the timeline no longer show their name twice ("Shape Shape").

## 1.0.783 — 2026-10-28
- Video editor: Loop playback button under the picture (next to the frame-step button): the in–out range, or the whole timeline, plays over and over. Tap again to stop looping.
- Smoother sound in the preview: the music now sets the timing and is never jumped or sped up while it plays. The picture follows it instead — a touch faster or slower for a moment, or, if the device can't keep up, it skips ahead to the music. No more little glitches in the soundtrack.

## 1.0.777 — 2026-10-28
- Fix: the playhead jumped when playback went through a transition (or from one clip to the next). It now glides straight through.

## 1.0.773 — 2026-10-28
- Photo editor filmstrip: colour labels show as a stripe along the top of each photo, the colour dots next to the filters show only that colour, and several selected photos can be labelled at once (hold one).

## 1.0.771 — 2026-10-28
- Media strip: colour labels (red, yellow, green, blue, purple) on clips — set them for the picked clips or from a clip's long-press menu, shown as a stripe under the thumbnail, and the colour dots filter the strip. The same labels show in the video library.

## 1.0.767 — 2026-10-28
- Smoother preview: the playhead now glides every screen frame while playing instead of stepping, and the music and other audio tracks stay in step by playing a touch faster or slower for a moment instead of jumping — no more little gaps in the soundtrack every second or two.
- The app log also notes the app's memory use and Android's low-memory warnings, to track down closes for low memory.
- **Video library** (video editor → Media → Library & import, or the start page): every video on the device with stars, flags and colour labels (red, yellow, green, blue, purple), filters, albums, smart collections and a calendar view by year, month and day.
- **Pro import** in both editors: copy photos and videos from an SD card, a USB drive or a camera on its USB cable; new files only, rename patterns, a folder per day, a backup copy to a second place, a metadata preset and (photos) a develop preset.
- **Metadata presets**: your name, copyright, contact, caption and keywords saved as presets, given to photos in the library and written into exports — and into videos (Deliver → Metadata).
- Photo library: **calendar view**, **smart collections** with more rules (keyword, date, file type, location, colour label), metadata presets, **client proofing gallery** (a web gallery the client marks favourites, stars and notes in; their list comes back as picks) and **time-lapse video** from a photo sequence (deflicker, exposure ramp, a look that blends into another).
- Photo editor: **adaptive presets** (AI finds the subject, sky or people in each photo and adjusts only those), **remove people** in AI Remove, **HDR panorama** in Merge, and **upload after export** (every export is also copied to Google Drive, OneDrive, a NAS or any folder) — also in the video editor's Deliver page.
- The video editor starts with a wider tools panel and a taller timeline on tablets and phones in landscape.

## 1.0.759 — 2026-10-28
- App log on the device: app starts, exports, problems and crashes (with the app's recent system log lines). VIEW LOG in the crash / problem report, or ? → Troubleshooting → App log — share it or save a copy.
- Fix: 87 of the transitions (cinematic, creative and newer ones) still couldn't build their preview effect ("program too large") — only the chosen transition's code is built now.

## 1.0.755 — 2026-10-28
- Fix: export could still fail with "Video frame processing error" on some transitions (a transition that doesn't use every setting). Every setting is now kept; if one still can't be applied it is skipped and the export says so in a short note.

## 1.0.748 — 2026-10-28
- Fix: every video transition looked like a plain fade in the preview and on the Transitions page — the effects shader had grown too deeply nested for Android and wasn't being built. Shutter, kaleidoscope and all the others show their own effect again.
- Fix: export failed ("Video frame processing error") on edits with push, whip, slide-blur or film-gate transitions, and with layers dragged partly off the frame.
- Fix: lower thirds and other layers stayed on screen during playback outside their own time when they were selected. While playing, layers now show only in their own time; paused, a layer outside its time is shown faded so you can still place it.

## 1.0.746 — 2026-10-28
- Fix: every video transition looked like a plain fade in the preview and on the Transitions page — the effects shader had grown too deeply nested for Android and wasn't being built. Shutter, kaleidoscope and all the others show their own effect again.
- Fix: export failed ("Video frame processing error") on edits with push, whip, slide-blur or film-gate transitions, and with layers dragged partly off the frame.

## 1.0.740 — 2026-10-28
- About 80 new fonts for text in the photo and video editor, in five groups (Simple, Serif, Typewriter & mono, Script & handwritten, Display & fun) — with free twins of Arial, Times New Roman, Courier New and Calibri — each shown in its own lettering.
- Text effects: neon glow, gradient, rainbow, chrome, gold, 3D block, long shadow, hollow, double outline, retro 80s, glitch, fire, ice, comic pop, embossed, soft shadow, highlighter, sticker and pastel.
- 16 lower-third designs and 20 ready-made kinds of text (headlines, subtitles, quotes, speech bubbles, hashtags, breaking news, sale tags and more). In the video editor they are on the Text page; the selected text's font and effect are in Layers.
- Video editor → Audio → Separate voice and music (AI): a clip's sound split into a voice track and a music track (Demucs via your fal.ai account).
- Video editor → Color → Film & lens → Sky replacement: a new sky (Clear blue, Golden hour, Sunset, Dramatic, Night) above the horizon, frame by frame, in the preview and the export.
- Video editor → Layers → Text behind subject: a big title with the person standing in front of it (AI background removal).
- Video editor → Media → Edit templates: Travel recap, Drone reel, Action, Calm & warm, Intro / opener and Old film — one tap cuts your clips on the beat with transitions, a title and more.
- Photo editor → Text → Thumbnail: one tap to a 16:9 video thumbnail with a big outlined title and the subject popped out with a glow (new Background → Pop out).
- Text layers (photo and video editor): Outline in black, white or red, and a new Heavy font.
- Photo editor → Retouch → Liquify → Face (AI): sliders for eyes, face width, chin, nose and smile on every face found.
- Photo editor → Effects → Background (AI) → Double exposure: a second picture shown inside the subject's silhouette.
- Photo editor → Merge photos → Star trails: 10–100 tripod shots of the night sky become one picture with the stars' arcs.
- Photo editor → Library → select photos → Slideshow video: the photos become a new video timeline with a slow zoom and transitions between them, ready for music and titles.
- Video editor → Layers → Retro look: VHS tape, Old TV, Camcorder with a date stamp, Old film and Security camera, drawn over the picture in the preview and the export.
- Visualizers Dot matrix, Flames, Ribbons, Aurora and Heartbeat draw several times faster, so their previews run smoothly.

## 1.0.718 — 2026-10-28
- Video editor → timeline: tap anything to open its settings — a layer (text, visualizer, progress bar, countdown, map…) opens Layers with its own settings unfolded and scrolled into view, and the music or another audio track opens Audio at that track. Layers and audio bars move and trim by holding and dragging, the same as clips.
- Video editor → hold a clip on the timeline: new Colour & details (straight to the clip's exposure, contrast, highlights and shadows), Reveal in project and Reveal on device. Hold a file in the media strip: Colour & details, Reveal in project (jumps to where it is used on the timeline) and Reveal on device.
- Video editor → Deliver → WIDE + VERTICAL: export the same edit as 16:9 and 9:16 in one go.
- Video editor → Layers → Flight path / route now also reads .GPX tracks from GPS apps (hikes, drives, rides) and draws the route over the selected clip.
- Visualizers move with the music again in the preview (they had frozen on the first frame).
- Transition thumbnails fill their tiles again and each shows its own transition.
- Photo editor → Effects: Colour splash (keep one colour), Tilt-shift miniature and Sun glow & rays. Masks: Dodge and Burn brushes. Export → Metadata: title, caption, author, copyright and keywords written into the photo.
- Light look everywhere again, the start screens and splash included. Capture → Settings → Appearance: Dark, Light or Like the phone — on Light the menus and panels are light, the controls on the camera picture stay dark.

## 1.0.709 — 2026-10-28
- Light look: back to the editors only — the start screens and the splash screen always stay dark as before. Inside the editors all text stays dark on the light look.

## 1.0.707 — 2026-10-28
- Light look (Preferences → Appearance) now covers the start screen, the editor choice screen, the guide and the library too, and all text on it is dark — yellow headings and other coloured text turn near-black (red warnings stay a darker red). The dark look is unchanged; Capture stays dark on the camera picture, and the picture, timeline and media strip stay dark in the editors.

## 1.0.705 — 2026-10-28
- Video editor → Transitions: 15 more — Tilt down, Horizon wipe, Golden hour, Nightfall, Lens dirt flare, Brush strokes, Torn paper, Comic burst, Halftone, Thermal, Night vision, Hologram, Page turn, Blur wipe, Zoom punch.
- Video editor → Transitions → Audio: 37 more, 100 in all — Phaser sweep, Ping-pong echo, Megaphone, Narrow out, Wide in, Harmony rise, Octave drop, Reverse out, Crush in, Bass drop in, Thin crossfade, Flutter in, Gate in, Chop in, Wobble in, Grit in, Metal in, Shimmer in, Hall tail, Slap in, Pump crossfade, Phone call, Old record in, Fly in, Filter open, Freeze & echo, Tape stop echo, Stutter swap, Build & boom, Muffle to echo, Voice in, Airy out, Lo-fi out, Lush in, Reverse & swell, Megaphone in, Rewind & restart.
- Transition previews on the Transitions page load and scroll faster: small copies of the clips' pictures, drawn at half size, effects built once in the background and kept, and redrawn without rebuilding the page each frame. The live preview and the export are unchanged.

## 1.0.703 — 2026-10-28
- Video editor → Transitions → Cinematic: 15 more — Sun flare wipe, Dust in light, Projector, Lightning, Black hole, Fog roll, Rain on glass, Blizzard, Fire wipe, Light tunnel, Prism leak, Silent film iris, Glass pane, Depth push, Shockwave.
- Video editor → Transitions → Audio: 15 more — Doppler fly-by, Filter sweep, Vinyl crackle, Radio in, Phone in, Stutter in, Pitch slide in, Shimmer, Freeze, Sub drop, Downlifter, Chorus wash, Cathedral, Rise & drop, Surface.

## 1.0.701 — 2026-10-28
- Video editor → Transitions → Creative: 10 more — Ink drop, Zoom spin, Mirror fold, Light streaks, Spiral wipe, Bars rise, Shutter slices, Vignette close, Chroma zoom, Overexpose.
- Video editor → Transitions → Audio: 10 more with effects — DJ rewind, Record brake, Pump, Metal ring, Auto-pan, Slap echo, Lo-fi, Voice out, Airy, Trance gate.

## 1.0.699 — 2026-10-28
- Video editor → Transitions: 20 new picture transitions in a new Creative group — Paint splash, Honeycomb, Heart iris, Star iris, Diamond, Spin blur, Wave wipe, Stripes, Checkerboard, Water drop, TV switch, Glow dissolve, Dip to colour, Polka dots, Stretch warp, Split open, Pinwheel, Slide blur, Jelly, Pixel rain.
- Video editor → Transitions → Audio: 25 new audio transitions with sound effects at the cut, in the preview and the export — Underwater, Muffle out, Open up, Muffled crossfade, Thin out, Echo out, Echo in, Reverb wash, Reverb swell, Tape stop, Tape start, Tape stop & start, Stutter out, Glitch, Bitcrush, Telephone, Old radio, Pitch dive, Pitch rise, Tremolo fade, Chopper, Flanger sweep, Wobble, Riser, Distortion fade.

## 1.0.698 — 2026-10-28
- Fixed: the video editor could crash while layers were shown on the picture (e.g. with a visualizer whose analysis finished, or layers appearing and disappearing during playback).
- Visualizer: much livelier — each frequency band is measured against its own range in the song and jumps when it gets louder, so bars, rings and lines clearly move with the music (songs are analysed again once).
- Moving overlays (visualizer, countdown, progress bar, flight path) now update 25 times a second in the preview instead of 10.
- Moving overlays (visualizer, countdown, progress bar, flight path) now update 25 times a second in the preview instead of 10, so they follow the music smoothly.

## 1.0.693 — 2026-10-28
- Visualizer: much livelier — each frequency band is measured against its own range in the song and jumps when it gets louder, so bars, rings and lines clearly move with the music (songs are analysed again once).
- Moving overlays (visualizer, countdown, progress bar, flight path) now update 25 times a second in the preview instead of 10, so they follow the music smoothly.

## 1.0.691 — 2026-10-28
- Visualizer: now follows music on the audio tracks too (it only looked at the main music track before, and fell back to the clip's own sound), reads every kind of decoded audio, and its settings have FOLLOWS THIS SOUND to pick the music, any audio track or the clip's sound.

## 1.0.689 — 2026-10-28
- Visualizer: 20 new styles — Dot matrix, Neon bars, Skyline, Bubbles, Oscilloscope, Mountains, 8-bit blocks, Flames, Ribbons, Aurora, Sunburst, Hexagon, Spiral, Kaleidoscope, Star burst, Vinyl, Heartbeat, Twin rings, Flower, Gauge.
- Countdown: 20 new styles — Digital, Dotted ring, Segments, Double ring, Pie, Square, Bar under, Neon, Film leader, Hourglass, Flip clock, Minimal, Pulse, Radar, Rainbow ring, Clock ticks, Battery, Liquid, Dots, Scroll wheel bar.

## 1.0.687 — 2026-10-27
- Visualizer: 5 new styles — LED equalizer, Radial wave, Sound rings, Particles and Bass orb — and it can now be up to full-frame size.
- Countdown: a Big numbers style, and it can now be up to full-frame size.

## 1.0.686 — 2026-10-27
- Progress bar: no selection frame around it in the preview, and the Glowing tip style shows only the line and its glow (no background track).

## 1.0.682 — 2026-10-27
- Video editor → Layers → OVERLAYS → Countdown: an animated ring that empties with the seconds left in the middle; automatic (the layer's length) or manual (your number of seconds); ring, glowing ring, scroll wheel colours or solid disc.
- Video editor → Layers → OVERLAYS → Visualizer: spectrum bars, mirror bars, glow line, circular or waveform, driven by the music's own frequencies and pulsing on every beat; in sync in the preview and the export.

## 1.0.679 — 2026-10-27
- Progress bar → Scroll wheel style: its moving effects now always show — pixel shimmer behind the front edge, a glowing tip, sparkles, rolling ridges and a travelling coloured rim — and it gets enough height to see them.

## 1.0.678 — 2026-10-27
- Video editor → Layers → OVERLAYS: Cinema bars (still or sliding in and out), Flight path mini-map from the drone's .SRT flight data, Progress bar drawn into the video, and Lens flare (warm sun, anamorphic blue, soft glow).
- Progress bar styles: Plain, Rounded, Glowing tip, Gradient, Segments, Dots, Thin + dot and Scroll wheel (the look of the timeline's scroll bar).

## 1.0.672 — 2026-10-27
- File, Help, Panels and Markers menus: they always open right under their button and stay on the screen; when their content is taller than the room below, they scroll (with arrows). Closing a group inside them brings everything back into view, and the menu no longer changes width.

## 1.0.669 — 2026-10-27
- Video editor → Transitions: 11 new picture transitions — Rack focus, Luma fade, Shine sweep, Bokeh lights and Prism split (Cinematic), and a new Drone & travel group: Cloud pass, Warp zoom, Drone roll, Dolly zoom, Film gate roll and Heat haze.
- Video editor → Transitions → Audio: S-curve, Dramatic pause, Late swap, Early swap and Swell.
- Exports with transitions build a smaller GPU program (only the transitions each clip uses), so they work on more phones.

## 1.0.667 — 2026-10-27
- Photo editor → Export → FOR SOCIAL MEDIA: a labelled before / after picture, and a panorama carousel (a wide photo cut into 2–5 seamless 1:1 or 4:5 slides for Instagram).
- Video editor: hold a clip → Boomerang — the clip plays forwards, then backwards straight after.

## 1.0.664 — 2026-10-27
- Photo editor → Crop & perspective → SMART CROP: one tap crops to 1:1, 4:5, 9:16, 16:9 or 3:2 and keeps faces (or the main subject) in the picture.
- Video editor → Color → VERSIONS A / B / C: save up to three looks per clip and switch between them to compare.

## 1.0.661 — 2026-10-27
- Video editor: film looks (halation, bloom, grain, gate weave), lens correction and privacy blur now change the preview at once while you adjust them, on a clip that's already open — no reload. Deflicker still shows after letting go of its slider.

## 1.0.658 — 2026-10-27
- Video editor: Clarity, Texture and Image detail now change the preview live while you drag the sliders (checked on the test phone), on every Android 13+ phone and tablet.

## 1.0.650 — 2026-10-27
- Video editor: changing Clarity, Texture, Image detail, film looks or privacy blur now updates the preview right away (the clip reloads at the same spot when you let go of the slider).
- The glowing mouse pointer (app and website) no longer has the small orbiting dot; its halo, trail and click ripple stay.
- Video editor → Deliver → Resolution: 2.7K (2688 × 1512), 5.3K (5312 × 2988, GoPro), 5.4K (5472 × 3078, DJI Air 2S) and 5.7K (360) — a 360° video exports at 5760 × 2880 and stays 360° (marked so players, YouTube and Facebook show it as a sphere); any other video exports 5760 wide in its own shape. A 360° video exported at any 2:1 size keeps its 360° mark.
- Video editor → Deliver → Resolution: 5.1K (5120 × 2700, the DJI Mavic 3 / Air 3 shape) next to 5K (5120 × 2880, 16:9).

## 1.0.638 — 2026-10-27
- Video editor → Deliver → Resolution: 5K, 6K, 7K and 8K (5120×2880 up to 7680×4320 in 16:9), with 300 and 400 Mbps bitrates. The app checks the phone's video encoder and says whether that size works, at what highest frame rate, and when the other codec is needed (e.g. the Galaxy S24 makes 8K at up to 30 fps; most tablets stop at 4K).
- Video editor: Clarity, Texture and Image detail now run in the video player itself during preview — the same code as the export — so they show on every Android 13+ phone and tablet.
- Video editor: Clarity and Texture are sized to the picture like in the photo editor (they were far too weak on 4K and log footage such as D-Log), in the preview and the export.

## 1.0.632 — 2026-10-27
- Video editor: Clarity, Texture and Image detail now show in the preview while you drag them (Android 13 and newer), not only in the export.

## 1.0.630 — 2026-10-27
- Color → Curves → HUE CURVES (photo and video editors): Hue vs Hue, Hue vs Sat, Hue vs Bright and Bright vs Sat — drag a colour's point up or down (e.g. orange leaves → red, only the greens less saturated, a darker blue sky, less colour in the shadows); double-tap a point to reset it.
- Video editor → Edit → PRIVACY BLUR: FIND FACES finds every face in the clip on the phone and blurs it as it moves; + SPOT — tap anything else (a number plate, a screen, a sign) on the frame at the playhead and it's followed to the end of the clip. Blur or pixelate, strength and extra room; in the preview and the export.
- Video editor → Audio → PITCH & VOICE: raise or lower a clip's voice up to an octave without changing its speed, and Robot, Radio, Phone or Megaphone voices — heard live in the preview and in the export.
- Photo editor → Heal → Red eye / Pet eye: tap each pupil (FIND RED EYES finds them in faces automatically) — red pupils turn dark and natural, a pet's glowing eyes become dark pupils with a small catchlight.
- Library → Duplicates: near-identical photos grouped, the sharpest first (✓ KEEP); reject or select the extra copies in one tap. Nothing is deleted.
- Library → select photos → 📍 Geotag from GPX: each photo gets the position a GPS track (GPX from a GPS logger, phone app or drone log) had when it was taken; set the photos' time zone if the camera doesn't store one. Shown on the Map and written into exports — the original files aren't changed.

## 1.0.628 — 2026-10-27
- Video editor: the Play button effect has its own colour, apart from the scroll wheels — gear → Layout & look (or Playback) → Play button colour.

## 1.0.626 — 2026-10-27
- The Play button effect switch is under gear → Layout & look, and also under gear → Playback.

## 1.0.624 — 2026-10-27
- The Play button effect switch is now under gear → Layout & look.

## 1.0.622 — 2026-10-27
- Video editor: the buttons under the picture no longer flicker while you scroll the row.

## 1.0.620 — 2026-10-27
- The sweeping scan line is gone from the scroll wheels and the Play button; their other effects stay.

## 1.0.615 — 2026-10-27
- Video editor: with Play button effect on, the Play button looks like the scroll wheels — their glass gradient in the wheel colours, a turning rim, and flickering "upscale" pixels and sparkles while playing. Gear → Layout & look (or gear → Playback) → Play button effect: on = the animated look, off = the plain amber button. The row under the picture starts with Play in the middle until you scroll it.
- Capture: the Cast button on the picture (tablets) can be dragged anywhere, pinched to resize and double-tapped to make it small, like the other read-outs.
- Video editor, buttons under the picture: Play is bigger (an amber circle) and sits in the middle of the playback buttons — mute, go to in, previous clip and frame back on its left, frame forward, next clip, go to out and start on its right — and the row starts with Play centred under the picture; swipe the row for the rest.
- Video editor: while clips are picked (Select multiple or SELECT ALL), every change you make — colour, primaries, wheels, curves, LUTs, detail, effects, speed, audio, transitions, crop and more — is made the same way on all picked clips (only the settings you touch; each clip keeps its own media, trims and the rest of its edit).
- Photo editor: while photos are picked in the filmstrip, every change you make on the open photo — light, colour, detail, effects, lens, crop, masks, text and more — is made the same way on the picked photos (only the settings you touch; AI-made masks and AI-erased copies stay each photo's own).
- Color → Primaries: Clarity and Texture under Dehaze — clarity is broad mid-tone punch (or a softer, dreamier look below 0), texture brings out or smooths fine detail like skin, leaves and fabric.
- Timeline bar: SELECT ALL / DESELECT ALL next to + VIDEO, ◆ MARKER and SNAP, and ♪ DETACH AUDIO — the selected clip's (or every picked clip's) sound becomes its own audio track below the video. Holding a clip offers Detach audio too.

## 1.0.610 — 2026-10-27
- **Select multiple** in the photo filmstrip: hold a photo in the filmstrip → Select multiple: tap photos to tick them, hold any photo for the selection's menu (stars, flags, apply the open photo's edit, export, rename, remove), and Deselect all goes back to one photo. Holding a photo also offers Apply the open photo's edit and Export this photo.
- **Select multiple** on the video timeline: hold the middle of a clip on the timeline for its menu: Select multiple, Split at the playhead, Duplicate, Speed / Duration, Reverse, Mute sound, Delete. While selecting, tap clips to tick them; hold any clip for Deselect all, Select all, Duplicate, Mute, Nest into one clip and Delete selected. The ✕ on the timeline's SELECTED badge also ends it.

## 1.0.608 — 2026-10-27


## 1.0.605 — 2026-10-27
- **Light look** (gear → Preferences → Appearance: Dark, Light or Like the phone): the photo and video editors — panels, menus, dialogs, settings and the File, Help and other menus — switch to a light look, live. The picture, the timeline and the media strip stay dark so colours are judged correctly; Capture and the start screens stay dark.
- File, Help and Markers menus fit the screen and scroll; opening a group further down scrolls it into view instead of the list disappearing.
- Video editor: **Reveal in Project** is now one of the tools bar's chips, just before Deliver.
- Capture → Stop motion: its bar sits on the picture in every orientation — drag it anywhere, pinch to resize, double tap to make it small.

## 1.0.599 — 2026-10-26
- **Help** (question-mark icon next to the gear in both editors and Capture): the documentation and tutorials for the part of the app that's open (only that part), troubleshooting (common problems and fixes, check permissions, free space, reset the layout, share the last problem report, copy system info) and **Proof of purchase**.
- **Proof of purchase**: the AlcorCut Pro purchase on your Google account as Google Play reports it — product, order number, date and state — with links to your Play order history and the Play Store.
- **Project settings** (File → Project settings): General (the gear's main switches), Project (name, timeline name, shape and frame rate), Storage location (save location, NAS sign-ins, catalog, and the copies kept from the NAS and team projects with Clear), **Ingest** (new media into a bin, proxies for heavy clips, colour label by kind) and **Label colours** (name the eight colours; a colour per kind of media). Photo editor: General and Storage.
- Gear → **Preferences**: Appearance (Dark / Light / Like the phone — the light look itself arrives in an update), Sound (shutter sound on / off and which one), **Audio hardware** (which microphone and which speaker / headphones / interface the app uses; unplugged, the app goes back to the phone's own and tells you), Autosave, Playback, Timeline, and Editing (keep the screen on, photo-clip length, title length).
- Gear → **System Info** (also in Capture's settings): the phone's maker and model, Android version, processor, graphics chip and driver, memory and storage, display, every camera, hardware video encoders / decoders, audio devices, battery and heat — with Copy all.
- Video editor, buttons under the picture: **Speed / Duration** (speed in % or a length in seconds, backwards), **Time interpolation** (frame sampling, optical flow for smooth slow motion, motion blur), **Link / unlink tracks** (linked layers and audio move with the main video when clips are cut, trimmed with "closes the gap" or deleted; unlinked ones stay put), **Group / ungroup** (grouped layers and audio clips move together) and a **Markers** menu (go to, edit, clear one or all, export / import marker lists as .csv).

## 1.0.596 — 2026-10-26
- Video editor tools bar: **Reveal in Project** — Reveal in library picks the selected clip out in the media strip; Reveal on device shows the folder on the phone where its file is (copy the path, or open the folder in the Files app).
- **File menu** (file icon next to the layout and gear icons), with fold-out groups:
  - Video editor — **Project**: New project, New team project, Open project, Open recent, Open team project, Project settings. **Open projects**: several projects open at once — switch between them in a tap; Close project, Close other projects, Close all projects (unsaved changes are asked about). **Save**: Save project changes, Save as, Save a copy, Revert to saved. **Capture** (shots go to the media strip). **Import**: gallery, files, a whole folder, audio, NAS, project archive. **New**: Sequence (shape and frame rate), Sequence from clip, Bin, Offline file, Captions (from the transcript, AI, a subtitle file or empty), Transcript, Title. **Team**: open, share this project, let go, your name. **Dependencies**: Collect files, Make offline, Link media. **Exit**.
  - Photo editor — Project (new, open, open recent, settings), Save (changes, save as, save a copy, revert), Capture, Import (gallery, files, NAS), Exit.
- **Team projects**: a video project kept in a shared folder — on your NAS or a shared / cloud folder — that the whole team opens. Every file the edit uses is copied there once; opening copies it down once. The others see who has it open (they can open it read-only meanwhile), and saving over a newer version asks first.
- **Offline media**: New → Offline file makes a black stand-in (marked OFFLINE on the timeline) for a shot you don't have yet; Make offline unlinks a clip's media but keeps its trims and settings; Link media brings the file back or swaps in another.
- **Collect files**: copies every file the project uses (and the project itself) into one folder.
- **Keyboard shortcuts you can change** (keyboard icon in both editors; Capture → Settings → Keyboard shortcuts): tap an action and press the key (with Ctrl, Shift or Alt) — standard keys for playback, marking, editing, timeline, pages and the File menu in the video editor; rating, labels, panels and the File menu in the photo editor; shutter, zoom, exposure, locks, modes and overlays in Capture.
- **Exit** in the File menu closes the app with the same fade as the home screen's EXIT.

## 1.0.592 — 2026-10-26
- Photo and video editor start pages: the whole page now scrolls when it's taller than the screen, so the recent photos / videos at the bottom are always reachable.

## 1.0.590 — 2026-10-26
- **Network (NAS)**: a remembered NAS sign-in is now only used after the **phone's own lock** — fingerprint, or the PIN / pattern / password you've set up on the phone. Your fingerprint and PIN stay in the phone; the app only learns whether it was unlocked. If the phone has no fingerprint, PIN or pattern set up, the app tells you, and the saved sign-in is then the only protection. Gear → Network (NAS) → **Unlock with the phone's lock** turns the check off (both editors).
- Video editor, full screen: tapping the picture while it plays brings the controls back straight away (before, they only came back after pausing).
- Video editor timeline: shortening a clip from either edge now **leaves a gap** by default — the clip and everything after it stay exactly where they are (no jump to the left, also while the gap is being made). Gear → **Trimming closes the gap** turns on the old way: the gap closes and everything after it moves left on every track.
- Video editor on phones: the top bar's icon area now reaches from the back arrow to LOAD, so it's easy to swipe; the clip count and length text there is gone.

## 1.0.587 — 2026-10-26
- **Network (NAS)** in both editors: the add lists (video editor: start page and Media page; photo editor: start page and +) can open a **Synology or any other NAS** — or a computer sharing folders — on your Wi-Fi. Found automatically (or type its address), every shared folder and sub-folder can be browsed, and the picked videos, photos (RAW too in the photo editor) and audio are copied into the app with progress, so the edit keeps working when the NAS is off. It connects as a guest first; only when the NAS asks for it you sign in, and then choose whether to **remember the sign-in**. Remembered sign-ins are encrypted on the phone, left out of backups, and the password is never shown — gear → **Network (NAS)** lists server and user only, with **Remove**.
- Video editor: **more buttons under the picture** — step back / forward one frame, go to in / out point, mark in / out, add marker, safe margins on / off, **playback resolution** (Full, 1/2, 1/4, 1/8, 1/16 — the preview only, the export is always full), **magnification** (2× / 4×, view only, drag to look around), export frame and settings. All are icons; the **+** at the end chooses which buttons the bar shows.
- **Pinch, move and double-tap on the picture** in both editors: pinch to zoom in on the picture and drag to place it — the export is framed exactly as you see it (what's outside the frame is cut off). Double-tap = the whole picture again. In the photo editor's painting tools (Masks, Heal, Retouch brush) and the split view two fingers zoom the view only, so you can paint details.
- Timeline: the **in and out points** show as IN / OUT flags on the ruler, with the rest dimmed. **Markers** and strong keyframes shine like the scroll wheels — gear → **Marker effects** turns it off.
- Timeline: dragging a clip's **start edge** now moves that edge with your finger. Gear → **Trimming leaves a gap**: on = the cut-off part becomes black and everything after stays in place; off (normal) = the gap closes and **everything after it moves up on every track** (layers, audio, markers; locked tracks stay).
- Timeline **track switches**: the main video has **show / hide (eye), mute and lock**; every layer (titles, texts, watermarks, stickers, video layers) has show / hide and lock, and video layers also **sound on / off**; audio tracks have mute and lock.
- Video editor → Deliver: **RENDER & EXPORT** is the very first thing in Deliver, with the **resolution** choice inside it.
- Video editor: the time read-out is shown only below the picture (no longer on the picture).
- Capture: the histogram / scopes, exposure read-out and audio meter can now be **resized** (two fingers, or hold one for a resize grip) and **minimised** (double tap) — still live, just small. Size and position are kept for each one.
- Photo editor: the add buttons on the empty editor fit on phones (icon and a short name each). On **tablets** the photo editor has a back arrow at the top left, like the video editor.
- Phones: the editors' top-bar icons (gear, layout and the others) scroll sideways with the small arrows when they don't fit; Load, Save and Export stay put.

## 1.0.574 — 2026-10-26
- Video editor: the button bar under the picture shows the app's small animated arrows at the side that has more buttons, like the other scrolling rows.
- Scrolling rows and lists everywhere: when they don't fit, their ends keep an empty strip as wide as the arrow, so scrolled all the way to the left / right (or top / bottom), no button sits under an arrow. Rows that fit look exactly as before.
- Video editor: exporting video with sound failed with "Export failed: null" when the edit had transitions and a clip without sound (drone footage, generated clips). Fixed — those clips get silence and the export runs. If an export ever can't start again, the message names the problem and a problem report is saved.
- Problem reports: the app being closed by Android while in the background (low memory, another app open) is no longer reported as a crash.

## 1.0.570 — 2026-10-26
- Video editor: the whole button bar under the picture now scrolls sideways when it's wider than the screen (like the tool tabs), so every button — split, undo / redo, zoom, cast, play, full screen, playhead to start — can always be reached, and new buttons can be added there. When it all fits, the layout is unchanged.

## 1.0.568 — 2026-10-26
- Video editor: new **playhead to start** button (own icon: the playhead jumping back to the timeline's start) on the right of the transport bar below the picture — one tap and the playhead is at 0:00.
- Chromecast placement: in the video editor the cast button sits next to the zoom buttons below the picture on **tablets**; on **phones** it is in the tools, Deliver → **CAST**. Capture shows it on tablets only.
- Video editor → Deliver: Audio, Audio format, Codec, Bitrate and Estimated file size are now one **RENDER & EXPORT** fold-out, together with the RENDER & EXPORT button — everything about the file in one place. Codec and bitrate show only for video exports.

## 1.0.562 — 2026-10-26
- **Long exports in parts**: films over 4 minutes are rendered in parts of about 2½ minutes (split only at plain cuts, never inside a transition), each on its own, then joined into one video without re-encoding. If a part fails it's done again on its own ("Part 3 didn't finish — doing just that part again") instead of restarting the whole film.
- Sound exports: a clip is only treated as having no sound after two separate checks agree, so no clip's sound can be dropped by mistake.

## 1.0.559 — 2026-10-26
- Fix: exports with sound (and sound-only exports) failed or closed the app when the timeline had clips **without a sound track** (e.g. drone footage or generated clips) — "no audio or video track to output". Those clips now get silence in their place.
- **AI noise reduction done once per clip**: on the first export, each clip with AI noise reduction is cleaned once at full size and kept in the video catalog ("AI noise reduction — clip 1 of 2 (done once, reused next time)"); that export and every later one read the cleaned clip instead of running the AI on every frame again. Changing the strength cleans it anew; Slim down frees them. Stored at a very high bitrate so it looks the same as the AI's output.

## 1.0.555 — 2026-10-26
- Export problems now come with full details: when a video or audio export fails, the message says what failed inside, and a report (with the clips' audio formats, no media) opens at once with COPY / SHARE. On Android 11+ the app also reports crashes in native code on the next start, so they can be fixed too.

## 1.0.552 — 2026-10-26
- Video editor: **much faster thumbnails** on the timeline and in the media strip. Each clip shows a cover at once (the phone's own ready-made video thumbnail, or one quick frame), filmstrips fill in coarse to fine (whole clip first, then the gaps) for several clips at a time, and every thumbnail is kept on disk in the video catalog — a project you open again shows its thumbnails straight away.

## 1.0.549 — 2026-10-24
- Chromecast fix: the video editor's picture now reaches the TV (it showed only the AlcorCut Pro card — the title / layer pictures couldn't be drawn into the TV frame, which dropped the whole frame).

## 1.0.547 — 2026-10-24
- Chromecast fix: the TV connected but showed nothing. The phone now waits until a few seconds of stream are ready before telling the TV to play, answers the TV's requests fully (no dropped connections), gives exact segment lengths, and tries again by itself if the TV reports an error.

## 1.0.546 — 2026-10-24
- Rebuild of 1.0.544, no changes.

## 1.0.544 — 2026-10-24
- Chromecast, more complete: the video editor's TV picture now includes titles and layers (below and above the video), and **sound** goes to the TV too — the video's, the music's and every extra track's, mixed (Capture sends the microphone). Leaving a screen no longer ends casting: the TV stays connected, shows the AlcorCut Pro card, and picks up the next screen with a cast button (e.g. Capture opened from an editor, then back to the editor).
- The cast button follows the app colour (gear → App colour): outlined when idle, filled while casting; the card on the TV uses it too.

## 1.0.541 — 2026-10-24
- **Chromecast** in Capture and the video editor: the cast button shows the live viewfinder (Capture) or the viewer (video editor) on a TV with a Chromecast or Google TV on the same Wi-Fi, a few seconds behind. The picture is streamed from the phone (own HLS / MPEG-TS code); Google's free Cast library connects to the TV. The button sits on the picture by default; gear → Remote & output (Capture) or gear → Cast to TV (video editor) moves it below the picture / next to Split. Hide it in the layout (panels) menu ("Cast button").

## 1.0.538 — 2026-10-24
- The editors' **Camera** now opens the full **Capture** screen — every mode and pro control (manual ISO / shutter / focus / white balance, RAW, lenses, pro video…) — over the editor instead of the phone's basic camera app. Photo editor: each photo goes straight into the filmstrip. Video editor: videos and photos go into the media list. A "← Back to … editor" bar (or Back) returns to the editor, which stays open underneath.

## 1.0.536 — 2026-10-24
- Photo editor: photos taken with the editor's Camera no longer get the remembered watermark automatically — add a watermark by hand in the Watermark tab if you want one.

## 1.0.533 — 2026-10-24
- **Camera inside the editors**: Photo editor — the filmstrip's + (and the empty editor and start page) offers Gallery, Browse files and **Camera**; a photo you take goes straight into the filmstrip. Video editor — Media page **Camera → media list** and the media strip's **+ Camera**: record a video or take a photo (it becomes a 5 s clip); both go into the media list, not onto the timeline. Captures are saved to DCIM/AlcorCutPro.

## 1.0.531 — 2026-10-24
- AI progress badge: the full-size noise-reduction pass is shown as plain "AI NOISE REDUCTION" with one short line ("Cleaning the full-size photo").

## 1.0.529 — 2026-10-24
- **Video catalogs**: the video editor keeps a catalog of every clip you use — its last settings (grade, detail, stabilisation, film look, desqueeze, audio), given back when you add the clip to another project (switch in ⚙ → Catalog), and the slow work done for it: proxies, stabilisation analysis, waveforms, render cache, cloud AI results (never paid for twice) and transcripts, now kept in the catalog instead of the cache Android may clear. ⚙ → **Catalog**: create, name, rename, open and delete catalogs; choose how many clips to remember (10 or more, or Unlimited); clip copies (off by default — videos are large; when on, a project still opens if a clip's original is gone); move the catalog to private storage, shared storage or an SD card; **Slim down** frees what can be made again and keeps every clip's settings. Everything is stored as is or losslessly — nothing lossy.
- Photo catalogs: **Slim down** button too (removes AI results only; photos, copies and edits stay). Catalog edits and settings are included in the settings backup; copies and caches aren't.

## 1.0.526 — 2026-10-24
- **Photo catalogs**: the photo editor now keeps a catalog of every photo you edit — the photo itself (a copy), its edit and all settings, stars / flags / labels and the results of AI tools, so AI noise reduction never runs again for a photo in the catalog. Edits are saved as you work, not only when leaving. ⚙ → **Catalog**: create, open, rename and delete catalogs; choose how many photos a catalog remembers (10 or more, any number, or Unlimited — the photo opened longest ago is let go first); keep photo copies on or off (a photo still opens if the original is moved or deleted); move a catalog to the phone's private storage, shared storage or an SD card. Your existing filmstrip became the first catalog.
- Catalog storage is **lossless only**: AI results are stored as lossless PNG (about a third of the raw size, identical pixels), and RAW / TIFF copies are packed with lossless compression when it saves space and unpacked byte-for-byte on opening. JPEG / HEIC copies are kept as they are.

## 1.0.524 — 2026-10-24
- AI noise reduction is **remembered**: the cleaned preview and full-size results are kept on the phone (also after restarting the app), so reopening a project or photo with AI noise reduction shows it straight away without running the AI again. It also no longer starts while a RAW is still loading (which could make it run up to three times), and an outdated full-size pass stops quietly instead of interrupting the preview.
- AI progress badge: the second, full-size noise-reduction pass is labelled **AI NOISE REDUCTION · FULL SIZE** with a short line saying why it runs (the full photo for the export — or, for RAW, that exposure, white balance or tones changed).
- Photo editor → Detail → AI NOISE REDUCTION: a note says when the AI has to run again. For RAW: use it last, after exposure, white balance, highlights, shadows, whites and blacks. Clarity, texture, colour grading, curves, masks and the rest never make it run again.

## 1.0.517 — 2026-10-24
- Photo editor **Export tab** reorganised like a delivery page, each part in its own fold-out: **EXPORT** (format, quality, size, AI upscale and the EXPORT PHOTO button together), EXPORT PRESETS, COLOUR & SHARPENING, FILE NAME, METADATA (camera info, caption & keywords, .xmp sidecar) and BATCH. Nothing is duplicated.
- **AI noise reduction is done while you edit**: once it's on, the AI cleans the full-size photo in the background, once, and keeps the result. The export uses it and doesn't run the AI again (with RAW, changing exposure or white balance afterwards cleans it again in the background, as the AI works on the developed picture). Cloud AI (Topaz) still runs at export.

## 1.0.515 — 2026-10-24
- Photo export now runs in a **process of its own**, with all of its memory free for the photo (none taken by the editor, previews or Capture) — full-resolution exports with AI noise reduction no longer need to step down.
- AI noise reduction runs **once** per export (a retry reuses the cleaned picture instead of running the AI again).
- New **maximum-quality JPEG**: the app's own encoder keeps full colour detail (4:4:4 instead of halved colour) and at 100 % quality hardly compresses at all — true pro-size files. Colour space profiles (Display P3, Adobe RGB) are embedded. Quality now starts at 100 %.

## 1.0.513 — 2026-10-24
- Photo export: the size you choose is now **always** the size saved, however heavy the edit. Noise reduction, clarity, texture and sharpening work through the photo in bands, so full-size photos need far less memory; if a phone still runs short, the heaviest steps run a little smaller and the photo is brought back to your exact size (the app tells you).

## 1.0.511 — 2026-10-24
- Photo editor: exports with **AI noise reduction** no longer fail with "Couldn't export — try a smaller size" on big photos. The AI now works through the photo piece by piece (far less memory), falls back to the phone's processor if the graphics chip gives up, and if a photo still doesn't fit, the export steps down a little and saves anyway — telling you the size it saved at.
- New export size **Original (full resolution)**: the photo's own full size, with no 4800 px limit.
- AI upscale on export now goes up to **50 MP** (was 24 MP).

## 1.0.507 — 2026-10-23
- New **Keep settings after uninstall** switch (gear → backup, in Capture and both editors): your settings, presets and LUTs are saved to Documents/AlcorCutPro/Settings when you leave the app; after reinstalling, **Restore my settings** on the first screen brings them back. Off by default; the fal.ai key is never included.
- Android's own backup now covers settings only (no large AI models, no encrypted key), so it no longer runs over its size limit.

## 1.0.504 — 2026-10-23
- Photo and video editor: new **gear → Layout & look → Fold-out sections** setting — Remember (sections you left open open again next time), Always closed, or All open; each editor has its own choice.

## 1.0.502 — 2026-10-23
- Fold-out sections: no more shaking while you drag a slider in an open section — the heading's summary stays still (fixed height, values never included).

## 1.0.499 — 2026-10-23
- Video editor: **gyro stabilisation** — Edit → Stabilize → Source: Gyro (motion data) uses the motion file Capture records with the clip (Gyroflow setting) for sharper, steadier stabilisation; the picture is measured once to line the data up automatically. A .gcsv can also be picked by hand.
- Video editor: **HDR levels in nits** (Color → Input, HDR clips): a nits waveform from the real 10-bit frame with 100 / 203 / 1000-nit guides, peak and average, and how much of the frame is above 203 and 1000 nits.
- Video editor: **track groups (VCA)** in the mixer — Dialogue, Music and Effects; a group fader moves all its tracks together, M mutes the group.
- Photo and video editor: **export your look as a .cube LUT** (LUTs → Export look as LUT) — saved in Documents/AlcorCutPro/LUTs and added to My LUTs.
- Video editor: **luma key** for video and picture layers — remove dark (fire, sparks, smoke on black) or bright parts, with level and softness.

## 1.0.497 — 2026-10-23
- Photo editor and video editor: every section in the tool panels is now a **fold-out** (like the Capture settings) — a title, a short line listing what's inside, and an arrow. Open only what you need, so finding a tool is much quicker. The tabs above (Presets, Light, Colour… / Media, Edit, Colour, Audio, Proxy…) work as before.

## 1.0.495 — 2026-10-23
- Photo editor: a zoomed-in photo (pinch, double-tap or 1:1) now stays inside the viewer — it no longer spills over the top bar and the corners.

## 1.0.493 — 2026-10-23
- New **quick tour** in the photo editor and the video editor (same look as Capture's): SKIP closes it for now, "Don't show this again" turns it off, and gear → App → Quick tour shows it again or switches it back on.
- New photos and videos now save to **DCIM/AlcorCutPro** (also Music/AlcorCutPro and Documents/AlcorCutPro); everything in the old DCIM/VeyroCutPro folder still shows in the app's gallery and editors.
- Name: the home-screen shortcuts are now **AC Capture / AC Photo editor / AC Video editor** (icons already on the home screen update when the app opens), and the last places showing the old name in capitals (start-page "RECENT … PHOTOS", the guide header) now say AlcorCut Pro.

## 1.0.490 — 2026-10-23
- New name: **AlcorCut Pro** (formerly VeyroCut Pro) everywhere in the app, the docs and the download files (AlcorCutPro-ARM-….apk). Same app — it updates over the old one; settings, projects and the save folders on the phone are unchanged. Old backups still restore.

## 1.0.486 — 2026-10-23
- Capture settings (gear): everything is now in **folded sections** — Photo, Video, Audio, Viewfinder & monitoring, AI, Controls & buttons, Privacy / title / copyright, Film-set workflow, Remote & output, Storage & backup, App and This lens. Each line says what's inside; tap to open or fold it again.
- Photo editor and video editor settings (gear): also in **folded sections** — Layout & look, Saving & backup, Watermark, AI and App; the video editor adds Project archive & versions and Playback & proxies. Each line says what's inside.

## 1.0.482 — 2026-10-23
- Photo editor: fixed a crash (out of memory) when using **Colour noise** on big photos — it now works on a small copy of the colours and needs very little memory.

## 1.0.479 — 2026-10-22
- Photo editor → Detail → NOISE REDUCTION: new **Colour noise** slider — removes blotchy colour specks such as blue patches in the shadows of photos taken in dim, warm light, keeping the detail (RAW and JPEG).

## 1.0.477 — 2026-10-22
- Capture gallery → **Grade**: the tab row (Looks, Light, Colour, Detail, Toning) stays on one line and slides sideways; the picture now **updates live while you move a slider**, then sharpens to full quality when you let go.

## 1.0.474 — 2026-10-22
- Capture settings (gear) → PHOTO: **Always JPEG / Always RAW + JPEG / Always RAW only** for Pro photo mode — only one can be on, and the choice is remembered when the app is opened again.
- Capture gallery → **Grade**: new **Detail** tab for photos — **Clarity, Texture, Dehaze, Sharpening and Vignette** (Light already has Exposure, Contrast, Highlights, Shadows, Whites and Blacks).
- Capture gallery → **Grade**: RAW (DNG) photos open upright, and the graded copy is saved upright. Contact sheets, print layouts and collages made from DNGs are upright too.

## 1.0.471 — 2026-10-22
- **DNG thumbnails are upright too**: in the Capture gallery, the last-photo button, the photo editor's recent photos and the Library (the phone's own thumbnails don't turn DNGs).

## 1.0.469 — 2026-10-22
- Capture gallery: the upright fix now applies to DNG RAW files only; other RAW formats are shown as before.

## 1.0.467 — 2026-10-22
- Capture gallery: **RAW (DNG) photos taken upright now show upright** — the viewer now follows the file's orientation, as the JPEGs and your phone's gallery already did.

## 1.0.465 — 2026-10-22
- Video editor → Audio: the level, loudness and mixer meters now only redraw while the edit plays, instead of all the time — smoother and easier on the battery.

## 1.0.463 — 2026-10-22
- Video editor: internal clean-up — the editor's screen state is organised so the code stays well within Android's size limits and new features fit in. No change to how it works.

## 1.0.461 — 2026-10-22
- Photo editor → Merge photos: **Star stack** — night-sky shots lined up on the stars and averaged.
- Photo editor → Crop: **Expand (Cloud AI)** — a wider picture with the surroundings painted in (Bria Expand, trained on licensed images).
- Video editor: **keyframe curve editor** — Bézier handles and presets (ease in / out, back, anticipate) for clip keyframes.
- Video editor → Audio: **parametric EQ** (up to 8 bands over a live spectrum) and a **de-esser**.
- Video editor → Deliver: **review copy** — burned-in timecode, a watermark and marker notes on screen.
- Video editor → Proxy: **render cache** — AI noise reduction, background cut-out and AI depth clips rendered once for smooth playback.
- Video editor → Auto highlights: favours **sharp shots and people**, and can **cut on the music's beat**.
- Video editor → Layers: **AI dubbing** — the captions spoken as a new audio track (English on the phone; other languages via Cloud AI).
- Capture: **Stop motion** — onion skin over the viewfinder, a frame strip (play, hold, delete, 6–24 fps) and MAKE VIDEO.

## 1.0.457 — 2026-10-22
- Photo editor: new **Retouch** panel — **Liquify** (push, pinch, bloat, twirl, restore), **frequency separation** for skin (whole photo, face skin or all skin via AI) and **Relight** (move or add light using the AI depth map).
- Library: **Print layout** — PDF at exact print sizes (A4, A3, Letter, 10×15, 13×18 paper; 1–6 per page or fixed print sizes) with borders and crop marks.
- Library: **Collage** of 2–9 photos — five templates, five shapes, spacing, rounded corners and background colour.

## 1.0.455 — 2026-10-22
- Photo editor → Light: **camera profiles (.dcp)** — import DNG camera profiles and use one instead of the RAW file's own colour.
- Photo editor → Export: **soft proof with your printer's ICC profile** (perceptual / relative colorimetric, paper white), and **export recipes** — several presets in one go.
- Photo editor → Merge photos: **Noise stack**, **Remove passers-by** and **Super resolution** (2× from hand-held shots).
- Photo editor → Crop: **composition guides** — thirds, golden ratio, golden spiral, golden triangles, diagonals, grid.
- Library: **contact sheet PDF** of the selected photos.

## 1.0.453 — 2026-10-22
- Video editor → Audio: **mixer** — a strip per track (clip sound, music, extra tracks) with live meter, level, pan, mute and solo, plus a master level; levels, pan and mute go into the export.
- Video editor → Audio: live **LUFS loudness meter** (momentary, short-term, integrated) and a **dialogue leveller** that evens out speech.
- Video editor → Deliver: **OpenTimelineIO (.otio)** export next to EDL and FCPXML; captions also as **WebVTT (.vtt)**; **import .srt / .vtt subtitles** as caption layers (Layers).
- Video editor → Deliver: **safe zones** on the viewer for TikTok, Reels and Shorts, plus TV action / title safe.
- Video editor → Deliver: **smart render** — a cut-only edit is copied straight from the clips without re-encoding (no quality loss, much faster).
- Video editor → Media: **several timelines per project** — new, duplicate, rename, switch.

## 1.0.450 — 2026-10-22
- App icon long-press menu: **Video** is now **Pro video** and opens Pro Video mode.

## 1.0.448 — 2026-10-22
- Teleprompter: text can now also scroll **sideways** as one line, like a news ticker (⇅ / ⇆ in its top bar, or ✎ → Scrolling); scrolling upwards stays the default.

## 1.0.445 — 2026-10-22
- Capture: **24-bit audio** and **Extreme bitrate** are only offered on phones strong enough for them (memory, processor, graphics and video encoder are checked); on others they're greyed out with a short note.
- Capture: **teleprompter** — type your text, style it (bold, italic, underline, narrow, alignment, size, text and background colours), let it scroll by itself (**AUTO-SCROLL** button and **speed slider** right on the teleprompter) or start scrolling with the recording; drag it anywhere, resize it, shrink it to a small bar or close it. Opens by itself with the selfie camera, and works with the back camera too (PROMPTER in the quick bar).

## 1.0.440 — 2026-10-22
- Capture (Pro): **read-out bar** with exposure mode — **shutter priority** and **ISO priority** now follow the light continuously (Android 15+ uses the camera's own priority modes) — and resolution · frame rate · bitrate in video; tap any value to change it.
- Capture: **Fn button** — tap to run your chosen function, hold to pick one of 17.
- Capture: **pull points** for zoom and exposure as well as focus, over 1–8 s, separately or all together.
- Capture: **separate focus and exposure points** (tap = focus, double-tap = exposure).
- Video: **24-bit WAV audio** saved next to each clip; **NTSC rates** (23.976 / 29.97 / 59.94); **record in the frame guide's shape** (2.76, 2.39, 1.85, 4:3, 1:1, 9:16); **bitrate tiers** (Economy, Standard, High, Extreme); **flip picture** for adapters and mirror rigs.
- Capture: **sync C1–C3 set-ups** between devices through a file in your cloud folder.
- New **watch remote** for Wear OS watches (Galaxy Watch 4+, Pixel Watch): live picture, record / shutter, photo ↔ video, zoom — install VeyroCutPro-WATCH from this release (see Guides and Tips).
- Start screen: new **gear** with **home-screen shortcuts** — add separate icons for Capture, the Photo editor and the Video editor.

## 1.0.434 — 2026-10-22
- Photo editor → Light → **RAW LOOK**: a folded line at the top of Light ("Tap here to see different DNG looks"). Unfolded: Default (the app's own RAW look, unchanged), **Match camera picture** (copies the brightness and colour of the preview stored in the RAW file), and fold-out sub-menus **Phones**, **Drones**, **Cameras** and **Action & 360°** with small previews of your photo in each look — scroll and tap, like the transitions list; everything folds away again. The looks are measured: the automatic RAW test compared 150+ sample RAW files with the pictures those cameras made themselves (Pixel, Galaxy, iPhone, DJI, Parrot, Autel, Canon, Sony, Nikon, Fujifilm, Leica, Ricoh, Panasonic, Olympus / OM, YI).
- RAW: **JPEG XL compressed DNGs** now open — the format newer Samsung Galaxy phones (and recent iPhones) save their RAW photos in.
- RAW: DNGs from **black-and-white cameras** (Leica M Monochrom, Pentax K-3 III Monochrome) now open.
- RAW: fixed coloured garbage blocks in DNGs from some action cameras (e.g. YI Action Camera 3) — older DNGs written with a lossless-JPEG quirk are now read correctly.

## 1.0.425 — 2026-10-21
- **RAW files from other cameras now open in the photo editor**: Sony ARW, Canon CR2 / CR3, Nikon NEF, Fujifilm RAF, Olympus / OM System ORF, Panasonic RW2, Pentax PEF and more. They're read by LibRaw (credited in Settings → Open-source licences) and developed by the same RAW engine as DNGs, starting at about the brightness of the camera's own picture. The gallery and Library recognise them as RAW too.
- Behind the scenes: an automatic RAW test now develops 60+ drone, camera and phone RAW files every time the RAW code changes, so a fix for one camera can't quietly break another.

## 1.0.419 — 2026-10-21
- RAW (DJI and other DNGs with per-colour lens-shading maps): the quick preview shown while a slider moves now keeps the dark-corner correction too, instead of flashing the uncorrected lens fall-off.

## 1.0.417 — 2026-10-21
- Photo editor, RAW → Lens corrections from the file: **Dark corners** and **Lens shape** can now be turned off separately, so you can keep the part that works for your camera or drone.

## 1.0.415 — 2026-10-21
- RAW photos: **no more dark edges and a bright centre.**
  - Capture now asks the camera for its lens-shading data and saves it inside every RAW (DNG), as the phone's own camera app does, so editors can even out the lens's dark corners.
  - RAWs already taken without that data (from phones) get their dark corners evened out automatically in the photo editor. A note says so, and **Light → Lens corrections from the file** turns it off.

## 1.0.413 — 2026-10-21
- Capture gallery: **select several photos and videos**. Hold one to start, then tap more; the ✓ shows which are selected. Then share them all at once, delete them (to the trash, restorable for 30 days), favourite them, or mark them pick or reject.
- Capture settings → **Side buttons (volume up / down)**: choose Off (they just change the volume), Shutter / record (take a photo or start / stop recording, whichever mode is on) or Zoom.
- Photo editor: **the viewer now shows the photo at your screen's full resolution** instead of a small preview, and zooming in shows the photo at its full size. Thumbnails stay small and fast.
- Photo editor: **very large RAW photos open as RAW** instead of failing with "too big for the phone's memory". The preview is made straight from the sensor data, without a full-size copy in between.
- Photo editor → Presets: **the preset thumbnails appear much faster**. Each one is made small and directly, and they're kept, so they show at once next time.
- Photo and video editor → **SAVE shows your saved projects**. Tap one and choose Overwrite (it asks first), or type a name and choose Save as new. The project that's open is marked.
- **Samsung DeX:** the app's own mouse pointer (the same look and colours as your pointer settings) now shows on the monitor, and the normal pointer stays until the app's pointer appears.
- **Samsung DeX:** on a wide monitor, Capture takes photos and videos wide (landscape) instead of upright.

## 1.0.410 — 2026-10-21
- **Background tasks show in the notification shade**: one "Background tasks" notification lists everything VeyroCut Pro is doing in the background, each with its progress — photo and video exports, edit sync, proxy generation, AI noise reduction, AI upscale, AI cull, Cloud AI and the first-launch downloads — and a short note when an export is done. On Android 13+ the app asks once for permission to show notifications.

## 1.0.408 — 2026-10-21
- Photo editor: **exports keep going when you leave the app**. Single exports, batch exports and filmstrip "export selected" now run in the background with a progress notification (and a "finished" notification), like video exports — minimising the app or turning the screen off no longer stops them. If Android does close the app in the background, it now reopens on the screen you were on instead of the start screen.

## 1.0.406 — 2026-10-21
- Photo editor filmstrip: **thumbnails show at once and stay put**. RAW thumbnails now come from the camera's preview inside the DNG (colour-matched to the editor) instead of developing the whole RAW, they're kept in memory and on disk so switching photos no longer blanks the strip, and at most three are made at a time so they don't slow the main viewer.

## 1.0.404 — 2026-10-21
- Photo editor: **no more black viewer when switching RAW photos**. The photo shows at once — the last finished render of that photo and edit when there is one, otherwise the camera's own preview from inside the DNG, colour-matched to how the editor develops that camera's files — and the full RAW render fades in over it.
- Photo editor: the filmstrip photos next to the one you're on are read ahead in the background (decoded and rendered with their own edit), so stepping through them is usually instant.

## 1.0.402 — 2026-10-20
- Video editor timeline: clips start right next to the track headers and are never drawn past them, even when scrolled or selected.

## 1.0.400 — 2026-10-20
- Fixed: the video editor wouldn't open in 1.0.395 (it closed straight away).

## 1.0.395 — 2026-10-20
- Video editor timeline: **a bin on the selected clip**. Tap a clip to select it, then tap the red bin at the top of the clip to delete it. You choose each time:
  - **Close gap:** the clips after it move left.
  - **Leave gap:** black of the same length takes its place, so everything after it stays where it is.

  Delete in the clip tools asks the same question, and undo brings the clip back.
- Fixed: the app crashed when you held a very short clip, such as a small piece left by a split, to trim it.

## 1.0.388 — 2026-10-20
- Photo editor, Export → FORMAT: **AVIF** and **JPEG XL**.
  - **AVIF** is often half the size of a JPEG at the same quality. It uses the phone's own AV1 encoder, which Android 14 and newer have built in. The app checks that every AVIF file opens before keeping it.
  - **JPEG XL** gives high quality at small sizes and opens on Apple devices, in Safari and in newer photo software.
  - Both use the Quality slider. On older Android versions that don't list these types in the photo library, they are saved to Download/VeyroCutPro instead.

## 1.0.385 — 2026-10-20
- Photo editor, Lens:
  - **AUTO COLOUR FRINGES** measures how far red and blue are off towards the corners, and lines them up.
  - **LENS PROFILE** sets distortion and corner shading for the kind of lens the photo was taken with (ultra-wide, wide, standard, normal or telephoto), read from the photo's camera details.
- Photo editor **Library views**:
  - **Stacks:** bursts are shown as one photo with a count; tap it to open the stack.
  - **People:** photos grouped by how many people are in them (no people, 1, 2, groups). It counts faces but doesn't recognise who they are.
  - **Map:** a pin for every photo that has a location. Tap a pin to see those photos. The world map is drawn by the app itself (Natural Earth, public domain), with no map service. The app asks once to read the locations saved in your photos, and they never leave the phone.
- Photo editor, Color → CALIBRATION → **FROM A COLOUR CHART**: photograph a 24-patch colour chart, tap its first and last patch, and the app works out a colour matrix so the camera shows the true colours. Save it in MY PRESETS to use for other photos taken in the same light.
- Photo editor masks: **Refine edge (hair)** for Subject, Sky, Object and People masks, so the edge follows hair, fur and leaves strand by strand.

## 1.0.380 — 2026-10-20
- Photo editor, Export → OUTPUT → **Gamut warning** (Glossy print / Matte print): cyan marks colours that paper can't print, so you can tone them down before printing.
- Photo editor **reference view**:
  - REFERENCE on the photo (or hold a filmstrip photo → Use as reference) shows a reference photo next to the one you're editing. Use it to keep a set looking the same.
  - **MATCH LOOK** matches this photo's exposure, white balance, contrast and saturation to the reference.
  - Shift+R turns the view on and off.
- Photo editor filmstrip → SELECT → **Rename**: gives the photo files new names from a template ({name}, {date}, {time}, {seq} and your own text), with a preview. Android asks once for permission to change gallery photos.
- Photo editor masks: **Noise reduction** inside a mask, for example only in the sky or the shadows.

## 1.0.376 — 2026-10-20
- Photo editor, Heal → **AI REMOVE** (was AI Erase):
  - **Tap to select** people, cars or signs (the AI selects each one), or paint over them, or both.
  - On the phone, the LaMa AI now paints in what was behind them at the photo's **full size**. Each area is filled separately, so small things keep their detail and the rest of the photo keeps every original pixel.
- Photo editor, Masks → **People (AI)**:
  - Selects parts of everyone in the photo: face skin, body skin, eye whites, irises, eyebrows, lips, teeth, hair and clothes.
  - **Portrait looks** in one tap: Smooth skin, Whiten teeth, Brighten eyes, Iris enhance, Lip colour, Hair shine and Brows define.
  - Every mask now also has **Texture** (minus smooths skin) and **Clarity**.
- Photo editor **keyboard shortcuts** (Bluetooth / USB keyboards, Samsung DeX):
  - ← → next photo, 0–5 stars, P / X / U flags, 6–9 colour labels, \ before / after, Z zoom.
  - L / C / D / R / Q / M / E / H open the panels.
  - Ctrl+Z undo, Ctrl+C / Ctrl+V copy and paste the look, Ctrl+S save, Ctrl+E export.
  - Press ? or F1 for the list.
- Photo editor, Export → OUTPUT → **Ultra HDR** (Android 14+): JPEGs whose bright parts (sky, lights, reflections) glow brighter on HDR screens, and look exactly as edited everywhere else. Choose Subtle, Standard or Strong.
- Photo editor filmstrip → SELECT → **AI cull**:
  - Rejects blurry and out-of-focus shots, and shots with closed eyes.
  - Groups similar shots (bursts) and picks the best of each.
  - A note on each thumbnail says why. Nothing is deleted.
- Photo editor **targeted adjustment**: in Curve or HSL Mixer (Hue / Sat / Lum), put a finger on a tone or colour in the photo and drag up or down to change exactly that.

## 1.0.371 — 2026-10-20
- Video editor, Audio → AI VOICEOVER FROM TEXT: **tap a voice to hear a sample**. It reads the first sentence of your text, or a short hello if the text box is empty. The first sample of each voice takes a moment, because the voice is downloaded; after that it plays straight away.

## 1.0.369 — 2026-10-20
- Video editor: the media strip is now the first tab of the tools panel on **both phones and tablets, in any rotation**. With the tools panel hidden it sits above the timeline, and external monitors keep their own layouts.

## 1.0.367 — 2026-10-20
- Capture: **shutter sound styles** (Settings → Shutter sound). Choose from Phone default, SLR, Rangefinder, Film camera (with the winder) and Digital, and tap a style to hear it. The app makes these sounds itself. Where the law requires a shutter sound, the phone's own sound is always used.

## 1.0.365 — 2026-10-20
- Video editor timeline: **keyframes now show on the ruler** in their own lane along its bottom edge. Clip keyframes are amber diamonds, layer keyframes light-blue diamonds and volume keyframes blue dots. Each has a dark outline so it stands out without being large. The selected clip's or layer's keyframes are full strength with a thin line down to their track, and the others are dimmer. The diamonds on the clips themselves are easier to see too.

## 1.0.363 — 2026-10-20
- **External monitor layouts**. These are used only when the app is shown on a monitor through Samsung DeX or HDMI / USB-C desktop mode. On the phone's or tablet's own screen nothing changes, and unplugging switches back straight away without losing your edit.
  - **Normal wide monitors (16:9 / 16:10):** the side-by-side layout with the media strip above the timeline.
  - **Ultra-wide (21:9) and super ultra-wide (32:9):** the video editor gets three columns: media (a tall grid of clips) | viewer | tools, with the timeline across the whole width. The photo editor gets filmstrip | photo | tools. Capture keeps the camera in the middle with a large histogram and the exposure read-out at the sides. Every column can be resized with its divider.

## 1.0.361 — 2026-10-20
- Photo editor **Library** (start page → Library): every photo on your phone in one place.
  - Star ratings, Pick / Reject flags, colour labels and keywords.
  - **Albums**, and **smart albums** that save a set of filters and a search.
  - **AI search**: find photos by what's in them, such as dog, beach, sunset or food. Your photos are labelled on the phone in the background.
  - **Info** shows the camera, lens, focal length, aperture, shutter speed, ISO, date and location.
  - **Compare** two photos side by side with linked zoom, or **Survey** up to 12 and drop the ones you don't want.
  - Edit many photos together in the filmstrip.
- **Virtual copies**: the same photo with a separate edit, from the library or the filmstrip ("Make virtual copy").

## 1.0.359 — 2026-10-20
- **Autosave** in the photo and video editors (gear → AUTOSAVE). Choose how often (Off, or every 1, 2, 5, 10, 15 or 30 minutes) and how many autosaves to keep (1–5). Each save is a separate copy, and the oldest is removed only when there are more than you chose to keep. A copy is saved only when something changed. Open them from LOAD → AUTOSAVES; your own saved projects are never touched.

## 1.0.357 — 2026-10-20
- The start-screen logo's aperture now **closes and opens like a shutter** about every 2.6 seconds, as if a photo is being taken, and the glowing ring flashes as it fires. The sparkling ring effect stays as before.

## 1.0.355 — 2026-10-20
- The logo on the start screens now has a **glowing, sparkling ring** in your app colour (Settings → App colour). A light runs around it, sparkles orbit and a soft glow breathes behind it. It appears on the first-time download screen, the update check, the start screen and the Editor screen.
- Video editor media strip placement:
  - **Phones:** always the first tab, on the left, of the tools panel ("Media strip"), whichever way the phone is turned.
  - **Tablets:** above the timeline in landscape, and the first tab of the tools panel when upright.
  - With the tools panel hidden, it sits above the timeline.
- **− / +** buttons on the media strip and the photo filmstrip make the thumbnails smaller or bigger. You can also drag their dividers.
- Photo editor **Upright** (Crop & perspective): Auto, Level, Vertical and Full find the lines in the photo and straighten the horizon and the buildings.
- **Dust spots** (Heal): VISUALISE SPOTS makes sensor dust easy to see, and FIND DUST AUTOMATICALLY heals small dark specks in smooth areas such as the sky.
- **Panorama options**: Cylindrical, Spherical or Perspective projection, **Boundary warp** to fill the frame, and **Fill edges (AI)** for the empty corners.
- **Export**:
  - **TIFF** (16-bit or 8-bit, lossless).
  - **Colour spaces** with the profile embedded: sRGB, Display P3 or Adobe RGB.
  - **Output sharpening** for screen, matte paper or glossy paper.
  - **File-name templates** with name, date, time, counter and preset.
  - **Export presets**: Web, Social 1080, Full quality, Print (TIFF), Archive PNG and your own.
  - **Soft proof** previews a matte print.

## 1.0.351 — 2026-10-20
- Photo editor masks:
  - **Object (AI)**: tap any object and the AI selects it.
  - **Depth range (AI)**: select by distance from the camera.
  - **Combine** a mask with more selections: add, subtract or intersect a colour range, a brightness range, a depth range, the subject or the sky.
- Photo editor **Lens Blur**: a shallow depth of field from the AI depth map. Tap the photo to set the focus, then choose the blur amount, the focus range and the bokeh shape (circle, hexagon, octagon, ring, cat's eye). Bokeh boost makes highlights glow.

## 1.0.349 — 2026-10-20
- Fixed: the photo filmstrip and the video media strip disappeared when they were empty (for example after removing every photo). They now stay on screen with their + buttons until you hide them in the Panels menu.
- Samsung DeX: plugging in or removing a keyboard or mouse, or moving between the phone screen and a monitor, no longer reloads the screen or closes your edit.
- Photo editor, **pro develop tools**:
  - **60 built-in presets** in 10 groups, with live thumbnails of your photo and an **Amount** slider (0–200 %).
  - **.xmp** presets and sidecars can be imported (they go into My presets), and your look can be exported as an .xmp preset.
  - **Auto** tone, **Auto** white balance, a **white-balance eyedropper** and a **clipping warning** (red = blown highlights, blue = crushed shadows).
  - **Point Color**: pick a colour on the photo and shift only that colour. **Calibration** adjusts the shadows tint and the red, green and blue primaries.
  - Colour grading now has **Global**, **Blending** and **Balance**.
  - Sharpening now has **Radius**, **Detail** and **Masking**, with a mask preview.
  - **Defringe** for purple and green fringes.
  - **Post-crop vignette** shape: Midpoint, Roundness, Feather and Highlights. **Grain** now has Size and Roughness.
  - **Zoom** (pinch, double-tap or Ctrl + wheel), with a sharper **1:1 loupe** render.
- Resizable media strips: hold the divider above or below the video editor's media strip, or above the photo filmstrip, then drag to make it taller or shorter. Double-tap the divider to reset it.

## 1.0.344 — 2026-10-20
- Fixed: removing a photo from the photo editor's filmstrip (hold → Remove from strip) closed the app.

## 1.0.342 — 2026-10-20
- Video editor: a **media strip** above the timeline shows every imported clip, whether it is on the timeline or not. Hover with a mouse, or hold and slide a finger, to skim through a clip. Tap a clip to pick it and double-tap to add it to the end. Clips already on the timeline get an amber USED mark. You can give clips 1–5 stars or reject them, filter by All / Unused / Used / ★3+ / No rejects or by bin, and use SELECT to append, rate or remove several clips at once. **+ Clips** imports up to 100 clips at a time; **+ Folder** imports every video in a folder (including two levels of sub-folders).
- **Mouse support** in Capture, the Photo editor and the Video editor. A connected mouse gets VeyroCut's own **glowing pointer**, with a halo, a trail, a click ripple and an orbiting spark. A plain pointer and the normal Android arrow are also available.
- In the gear settings you can pick a **pointer colour** and, separately, a **scroll wheel colour**, so both stand out from the rest of the app's graphics.
- Mouse controls in Capture: the wheel zooms, right-click locks or unlocks exposure and focus at that spot, and middle-click takes the shot.
- Mouse controls in the Photo editor: the wheel moves to the next or previous filmstrip photo, right-click switches the before / after split, double-click hides or shows the tools, and right-click on a thumbnail opens its menu.
- Mouse controls in the Video editor: drag a clip from the media strip onto the timeline. On the timeline, Ctrl + wheel zooms, Shift + wheel scrolls along the edit, and right-click on a clip opens its tools. On the player, double-click goes full screen and the wheel steps through frames.

## 1.0.340 — 2026-10-20
- Photo editor: pick many photos at once (gallery or files). They appear in a **filmstrip** under the photo; tap one to switch, and each photo keeps its own edit automatically.
- Filmstrip: hold a thumbnail for stars (0–5), Pick / Reject and colour labels; filters for All, ★3+, Picks, Edited and No rejects; SELECT to sync the current edit to other photos, rate, flag, export the selected photos (each with its own edit) or remove them.
- The start page offers "Continue filmstrip" so you can carry on later; the filmstrip can be hidden from the Panels menu.

## 1.0.338 — 2026-10-20

## 1.0.337 — 2026-10-20
- After an app update, the first start quickly checks the download server for changed AI models or soundtracks and fetches only what changed (nothing, if nothing did).

## 1.0.334 — 2026-10-20
- **Much smaller app**: one APK per phone type (almost every phone takes the main APK), and the AI models and soundtracks are no longer inside the APK — on first launch the app downloads them once (about 700 MB) with a progress screen. It asks first on mobile data, carries on where it stopped if the connection drops, and you can start using the app while it downloads.

## 1.0.331 — 2026-10-20
- Video editor: **Object removal (AI)** (Color → Detail → REMOVE AN OBJECT) — tap a person, car, sign or wire and it disappears from the whole clip, filled from the real background in the frames around it.
- Video editor: **AI depth** (Color → Detail → AI DEPTH) — lens blur with real depth of field, fog by distance, or a darker, calmer background behind your subject.
- Video editor: **AI voiceover from text** (Audio) — 12 natural English voices, made on the phone and added at the playhead.
- Video editor: **Studio voice (AI)** — the cleanest voice clean-up (DeepFilterNet3), plus **Remove clicks & crackle** and **Remove room echo** (Audio → REPAIR).
- Video editor: **Translate captions** into 19 languages on the phone (Layers → TRANSLATE CAPTIONS).
- The new AI models are downloaded the first time you use them, so the app itself doesn't grow.
- Video editor: **Optical-flow slow motion** (Edit → SMOOTH SLOW MOTION): ½, ¼ or ⅛ speed — or your own speed ramp — with new in-between frames drawn from the motion on the phone. No more stutter.
- Video editor: **Motion blur** (Edit → MOTION BLUR): natural camera-shutter blur on moving things (90°, 180° or 360°).
- Video editor: **Corner pin & planar tracking** (Layers → selected layer): drag a layer's four corners onto a screen, sign or wall, then track it so it stays stuck there as the camera moves.
- Video editor: **Shape layers** and **Lottie animation layers** (Layers → Shape / Lottie).
- Video editor: **Shape (pen) masks** (Color → Masks → Shape): a smooth curve with a soft edge and its own grade, which can track what it covers.
- Video editor: **Auto podcast switching** (Multicam → AUTO: WHO'S TALKING) — cuts to whoever is speaking, with an optional wide shot.
- Video editor: **Auto highlights** (Edit → AUTO HIGHLIGHTS) — a 15 s to 2 min reel of the liveliest moments, cut between words.
- Video editor: **Smarter filler removal** — finds um / uh, repeats and stutters, and (if you want) filler phrases like "you know"; the transcript now keeps what was really said.

## 1.0.323 — 2026-10-20
- Video editor Settings: **Scroll wheel effects** now sits under APP COLOUR.

## 1.0.321 — 2026-10-19
- Settings (⚙) → **Scroll wheel effects**: switch the timeline's scroll wheels between the animated glow look and plain colours with no animation.
- Video editor timeline: a **horizontal scroll wheel** along the bottom, matching the vertical one — drag it like a wheel to move through the edit left and right, or tap to jump. It has its own window from the left edge to the vertical wheel's window, so it never covers a track.

## 1.0.318 — 2026-10-19
- Video editor: **Morph cut** (Transitions → Basic) has been rebuilt — VeyroCut Pro's own optical-flow engine analyses the jump cut and draws brand-new in-between frames, so a head glides to its new position instead of jumping or cross-fading. Shown in the preview; drawn at full size (with each clip's grade and effects) in the export.
- Video editor: **Magic Mask** (Color → Detail → BACKGROUND, and Layers → REMOVE BACKGROUND) — tap or box any person, animal, car or object; the AI outlines it and follows it through the clip frame by frame (MobileSAM on the phone), handling things passing in front and stopping at scene cuts. Blur, recolour or remove the background, or invert to blur / hide the object itself.
- Video editor: the background cut-out (people or Magic Mask) now shows live in the preview exactly as the export makes it, with finer edges that follow hair and outlines.
- Releases now include a separate native debug-symbols file for the Google Play Console (not part of the app).

## 1.0.309 — 2026-10-19
- Video editor: **Shot match** (Color → Film & lens) — one tap makes a clip's brightness and colour match another clip.
- Video editor: **Film emulation** — 35mm, 16mm, Super 8 and more, or your own halation, bloom, grain and gate weave.
- Video editor: **Lens correction** — straightens wide-angle bending (action cam, drone and ultra-wide presets), removes colour fringes, brightens dark corners.
- Video editor: **Deflicker** — evens out flickering timelapses, hyperlapses and lights.
- Video editor: **Background removal (people)** — blur, recolour or remove the background behind people (AI on the phone), and cut people out of video layers.
- Video editor: **Generators** (Media page) — black, colour, gradient, noise and colour-bars clips, and a film countdown leader.
- Video editor: **Split screen** (2 side by side, 2 stacked, 3 columns, 2 × 2 grid) and **picture in picture**, with frame shape, rounded corners, border and drop shadow for video layers.
- Video editor: **Smooth cut** transition — hides jump cuts in interviews.
- Video editor: **Word-by-word captions** (Text page) — each word lights up as it's spoken, made on the phone.
- Video editor: **Title templates** (Layers page) — lower third, big title, cinematic, location, chapter, subscribe and end card.
- Video editor: **Source viewer & three-point editing** — mark in / out on a file, then INSERT, OVERWRITE or APPEND at the playhead.
- Video editor: **Bins**, **colour labels**, **relink missing media** and **project archive** (.zip with every file, to move or back up a project).

## 1.0.305 — 2026-10-19
- Video editor: **Scene detection** (Edit → SCENE DETECTION) — finds every camera cut in a long clip or a finished film and splits it into separate shots.
- Video editor: **Select clips** (Edit → SELECT CLIPS) — tick several clips and delete, mute, change speed, duplicate or copy a grade to all of them at once.
- Video editor: **Music tools** (Audio page) — FIT MUSIC TO VIDEO makes the song end exactly with your video on its real ending; AUTO-EDIT MY CLIPS TO THE MUSIC cuts your clips on every 1st, 2nd, 4th or 8th beat.
- Video editor: **Before | After** split on the player (Color page, Android 13+) — drag the divider to compare the ungraded picture with your grade.
- Video editor: **Version history** (Settings ⚙) — a version of the project is kept on every save and every 10 minutes; RESTORE any of the last 30.
- Video editor: **YouTube chapters** (Deliver) — your markers become a chapter list ready to paste into the YouTube description.
- Video editor: **Rolling credits** — Layers → Credits, plus a Roll (credits) animation for any text layer.
- Video editor: **Keyboard shortcuts** for Bluetooth / USB keyboards — Space / J / K / L, frame steps, cut to cut, split, ripple delete, markers, in / out, undo / redo and save (full list in the guide).

## 1.0.300 — 2026-10-19
- Video editor: **Text-based editing** (new Text page) — transcribe the edit on your phone (on-device whisper.cpp; the speech model downloads once), tap words to cut them, REMOVE FILLERS for um / uh / øh, then APPLY CUTS. Undo brings everything back.
- Video editor: **Clip keyframes** (Edit → Keyframes) — animate zoom, position, rotation and opacity over time with Smooth / Linear / Hold easing; diamonds on the timeline; preview and export.
- Video editor: **Voice isolation (AI)** in Audio → Voice noise reduction — keeps speech and removes wind, traffic, hum, crowds and background music (RNNoise, on the phone).
- Video editor: **Background export** with a progress notification — exports keep going when you switch apps or lock the screen — and a **Render queue** to export several versions in one go.

## 1.0.292 — 2026-10-19
- The timeline scroll wheel now sits in its own narrow window on the right edge of the timeline (same place, full height, same effect, still scrolls the tracks up and down), so it never covers a track — the tracks end just before it.

## 1.0.290 — 2026-10-19
- Video editor timeline: the trim handles on clips, layers and audio are now about three times wider and much easier to grab (bigger touch zone, still a third of the bar at most on short items).
- The timeline scroll wheel is now as tall as the timeline and grows as you add tracks, with the same upscale effect.

## 1.0.288 — 2026-10-19
- Video editor timeline: video clips on V1 now have trim handles too — hold the start or end of a clip and drag to shorten or lengthen it (up to the full length of its video file; the clips after it move along). Video layers can't be stretched past the end of their own clip.
- Fixed: a video layer added ABOVE the main video showed its timeline row under V1. Layers on top of the video now sit above the V1 row (the top row is the topmost layer) and layers placed below the video sit under V1. Drag a row up / down to reorder within its group.

## 1.0.286 — 2026-10-19
- Video editor: add as many video layers as you like, each above or below the main video. Tap + VIDEO on the timeline (or Layers → Video), choose ABOVE or BELOW, then pick the clip. Above layers sit on top (picture-in-picture, or full frame); below layers sit behind your clips and show where the main video doesn't cover the frame. Change it any time under the layer's PLACEMENT; rows are marked ↑ / ↓ on the timeline, and the preview and export stack them the same way.

## 1.0.284 — 2026-10-19
- Video editor timeline: every layer and audio bar now has trim handles at both ends (a bracket tab). Hold a handle and drag to lengthen or shorten — never past the start or end of the video — with the new length shown above the bar while you drag.
- Video editor: the eye switch next to each layer now always hides that layer from the video feed, even while it's selected (it was still shown when selected); tap again to show it. Hidden layers are left out of the export as before.

## 1.0.281 — 2026-10-19
- Fixed: the video editor closed the app as soon as it opened (1.0.278). The new timeline scroll wheel could be drawn at zero height for a moment, which Android doesn't allow; it now skips that frame.
- Video editor: RECOVER (unsaved edit) no longer crashes the app — the edit is checked first, and clips or sounds that can no longer be read (moved, deleted or access lost) are left out with a note. Auto-saved edits now keep access to their media after a restart.
- New: if the app ever closes unexpectedly, the next start shows a short crash report you can share or copy (it holds no photos or videos), so problems can be fixed quickly.

## 1.0.278 — 2026-10-19
- Video editor → Deliver → Audio only: choose the format — AAC .m4a, WAV 16-bit, WAV 24-bit, WAV 32-bit float, AIFF 24-bit, FLAC (lossless) or Opus .ogg (Android 10+). WAV, AIFF and FLAC are written from the finished mix itself, so nothing is lost. MP3 isn't offered: Android has no MP3 encoder and the usual one needs licence terms that don't suit a paid app.
- Video editor: a small glowing scroll wheel in the timeline's lower-right corner moves the tracks up and down (drag like a wheel, or tap to jump), with an AI-upscale-style effect while it moves. The timeline now scrolls when it has more tracks than fit.

## 1.0.275 — 2026-10-19
- Video editor: 16 new transitions — a new Cinematic group (film burn, anamorphic flare, lens warp, letterbox close, aperture, smoke, shatter, kaleidoscope, cube turn, card flip, sun rays, neon edges, ember drift) and three new glitch styles (datamosh, VHS rewind, pixel sort). All are original effects written for VeyroCut Pro, so there's nothing to license; they show in the preview and the export.

## 1.0.273 — 2026-10-19
- Photo and video editors: start-page thumbnails show a small, readable file-type tag (DNG RAW, JPEG, PNG, HEIC / MP4, MOV, MKV…) while you browse; the editors themselves stay clean.

## 1.0.271 — 2026-10-19
- Video editor: new Proxy tab next to Media — add only the clips you want, switch Proxy on or off, choose 360p / 540p / 720p / 1080p; proxies are made in the background with progress and cancel, stay in sync with the originals (same timing, frame rate, orientation and sound), fall back to the original if missing or broken, and exports always use the original files. Replaces the old automatic preview copies.

## 1.0.268 — 2026-10-19
- Photo editor: tested with a real Mavic 3 Pro DNG — the drone's vignetting correction (stored per colour after the lens profile) is now applied, so corners are no longer dark; the drone name now comes from the photo itself (e.g. "Mavic 3 Pro" instead of "Mavic 3").

## 1.0.266 — 2026-10-19
- Photo editor: full DJI drone RAW support — DNGs from DJI drones open with the drone's own lens calibration applied (distortion fixed, switchable), RAW INFO shows the drone model and the flight data stored in the photo (height, altitude, GPS, gimbal angles, heading, speed), and MERGE PHOTOS (HDR / panorama) now develops DNG brackets from the RAW data instead of their small preview.

## 1.0.264 — 2026-10-19
- Photo editor: full DNG RAW support — opens DNGs from Fujifilm X-Trans and Quad Bayer sensors, lossy-JPEG and Deflate DNGs and floating-point HDR DNGs; applies every correction stored in the file (bad pixels, maps, lens shading, lens distortion, colour fringing, vignetting — switchable); uses both of the camera's colour calibrations, forward matrices and calibration data for more accurate colour at any white balance; new RAW PROFILE → Camera uses the maker's own profile stored in the DNG; new RAW INFO (camera, lens, exposure, sensor, compression, corrections).

## 1.0.262 — 2026-10-19
- The app now speaks Danish and Faroese: Settings → LANGUAGE · SPROG · MÁL (Phone language, English, Dansk, Føroyskt). It follows the phone's language by default; app-icon shortcuts are translated too.

## 1.0.259 — 2026-10-19
- Capture: face blur now works all the time in Video and Pro Video — faces are found in the camera picture itself when the phone can't run a separate analysis stream (4K, high frame rates).
- Video editor: 4:2:2 footage support — 10-bit 4:2:2 (and 4:4:4) H.264 / H.265 clips are detected (badge in the Media page), read with a decoder on the phone that can handle them, edited through a smooth preview copy and exported from the original. Clips no decoder on the phone can read are clearly marked.

## 1.0.256 — 2026-10-18
- Simple / Pro layout switch (Capture settings and both editors' settings).
- Backup & restore: one file with all settings, presets, LUTs, watermarks, sounds and projects.
- Quick tour of the camera gestures on first start; replay it from Settings.
- Gallery: delete now moves to a Trash tab (Android 11+), restorable for 30 days.
- App icon shortcuts (Video, Pro photo, Photo editor, Video editor) and a Quick Settings camera tile.
- Share or "Edit with" from other apps opens photos in the photo editor and videos in the video editor.

## 1.0.254 — 2026-10-18
- Video editor: MULTICAM — sync 2–4 angles by sound and cut between them live while they play.
- Video editor: SYNC by sound for audio tracks — external recorder / wireless mic sound lines up with the clip automatically.
- Video editor: DRONE TELEMETRY — DJI .SRT flight data (altitude, speed, GPS, camera) as an overlay that follows the clip.
- Video editor: TRIM TOOLS (slip, roll, slide by frames) and NEST / UN-NEST for compound clips.
- Video editor: auto-save every 20 seconds and RECOVER on the start page after an unexpected close.
- Photo editor: IPTC title, caption and keywords in exports, and an optional .xmp sidecar with the edit.

## 1.0.252 — 2026-10-18
- Video editor: new SOUND LIBRARIES on the Media page — 26 built-in cinematic soundtracks you can listen to and add to the timeline at the playhead; they play in the preview and are mixed into the export. A Nature sounds section is ready for more.
- Capture: stabilization is now a choice — OFF, IN-APP (CROP) or GYROFLOW. Gyroflow records the phone's motion sensors into a .gcsv file for each video (Documents/VeyroCutPro/Gyroflow, or next to the clip in a picked folder) for gimbal-smooth stabilization in Gyroflow on a computer; the camera's own stabilization is switched off for it.
- Capture: new LONG EXP smart mode — Light trails, Silky water and Night sky, 2 to 30 seconds, with the picture building up live on screen.
- Capture: motion blur (electronic ND) for video — ×2 / ×4 / ×8 frame blending for smooth, cinematic movement in bright light (quick bar ND BLUR or Settings).
- Capture: remote monitor & control — open the address shown in Settings in any browser on a second device on the same Wi-Fi to see the live picture and take photos, record, zoom and switch modes.

## 1.0.249 — 2026-10-18
- Photo editor: the same SKIP — OPEN THE EDITOR button under Browse files. The editor opens with no photo and shows Gallery / Browse files buttons to add one. Only when you tap it, never automatic.

## 1.0.247 — 2026-10-18
- Video editor: new SKIP — OPEN THE EDITOR button on the start page goes straight into the editor with an empty timeline, without choosing a video first. It's a button you tap each time, never automatic.

## 1.0.245 — 2026-10-18
- New app ID: com.dronedirector.veyrocutpro (matches the VeyroCut Pro name, ready for the Play Store). Phones see this as a new app: uninstall the old AcluxPro / VeyroCut Pro first. Settings and presets start fresh; your photos and videos are untouched and still show in the gallery.

## 1.0.243 — 2026-10-18
- The app is now called **VeyroCut Pro** (formerly AcluxPro). New photos and videos save to DCIM/VeyroCutPro (sounds to Music/VeyroCutPro); the gallery still shows everything in the old DCIM/AcluxPro folder. It installs over the previous version as before.
- Capture: autofocus by default in every mode. Tap still focuses where you tap. Press and hold the viewfinder for 2 seconds (a ring fills) to switch to manual focus: a narrow vertical focus slider appears on the right edge of the feed, with ∞ at the top, a live distance tag and the focus magnifier while you drag. A small ✕ above it returns to autofocus.
- Capture: the exposure lock is now a short hold and release (about ½ second, it ticks); keep holding to 2 seconds for manual focus instead.

## 1.0.241 — 2026-10-17
- Photo editor: RAW (DNG) develop. DNGs are now developed from the real sensor data instead of being opened as a JPEG-like picture: exposure (±5 stops), highlights, shadows, whites, blacks and white balance work with the full range (bright skies come back, shadows lift cleanly). New RAW PROFILE choice (Standard, Flat, Vivid, Portrait, Landscape, Mono) and white balance presets (As shot, Auto, Daylight, Cloudy, Shade, Tungsten, Fluorescent). RAW badge on the photo; full-quality demosaic on export; RAWs in batch exports are developed too.

## 1.0.239 — 2026-10-17
- Photo editor: removed the back arrow from the top bar so the EXPORT button is no longer squeezed (it stays on one line). Use the phone's back gesture / button to leave the editor.

## 1.0.237 — 2026-10-17
- Capture: the portrait mode with the live viewfinder blur is now called AI PORTRAIT. A new PORTRAIT mode does everything the same (any subject, slider, AUTO, blur added right after the shot) without the live preview.

## 1.0.235 — 2026-10-17
- Capture: PORTRAIT now shows the background blur live in the viewfinder, following the slider (or AUTO) as you move it.
- Capture: PORTRAIT works on more than people — pets, flowers, rocks, tree branches and other subjects that stand out (ML Kit subject segmentation on the phone), both live and in the saved photo. "no subject" shows when nothing is found yet.

## 1.0.233 — 2026-10-17
- Capture: PORTRAIT blur dial replaced by a slim slider (much lower, takes less of the viewfinder) — drag or tap, f/1.4 to f/16 in third stops; the chosen f-number shows next to BACKGROUND BLUR. AUTO still minimises it.

## 1.0.231 — 2026-10-17
- Capture: PORTRAIT blur panel now sits above the filter / zoom bar instead of overlapping it.
- Capture: PORTRAIT blur dial — a 180° wheel you turn left / right, f/1.4 to f/16 in third stops (tap a number to jump to it). Minimise it (AUTO) and the blur is picked for you from how much of the frame the subject fills.

## 1.0.229 — 2026-10-17
- Capture: PORTRAIT mode (Smart modes) — the person (or any subject) stays sharp and the background is blurred like a fast lens; choose f/1.4 – f/5.6 under the viewfinder. People are found on the phone; other subjects with the subject AI (or Cloud AI when it's on).
- Capture: rebuilt panorama — the gyroscope decides when to take each frame (about 180°, no more turning almost all the way round); frames are projected onto a cylinder, aligned in 2D, brightness-matched, blended with wide soft seams and trimmed, so no more black gaps or jumps.

## 1.0.226 — 2026-10-17
- Capture: 4K recordings were sometimes saved in 1080p — the camera now keeps the chosen resolution first (dropping the extra analysis stream instead) and tells you if a resolution really isn't possible with the current settings.

## 1.0.224 — 2026-10-17
- Photo editor: better heal — the patch is colour-matched smoothly from its edge inward and the automatic source avoids edges and objects; heal and clone spots can now be dragged (solid circle = the spot, dashed circle = where it copies from) and held to delete.

## 1.0.221 — 2026-10-17
- External storage for capture, the video editor and the photo editor: each has its own SAVE LOCATION (Settings) — internal storage, an SD card / USB drive Android lists, or a folder you pick on any USB stick, SSD or SD card. Videos record straight onto the drive; the gallery shows files from picked folders; if the drive is unplugged, AcluxPro saves to internal storage and tells you. The editors can follow capture ("Same as capture").

## 1.0.219 — 2026-10-17
- Capture: fixed privacy face blur turning the viewfinder / recording black on some devices; if a device's graphics chip can't run the face-blur shader, the picture now stays normal instead.

## 1.0.217 — 2026-10-17
- Video editor: motion tracking (Layers → KEYFRAMES → TRACK MOTION) — a layer follows a moving object; the path becomes position keyframes you can still adjust.
- Video editor: adjustment layers (Layers → Adjustment) — one grade (exposure, contrast, colour, look…) over every clip in its time range, with strength; live in the preview and in the export.

## 1.0.214 — 2026-10-17
- Video editor: keep HDR (HLG / HDR10) in the export — Deliver → DYNAMIC RANGE (10-bit HEVC).
- Photo editor: sky replacement (Effects → SKY REPLACEMENT) — AI finds the sky; choose Clear blue, Golden hour, Sunset, Dramatic, Night or your own picture; strength and light matching.
- Photo editor: clone stamp (Heal → Clone stamp) — tap the source, then tap where to paint exact copies with the same offset.

## 1.0.210 — 2026-10-17
- Photo and video editors: point curves — RGB, red, green and blue; tap to add points, drag to move, double-tap to remove (Curve / Curves tab).
- Video editor: animated GIF export (Deliver → ANIMATED GIF) — 320 / 480 / 640 px, 10–15 fps, up to 20 s, with the grade and text / logo layers.
- Video editor: marker notes — the Media page lists every marker; tap the time to jump, type a note; notes are saved in the project and exported in the FCPXML.
- Photo editor: Text tab — add text (font, colour, plain / shadow / box) and stickers, with size, position and opacity; saved in projects and exports.
- Capture: slate & shot log — scene / shot / take and notes on the viewfinder and in photos; every clip and photo logged with its settings; export as .csv; automatic next take.

## 1.0.203 — 2026-10-17
- Video editor: voice noise reduction per clip (Audio → Off / Light / Strong), applied on export.
- Photo editor: History panel — every step (tap to go back) and snapshots you can save and jump between.
- Video and photo editors: the pull-out scope drawer now has HIST, WAVE (waveform), PARADE (RGB parade) and VECT (vectorscope).
- Video editor: export the AI captions as subtitles (.srt) — Deliver → SEND TO DESKTOP EDITOR.
- Capture: safe areas — action-safe (90 %) and title-safe (80 %) frames (Settings → MONITORING & LENS).

## 1.0.198 — 2026-10-17
- Copyright: your name and copyright notice (Settings → COPYRIGHT) are written into every photo you take and every photo exported from the photo editor.
- Gallery: culling — 1–5 star ratings, ✓ pick and ✕ reject in the viewer; marks on the thumbnails; filters (★1+, ★3+, ★5, picks, rejects, hide rejects); delete all rejects at once.
- Video editor: send the edit to a desktop editor — Deliver → SEND TO DESKTOP EDITOR saves an EDL (cuts, dissolves, speed) or FCPXML (cuts, markers) for desktop editing software (Final Cut Pro.
- Capture: red tally border while recording, touch lock (padlock; hold to unlock) and warnings for low storage, low battery and heat — each can be hidden in the Panels menu.
- Capture: custom set-ups C1 / C2 / C3 in the quick bar — hold to save the current settings, tap to recall (can be hidden in the Panels menu).

## 1.0.191 — 2026-10-17
- Capture: the long-press exposure lock now works the same on tablets (fixed hold time, not affected by pinch zoom); also works in clean feed.

## 1.0.188 — 2026-10-17
- Capture: long press the viewfinder to lock the exposure, with an EV slider under the lock; long press again or tap the small ✕ to unlock.

## 1.0.186 — 2026-10-17
- Video editor and photo editor: estimated time remaining shown under "Rendering…" / "Exporting…" while exporting (batch exports too).
- Photo editor: the RGB histogram is now the same pull-out drawer as in the video editor — drag or tap the tab on the right edge of the photo.
- Capture: voice noise reduction (Light / Strong) for recorded sound.
- Capture: privacy face blur — faces pixelated live in the viewfinder, in videos and in photos (on-device detection); quick BLUR switch.
- Capture: title on videos — lower third, centre or top, timed, with a fade / slide in; quick TITLE switch and a viewfinder preview.
- Capture: auto-enhance — an extra, improved copy of each photo.
- Panels menu (Capture): hide the BLUR and TITLE switches and the title preview.

## 1.0.180 — 2026-10-17
- Photo editor: colour-range and brightness-range masks — tap the photo to pick a colour or brightness; range and softness sliders.
- Video editor: colour masks (Color → Masks) — up to 3 per clip: gradient, radial, colour range, brightness range, each with its own grade; live on the player and in the export.
- Video editor: Remove silences (Edit) — cuts the pauses out of talking clips, for one clip or all clips.
- Video editor: layer animations (Layers → ANIMATION) — fade, slide, pop, zoom, bounce, wipe reveal and typewriter, in and out.
- Video editor: loudness normalisation (Deliver → LOUDNESS) — −14, −16 or −23 LUFS.

## 1.0.174 — 2026-10-17
- Video editor: 14 more transition sounds (24 in all), now grouped — Hits & impacts (braam, drum hit, punch, thunder, heartbeat), Whooshes & air (deep whoosh, wind gust), Builds & risers (clock tick build, tape stop), Stylized & sci-fi (record scratch, laser zap, magic shimmer, camera shutter, TV static).

## 1.0.171 — 2026-10-17
- Video editor: import your own transition sounds — Transitions → Audio transitions → MY SOUNDS → + IMPORT SOUND (MP3, WAV, M4A, AAC, OGG, FLAC). Choose whether each sound starts on, centres on or ends on the cut; ▶ to listen, ✕ to remove.
- Video editor: sound effects on cuts — Transitions → Audio transitions → SOUND EFFECTS: cinematic impact, cinematic boom, trailer hit, sub drop, whoosh, fast swoosh, swirl, riser, reverse cymbal and glitch. Impacts land on the cut; risers build up to it. Volume per cut, ▶ to listen, in the preview and the export; APPLY TO ALL CUTS includes the effect.

## 1.0.168 — 2026-10-17
- Video editor: the Transitions page now has two sub-menus — VIDEO TRANSITIONS (the existing 34) and AUDIO TRANSITIONS.
- New audio transitions: match video, hard cut, constant gain, constant power, exponential fade, dip to silence, quick out / slow in and slow out / quick in. They work in the preview and the export, over a video transition or on a plain cut (fade out / in around the cut, 0.1–3 s), with APPLY TO ALL CUTS.

## 1.0.166 — 2026-10-17
- Video editor: the histogram toggle in the Panels menu is now called "Histogram pull-out".

## 1.0.164 — 2026-10-17
- Photo editor: Panels menu (icon next to the settings gear) — hide the tools panel, copy / paste bar, histogram or before / after buttons; the photo grows into the space and shrinks back when a panel returns. Presets: Full, View, Edit, Color. Triple-tap the icon to reset the layout.
- Photo editor: the histogram on / off choice is now remembered.
- Photo editor and Video editor: the screen name is gone from the top bar, leaving more room for the icons and buttons.

## 1.0.161 — 2026-10-17
- New Panels menu in Capture and the Video editor (icon next to the settings gear): hide the parts you don't need. Presets: Capture — Full, Minimal, Video; Video editor — Full, Cut, Color, Audio. Choices are remembered.
- Video editor: hidden panels give their space to the player and timeline; shown again, everything returns to its saved size.
- Triple-tap the panels icon to reset the layout to the original (all panels shown, positions and divider sizes back to default). Also available as RESET LAYOUT in the menu.

## 1.0.159 — 2026-10-16
- Video editor: the RGB histogram is now a pull-out drawer on the right edge of the player — drag the tab out / back in, or tap it; hidden in full screen.
- Capture settings: rows with many choices (Scope, Anamorphic desqueeze, Interval…) now show the title above the choices — no more vertical text or big empty gaps.

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
