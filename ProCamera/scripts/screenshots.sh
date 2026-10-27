#!/usr/bin/env bash
# Installs the ProCam APK on a running emulator and captures phone + tablet screenshots.
# Usage: screenshots.sh <apk> <output-dir>
set -u

APK="$1"
OUT="$2"
PKG=com.dronedirector.veyrocutpro
ACT="$PKG/.MainActivity"
mkdir -p "$OUT"

adb wait-for-device
adb install -r -g "$APK" || { echo "install failed"; exit 1; }
adb shell settings put system accelerometer_rotation 0
# Skip Android's one-time "Viewing full screen" notice.
adb shell settings put secure immersive_mode_confirmations confirmed
# The slow CI emulator's launcher sometimes freezes in the background; don't show its ANR box.
adb shell settings put global anr_show_background 0
adb shell settings put global hide_error_dialogs 1
# The launcher isn't needed for screenshots and is what keeps freezing; switch it off.
for L in com.google.android.apps.nexuslauncher com.android.launcher3; do
  adb shell pm disable-user --user 0 "$L" >/dev/null 2>&1 || true
done
adb logcat -c

CUR=0
rotate() { # 0 = portrait, 1 = landscape
  adb shell settings put system user_rotation "$1"
  # Also turn the virtual device itself, so its camera and level sensor match the screen.
  if [ "$1" != "$CUR" ]; then
    if [ "$1" = 1 ]; then adb emu rotate; else adb emu rotate; adb emu rotate; adb emu rotate; fi
    CUR="$1"
  fi
  sleep 3
}

shot() { # name, extra am-start args...
  local name="$1"; shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACT" --ez skipsetup true "$@" >/dev/null
  sleep 9
  adb exec-out screencap -p > "$OUT/$name.png"
  if ! adb shell pidof "$PKG" >/dev/null; then
    echo "!! $name: app not running (crashed?)" | tee -a "$OUT/problems.txt"
  fi
  echo "captured $name"
}

# Let System UI settle after the launcher change, then warm the camera up once.
sleep 15
adb shell am start -W -n "$ACT" >/dev/null; sleep 10; adb shell am force-stop "$PKG"

# Presses the shutter (camera key), waits, screenshots mid-capture, presses again to stop.
record_test() { # name, seconds
  adb shell input keyevent KEYCODE_CAMERA
  sleep "$2"
  adb exec-out screencap -p > "$OUT/$1.png"
  adb shell input keyevent KEYCODE_CAMERA
  sleep 6
  echo "captured $1"
}

# ---- phone (Pixel-size emulator)
rotate 0
shot 50-phone-video-editor-light --es screen video --es theme light
shot 51-phone-photo-editor-light --es screen photo --es theme light
shot 52-phone-video-editor-dark --es screen video --es theme dark
# Into the editor itself (Skip — open the editor): the button row under the picture, Play centred with its glow.
adb shell input tap 540 980; sleep 7
adb shell input tap 412 1630; sleep 3   # the tour's SKIP
# Clarity / texture test: the same test clip with them off and fully on (the pictures must differ).
for amt in 0 1; do
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$ACT" --ez skipsetup true --es screen video --es fxtest $amt >/dev/null
  sleep 40; adb shell input tap 412 1630; sleep 5
  adb exec-out screencap -p > "$OUT/55-fxtest-clarity-$amt.png"; echo "captured 55-fxtest-clarity-$amt"
  adb logcat -d | grep -E "FxTest|GradePreview|DetailEffect|VideoFrameProcessor|ExoPlayer" | tail -40 > "$OUT/55-fxtest-log-$amt.txt"
