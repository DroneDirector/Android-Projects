# Android Projects

## AlcorCut Pro — professional camera, photo and video editor for Android phones and tablets

**Download:** open the [latest release](../../releases/latest) and tap the `.apk` file, then open it to install.
The first time, Android asks you to allow installing apps from your browser; allow it once.

Every change pushed to `ProCamera/` is built automatically by GitHub Actions (see the **Actions** tab),
and the new APK appears on the Releases page a few minutes later. New builds install over the old one.

### Features

**Manual pro controls**
- Manual ISO and shutter speed in standard stops, with ISO-priority and shutter-priority behaviour
- Autofocus in every mode; hold the viewfinder 2 s for manual focus with a slim slider on the right edge (∞ to macro, ✕ back to AF); tap-to-focus/meter, AE lock
- White balance presets (3200K–7500K, twilight) and AWB lock
- Exposure compensation, live ISO / shutter / aperture / focus readouts
- Live histogram with highlight and shadow clipping warnings

**Video tools**
- 4K / 1080p / 720p / 480p, 24–60 fps (what the lens supports)
- FLAT log-style profile and HLG 10-bit HDR where available
- Stabilization toggle, high-bitrate mode, pause/resume, timecode and audio meter

**Composition aids**
- Rule of thirds, golden ratio (PHI), 4×4 and crosshair grids
- Electronic level that turns green when straight
- Zebras (adjustable 70–100%) and focus peaking
- Self-timer (3/5/10 s), burst (3/5/10), 4:3 / 16:9 / 1:1

**Formats & lenses**
- JPEG, RAW (DNG) and RAW+JPEG on supported lenses
- Switch between ultra-wide / main / telephoto lenses and the front camera, pinch zoom
- HDR, Night, Bokeh and Auto scene modes where the phone maker provides them
- Volume keys as shutter

Layouts adapt to phones and tablets in portrait and landscape. Files save to `DCIM/AlcorCutPro`.

### Building on a computer

Open the `ProCamera` folder in Android Studio, or run `./gradlew assembleRelease` inside it.
