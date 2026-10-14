#!/usr/bin/env bash
# Installs the ProCam APK on a running emulator and captures phone + tablet screenshots.
# Usage: screenshots.sh <apk> <output-dir>
set -u

APK="$1"
OUT="$2"
PKG=com.dronedirector.procam
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
  adb shell am start -W -n "$ACT" "$@" >/dev/null
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

# ---- phone (Pixel-size emulator)
rotate 0
shot 01-phone-photo-filters --es mode PHOTO --es filter VIVID --ez filters true
shot 02-phone-pro-photo-iso --es mode PRO_PHOTO --es param ISO
shot 03-phone-pro-photo-peaking --es mode PRO_PHOTO --es param FOCUS --ez peaking true --ez zebras true
shot 04-phone-video-cinema --es mode VIDEO --es filter CINEMA
shot 05-phone-pro-video-scope --es mode PRO_VIDEO --es guide SCOPE --es param EV
shot 06-phone-panorama --es mode PANORAMA
shot 07-phone-settings --ez settings true
shot 15-phone-pro-video-dlog --es mode PRO_VIDEO --es profile DLOG
shot 16-phone-live-youtube-connect --es mode LIVE --es live YOUTUBE
shot 17-phone-live-tiktok --es mode LIVE
rotate 1
shot 08-phone-landscape-pro-video --es mode PRO_VIDEO --es guide FLAT
shot 09-phone-landscape-photo-mono --es mode PHOTO --es filter MONO

# ---- tablet (resize the same emulator to a 10" tablet screen)
adb shell wm size 1600x2560
adb shell wm density 320
rotate 1
shot 10-tablet-landscape-pro-photo --es mode PRO_PHOTO --es param WB
shot 11-tablet-landscape-video --es mode VIDEO --es filter WARM --ez filters true
shot 12-tablet-settings --ez settings true
shot 18-tablet-live-twitch --es mode LIVE --es live TWITCH
rotate 0
shot 13-tablet-portrait-pro-video --es mode PRO_VIDEO --es guide VERTICAL
shot 14-tablet-portrait-panorama --es mode PANORAMA
adb shell wm size reset
adb shell wm density reset

# ---- logs for anything that went wrong
adb logcat -d -b crash > "$OUT/crash-log.txt" || true
adb logcat -d -s CameraX:* Camera2CameraImpl:* AndroidRuntime:* > "$OUT/camera-log.txt" 2>/dev/null || true
[ -s "$OUT/crash-log.txt" ] || rm -f "$OUT/crash-log.txt"
ls -la "$OUT"