done
adb exec-out screencap -p > "$OUT/53-phone-video-editor-open.png"; echo "captured 53-phone-video-editor-open"
sleep 1; adb exec-out screencap -p > "$OUT/54-phone-video-editor-open-b.png"; echo "captured 54-phone-video-editor-open-b"
shot 01-phone-photo-filters --es mode PHOTO --es filter VIVID --ez filters true
shot 02-phone-pro-photo-iso --es mode PRO_PHOTO --es param ISO
shot 03-phone-pro-photo-peaking --es mode PRO_PHOTO --es param FOCUS --ez peaking true --ez zebras true
shot 04-phone-video-cinema --es mode VIDEO --es filter CINEMA
shot 05-phone-pro-video-scope --es mode PRO_VIDEO --es guide SCOPE --es param EV
shot 06-phone-panorama --es mode PANORAMA
shot 07-phone-settings --ez settings true
shot 15-phone-pro-video-dlog --es mode PRO_VIDEO --es profile DLOG
shot 16-phone-live-youtube-connect --es mode LIVE --es live YOUTUBE
shot 17-phone-live-tiktok --es mode LIVE --es live TIKTOK --ez nosetup true
shot 19-phone-live-youtube --es mode LIVE --es live YOUTUBE --ez nosetup true
shot 21-phone-timelapse --es mode TIMELAPSE
record_test 21b-phone-timelapse-recording 12
shot 22-phone-hyperlapse --es mode HYPERLAPSE --es filter CINEMA
record_test 22b-phone-hyperlapse-recording 10
shot 23-phone-slowmo --es mode SLOW_MO
shot 24-phone-burst --es mode BURST
adb shell input keyevent KEYCODE_CAMERA; sleep 10
adb exec-out screencap -p > "$OUT/24b-phone-burst-after.png"
shot 26-phone-gallery --ez gallery true
# Open the newest item in the full-screen viewer.
DENS=$(adb shell wm density | grep -o '[0-9]\+' | tail -1)
adb shell input tap $((64 * DENS / 160)) $((205 * DENS / 160))
sleep 4
adb exec-out screencap -p > "$OUT/27-phone-gallery-viewer.png"
# Back to the grid, Videos tab, open the first video and press play.
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap $((226 * DENS / 160)) $((129 * DENS / 160)); sleep 2
adb shell input tap $((64 * DENS / 160)) $((205 * DENS / 160)); sleep 3
adb shell input tap $((205 * DENS / 160)) $((457 * DENS / 160)); sleep 3
adb exec-out screencap -p > "$OUT/28-phone-gallery-video.png"
shot 32-phone-photo-16x9 --es mode PHOTO --es aspect R16_9
shot 35-phone-pro-video-falsecolor --es mode PRO_VIDEO --ez falsecolor true
shot 39-phone-pro-video-zebras --es mode PRO_VIDEO --ez zebras true --ei zebralevel 70
shot 40-phone-pro-video-zebras-fallback --es mode PRO_VIDEO --ez zebras true --ei zebralevel 70 --ez screenanalysis true
shot 41-phone-pro-video-falsecolor-fallback --es mode PRO_VIDEO --ez falsecolor true --ez screenanalysis true
shot 36-phone-video-film-teal --es mode VIDEO --es filter TEAL_ORANGE --ez filters true
shot 37-phone-video-film-bleach --es mode VIDEO --es filter BLEACH
shot 38-phone-clean-feed --es mode VIDEO --ez clean true
shot 33-phone-pro-photo-16x9 --es mode PRO_PHOTO --es aspect R16_9
shot 30-phone-accent-blue --es mode PRO_VIDEO --es accent BLUE
shot 31-phone-accent-pink-settings --es accent PINK --ez settings true
# Back to the default gold for the remaining shots.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACT" --es accent GOLD >/dev/null; sleep 3
rotate 1
shot 08-phone-landscape-pro-video --es mode PRO_VIDEO --es guide FLAT
shot 09-phone-landscape-photo-mono --es mode PHOTO --es filter MONO
shot 34-phone-landscape-photo-16x9 --es mode PHOTO --es aspect R16_9
shot 20-phone-landscape-live-tiktok --es mode LIVE --es live TIKTOK --ez nosetup true
# Turn the phone while the app stays open: the viewfinder must turn with it.
shot 29a-phone-rotate-start-landscape --es mode VIDEO
rotate 0; sleep 3
adb exec-out screencap -p > "$OUT/29b-phone-rotated-to-portrait.png"
rotate 1; sleep 3
adb exec-out screencap -p > "$OUT/29c-phone-rotated-back-landscape.png"

# ---- tablet (resize the same emulator to a 10" tablet screen)
adb shell wm size 1600x2560
adb shell wm density 320
rotate 1
shot 10-tablet-landscape-pro-photo --es mode PRO_PHOTO --es param WB
shot 11-tablet-landscape-video --es mode VIDEO --es filter WARM --ez filters true
shot 12-tablet-settings --ez settings true
shot 25-tablet-landscape-timelapse --es mode TIMELAPSE
shot 18-tablet-live-twitch --es mode LIVE --es live TWITCH
rotate 0
shot 13-tablet-portrait-pro-video --es mode PRO_VIDEO --es guide VERTICAL
shot 14-tablet-portrait-panorama --es mode PANORAMA
adb shell wm size reset
adb shell wm density reset

# ---- what got saved
{
  echo "== videos =="; adb shell content query --uri content://media/external/video/media --projection _display_name:_size:duration 2>&1
  echo "== images =="; adb shell content query --uri content://media/external/images/media --projection _display_name:_size 2>&1 | tail -15
} > "$OUT/saved-media.txt"

# ---- logs for anything that went wrong
adb logcat -d -b crash > "$OUT/crash-log.txt" || true
adb logcat -d -s CameraX:* Camera2CameraImpl:* AndroidRuntime:* > "$OUT/camera-log.txt" 2>/dev/null || true
[ -s "$OUT/crash-log.txt" ] || rm -f "$OUT/crash-log.txt"
ls -la "$OUT"
