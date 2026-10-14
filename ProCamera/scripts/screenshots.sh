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
adb logcat -c

rotate() { # 0 = portrait, 1 = landscape
  adb shell settings put system user_rotation "$1"
  sleep 2
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

# ---- phone (Pixel-size emulator)
rotate 0
shot 01-phone-photo
shot 02-phone-iso-dial --es param ISO
shot 03-phone-focus-peaking --es param FOCUS --ez peaking true --ez zebras true
shot 04-phone-settings --ez settings true
shot 05-phone-video --es mode VIDEO
rotate 1
shot 06-phone-landscape-photo --es param SHUTTER
shot 07-phone-landscape-video --es mode VIDEO

# ---- tablet (resize the same emulator to a 10" tablet screen)
adb shell wm size 1600x2560
adb shell wm density 320
rotate 1
shot 08-tablet-landscape-photo --es param ISO
shot 09-tablet-landscape-video --es mode VIDEO
shot 10-tablet-settings --ez settings true
rotate 0
shot 11-tablet-portrait-photo --es param WB
adb shell wm size reset
adb shell wm density reset

# ---- logs for anything that went wrong
adb logcat -d -b crash > "$OUT/crash-log.txt" || true
adb logcat -d -s CameraX:* Camera2CameraImpl:* AndroidRuntime:* > "$OUT/camera-log.txt" 2>/dev/null || true
[ -s "$OUT/crash-log.txt" ] || rm -f "$OUT/crash-log.txt"
ls -la "$OUT"
