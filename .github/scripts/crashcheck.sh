#!/usr/bin/env bash
set -x
TESTS=" $(cat tests.txt) "
want() { [[ "$TESTS" == *" all "* || "$TESTS" == *" $1 "* ]]; }
mkdir -p out
adb wait-for-device
adb install -r -g "$(ls apk/*x86_64*.apk 2>/dev/null | head -1 || true)" || adb install -r -g "$(ls apk/*.apk | head -1)" || echo "install failed"
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put global hide_error_dialogs 1
adb shell settings put global anr_show_background 0
for L in com.google.android.apps.nexuslauncher com.android.launcher3; do adb shell pm disable-user --user 0 "$L" >/dev/null 2>&1 || true; done
adb shell mkdir -p /sdcard/DCIM/VeyroCutPro
adb push test_clip.mp4 /sdcard/DCIM/VeyroCutPro/VID_TEST.mp4
adb push test_a.jpg /sdcard/DCIM/VeyroCutPro/IMG_TEST_A.jpg
adb push test_b.jpg /sdcard/DCIM/VeyroCutPro/IMG_TEST_B.jpg
for f in IMG_TEST_A.jpg IMG_TEST_B.jpg; do adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/VeyroCutPro/$f >/dev/null 2>&1 || true; done
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/VeyroCutPro/VID_TEST.mp4 || true
adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1 || true
sleep 5
adb logcat -c
tapText() {  # tap the centre of the first on-screen element whose text contains $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > ui.xml
  local xy
  xy=$(python3 - "$1" <<'PY'
import re,sys
t=open('ui.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', t):
    n=m.group(0)
    tx=re.search(r'text="([^"]*)"',n); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if tx and b and sys.argv[1].lower() in tx.group(1).lower():
        x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  echo "tap '$1' at: $xy"
  [ -n "$xy" ] && adb shell input tap $xy
}
shot() { adb exec-out screencap -p > "out/$1.png"; }
xyOf() {  # centre of the first element whose text contains $1 (prints "x y")
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > ui.xml
  python3 - "$1" <<'PY'
import re,sys
t=open('ui.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', t):
    n=m.group(0); tx=re.search(r'text="([^"]*)"',n); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if tx and b and sys.argv[1].lower() in tx.group(1).lower():
        x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
}
tapExact() {  # tap the first element whose whole text is exactly $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > ui.xml
  local xy
  xy=$(python3 - "$1" <<'PY'
import re,sys,html
t=open('ui.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', t):
    n=m.group(0); tx=re.search(r'text="([^"]*)"',n); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if tx and b and html.unescape(tx.group(1)).strip() == sys.argv[1]:
        x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  echo "tap exact '$1' at: $xy"; [ -n "$xy" ] && adb shell input tap $xy
}
pickFirst() {  # photo picker: tap the first item, then Add / Done if the picker asks
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > out/picker.xml
  XY=$(python3 - "$1" <<'PY'
import re,sys
t=open('out/picker.xml',encoding='utf-8',errors='ignore').read()
k=0
for m in re.finditer(r'<node [^>]*>', t):
    n=m.group(0); d=re.search(r'content-desc="([^"]*)"',n); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if d and b and re.search(r'photo|video|taken|IMG_|VID_', d.group(1), re.I):
        if k == int(sys.argv[1]):
            x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
        k+=1
PY
)
  echo "picker item at: $XY"; [ -n "$XY" ] && adb shell input tap $XY; sleep 2
  seeText '"Add' && tapText "Add"; seeText '"Done' && tapText "Done"; true
}
dismiss() { for k in 1 2 3; do adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; if adb shell cat /sdcard/ui.xml | grep -q "isn't responding"; then tapText "Wait"; sleep 3; else break; fi; done; }
seeText() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -qi "$1"; }
scrollTo() { for k in 1 2 3 4 5 6 7 8 9 10; do seeText "$1" && return 0; adb shell input swipe 540 2150 540 1450 400; sleep 1; done; return 1; }
tapBetween() {  # tap halfway between two texts (e.g. the picture between a dialog's title and its status line)
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > ui.xml
  local xy
  xy=$(python3 - "$1" "$2" <<'PY'
import re,sys
t=open('ui.xml',encoding='utf-8',errors='ignore').read()
def find(s):
    for m in re.finditer(r'<node [^>]*>', t):
        n=m.group(0); tx=re.search(r'text="([^"]*)"',n); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
        if tx and b and s.lower() in tx.group(1).lower(): return list(map(int,b.groups()))
a=find(sys.argv[1]); b=find(sys.argv[2])
if a and b: print(540, (a[3]+b[1])//2)
PY
)
  echo "tap between at: $xy"; [ -n "$xy" ] && adb shell input tap $xy
}
start_video() { adb shell am force-stop com.dronedirector.veyrocutpro; adb shell am start -W -n com.dronedirector.veyrocutpro/.MainActivity --es screen video --ez bgsetup true; sleep 12; dismiss; }
colorTabs() {  # the colour tabs scroll sideways: swipe the row that holds "Input" until "Detail" shows
  ROWY=$(adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | python3 -c "
import re,sys
t=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>', t):
    n=m.group(0)
    if 'text=\"Input\"' in n:
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); print((int(b.group(2))+int(b.group(4)))//2); break
")
  for k in 1 2 3; do seeText '"Detail"' && break; [ -n "$ROWY" ] && adb shell input swipe 1000 $ROWY 150 $ROWY 300; sleep 1; done
}
if want setup; then
# First launch: the download screen, then wait for the whole first-time download.
adb shell am force-stop com.dronedirector.veyrocutpro; adb shell am start -W -n com.dronedirector.veyrocutpro/.MainActivity; sleep 8; dismiss; shot 00a-setup-start
sleep 20; shot 00b-setup-progress
for i in $(seq 1 120); do sleep 10; seeText "All set" && break; seeText "Check the internet" && break; seeText "mobile data" && break; done; shot 00c-setup-end
adb shell run-as com.dronedirector.veyrocutpro ls -la files/models files/sound_library/soundtracks files/whisper > out/setup_files.txt 2>&1 || true
sleep 3; shot 00d-home
fi
start_video; shot 01-video-start
start_video
tapText "MP4"; sleep 30; dismiss; shot 03-clip-workspace
if want mediastrip; then
# Video media strip: filters, pick + append, rating, mouse hover / right-click / drag to the timeline, timeline wheel.
tapExact "Media strip"; sleep 2
seeText "MEDIA 1" && echo "media strip shown" > out/mediastrip.txt; shot 13a-media-strip
tapExact "+"; sleep 2; tapExact "+"; sleep 2; shot 13a2-zoomed; tapExact "−"; sleep 1
tapExact "Unused"; sleep 1; tapExact "Used"; sleep 1; tapExact "All"; sleep 1
XY=$(xyOf "VID_TEST"); echo "strip clip at: $XY" >> out/mediastrip.txt
if [ -n "$XY" ]; then
  set -- $XY; X=$1; Y=$2
  adb shell input tap $X $Y; sleep 2; shot 13b-picked
  tapExact "★4"; sleep 1; tapExact "Append"; sleep 4; shot 13c-appended
  adb shell input mouse tap $X $Y; sleep 1
  adb shell input mouse swipe $((X-60)) $Y $((X+60)) $Y 900; sleep 2; shot 13d-mouse-skim
  adb shell input mouse swipe $X $Y 700 $((Y+400)) 1200; sleep 4; shot 13e-mouse-drop
  adb shell input mouse scroll 540 $((Y+300)) --axis VSCROLL,2 || true; sleep 2
  adb shell input mouse scroll 540 $((Y+300)) --axis HSCROLL,2 || true; sleep 2; shot 13f-wheel
fi
tapExact "SELECT"; sleep 1; tapExact "All"; sleep 1; tapExact "No rejects"; sleep 1; tapExact "DONE"; sleep 1; shot 13g-select
fi
if want layers; then
tapText "+ VIDEO"; sleep 3; shot 04b-video-layer-dialog
tapText "ABOVE the main video"; sleep 6; shot 04c-picker
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > out/picker.xml
# Tap the first media item in the photo picker (content-desc mentions Video / Photo).
XY=$(python3 - <<'PY'
import re
t=open('out/picker.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', t):
    n=m.group(0)
    d=re.search(r'content-desc="([^"]*)"',n); b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if d and b and re.search(r'video|taken|VID_', d.group(1), re.I):
        x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
echo "picker item at: $XY"; [ -n "$XY" ] && adb shell input tap $XY
sleep 8; shot 04d-after-add-above
tapText "Layers"; sleep 3; shot 04e-layers-page
fi
if want transcribe; then
# Text-based editing: download the speech model and transcribe the clip.
tapText "Text"; sleep 3; shot 04f-text-page
tapText "DOWNLOAD MODEL"; for i in $(seq 1 24); do sleep 5; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -q "Model ready" && break; done
shot 04g-model
tapText "TRANSCRIBE"; for i in $(seq 1 100); do sleep 5; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -q "APPLY CUTS\|TAP WORDS\|No speech" && break; done
sleep 2; shot 04h-transcript
fi
if want morph; then
# Morph cut: split the clip (keyboard: L = +5 s, S = split), then pick Morph cut on the cut.
adb shell input keyevent KEYCODE_MOVE_HOME; sleep 1; adb shell input keyevent KEYCODE_L; sleep 2; adb shell input keyevent KEYCODE_S; sleep 3; shot 08a-split
tapText "Transitions"; sleep 3; scrollTo "Morph cut"; tapText "Morph cut"; sleep 20; shot 08b-morph-cut
adb shell input keyevent KEYCODE_MOVE_HOME; sleep 1; adb shell input keyevent KEYCODE_L; sleep 1; adb shell input keyevent KEYCODE_J; adb shell input keyevent KEYCODE_SPACE; sleep 6; shot 08c-morph-playing; adb shell input keyevent KEYCODE_SPACE
fi
if want magic; then
# Magic Mask: Color → Detail → BACKGROUND → Blur → Magic Mask, tap the middle of the picture, track.
tapText "Color"; sleep 3; colorTabs
tapText "Detail"; sleep 2; scrollTo "BACKGROUND"; tapText "Blur"; sleep 2; shot 09a-background
scrollTo "Magic Mask"; tapText "Magic Mask"; for i in $(seq 1 30); do sleep 4; seeText "Tap the object" && break; seeText "couldn" && break; done; shot 09b-magic-dialog
tapBetween "MAGIC MASK" "Tap the object"; sleep 12; shot 09c-magic-picked
tapText "TRACK THROUGH"; for i in $(seq 1 60); do sleep 6; seeText "Tracked through" && break; seeText "couldn" && break; done; shot 09d-magic-tracked
fi
if want slowmo; then
# Optical-flow slow motion on the selected clip (Edit → ½ speed), then play it.
tapText "Edit"; sleep 3; scrollTo "½ speed"; tapText "½ speed"; for i in $(seq 1 80); do sleep 6; seeText "Smooth slow motion" && break; seeText "process this clip" && break; done; shot 10a-smooth-slowmo
adb shell input keyevent KEYCODE_SPACE; sleep 5; shot 10b-slowmo-playing; adb shell input keyevent KEYCODE_SPACE
fi
if want highlights; then
# Auto highlights (Edit → Reel 15 s).
tapText "Edit"; sleep 3
scrollTo "Reel 15"; tapText "Reel 15"; for i in $(seq 1 60); do sleep 5; seeText "Highlight reel" && break; seeText "find highlights" && break; done; shot 10c-highlights
fi
if want planar; then
# Shape layer, corner pin and planar tracking.
tapText "Layers"; sleep 3; scrollTo "ADD LAYER"; tapText "Shape"; sleep 3; shot 11a-shape
scrollTo "PIN TO FOUR CORNERS"; tapText "PIN TO FOUR CORNERS"; sleep 3; shot 11b-pinned
scrollTo "PLANAR TRACK FROM"; tapText "PLANAR TRACK FROM"; for i in $(seq 1 60); do sleep 5; seeText "Planar tracked" && break; seeText "follow that surface" && break; seeText "Tracked to" && break; done; shot 11c-planar
adb shell input keyevent KEYCODE_SPACE; sleep 4; shot 11d-pin-playing; adb shell input keyevent KEYCODE_SPACE
fi
if want tts; then
# AI voiceover (downloads the voice model once, then speaks a line).
tapText "Audio"; sleep 3; scrollTo "What should the voice say"; tapText "What should the voice say"; sleep 1
adb shell input text "Hello%sfrom%sVeyroCut%sPro."; adb shell input keyevent KEYCODE_BACK; sleep 1
scrollTo "MAKE VOICEOVER"; tapText "MAKE VOICEOVER"; for i in $(seq 1 90); do sleep 6; seeText "AI voiceover added" && break; seeText "make the voice" && break; seeText "Download failed" && break; done; shot 12a-tts
fi
if want depth; then
# AI depth (Color → Detail → AI DEPTH → Lens blur → ANALYSE DEPTH).
tapText "Color"; sleep 3; colorTabs
tapText "Detail"; sleep 2; scrollTo "Lens blur"; tapText "Lens blur"; sleep 2; scrollTo "ANALYSE DEPTH"; tapText "ANALYSE DEPTH"
for i in $(seq 1 120); do sleep 6; seeText "Depth worked out" && break; seeText "work out the depth" && break; seeText "Download failed" && break; done; shot 12b-depth
scrollTo "Show depth map"; tapText "Show depth map"; sleep 3; shot 12c-depth-map
fi
if want erase; then
# Object removal (Color → Detail → REMOVE AN OBJECT): pick the middle, let it run up to ~15 min.
seeText "SELECT THE OBJECT TO REMOVE" || { tapText "Color"; sleep 3; colorTabs; tapText "Detail"; sleep 2; }
scrollTo "SELECT THE OBJECT TO REMOVE"; tapText "SELECT THE OBJECT TO REMOVE"; for i in $(seq 1 30); do sleep 4; seeText "Tap the object" && break; seeText "couldn" && break; done
tapBetween "MAGIC MASK" "Tap the object"; sleep 12; tapText "TRACK THROUGH"
for i in $(seq 1 150); do sleep 6; seeText "Object removed" && break; seeText "remove the object" && break; seeText "follow the object" && break; seeText "Download failed" && break; done; shot 12d-erase
fi
adb logcat -d | grep -E "FlowRetime|Lottie|PlanarTracker|CornerPin|PenMask|ObjectRemoval|Depth|Voiceover|onnxruntime|libdf" | tail -60 > out/new.txt || true
adb logcat -d | grep -E "FrameReader|onnxruntime|OrtSession|Magic|TransitionPreview|veyrocut_morph|AndroidRuntime" | tail -60 > out/ai.txt || true
adb logcat -d | grep -i -E "veyrocut_native|veyrocut_whisper|UnsatisfiedLink|whisper" | tail -20 > out/native.txt || true
if want recover; then
start_video; shot 05-recover-banner
tapText "RECOVER"; sleep 25; shot 06-after-recover
fi
if want filmstrip; then
# Photo filmstrip: open a recent photo, add a second from the picker, sync, rate, export, mouse wheel switch.
adb shell am force-stop com.dronedirector.veyrocutpro
adb shell am start -W -n com.dronedirector.veyrocutpro/.MainActivity --es screen photo --ez bgsetup true; sleep 10; dismiss; shot 14a-photo-start
tapText "JPEG"; sleep 10; dismiss; shot 14b-photo-open
seeText "1 / 1" && echo "filmstrip shown" > out/filmstrip.txt
XY=$(xyOf "1 / 1"); echo "counter at: $XY" >> out/filmstrip.txt
tapExact "+"; sleep 6; shot 14c-picker; pickFirst 1; sleep 8; shot 14d-two-photos
seeText "/ 2" && echo "two photos" >> out/filmstrip.txt
tapExact "Light"; sleep 2; shot 14e-light
adb shell input mouse scroll 540 700 --axis VSCROLL,1 || true; sleep 6; shot 14f-wheel-next
adb shell input mouse scroll 540 700 --axis VSCROLL,-1 || true; sleep 6
adb shell input mouse tap 540 700; sleep 1; adb shell input mouse tap 540 700; sleep 3; shot 14g-double-click
adb shell input mouse tap 540 700; sleep 1; adb shell input mouse tap 540 700; sleep 2
tapExact "SELECT"; sleep 1; tapExact "Select all"; sleep 1; tapExact "★★★"; sleep 1; tapExact "Sync edit"; sleep 10; shot 14h-synced
tapExact "Export"; for i in $(seq 1 30); do sleep 4; seeText "Exported" && break; done; shot 14i-exported
tapExact "DONE"; sleep 1; tapExact "Picks"; sleep 1; tapExact "All"; sleep 1
# Remove a photo from the strip (hold its thumbnail → Remove from strip); this used to crash.
XY=$(xyOf "/ 2"); if [ -n "$XY" ]; then set -- $XY; adb shell input swipe $(( $1 + 40 )) $(( $2 + 110 )) $(( $1 + 40 )) $(( $2 + 110 )) 900; sleep 2; shot 14i2-menu; tapText "Remove from strip"; sleep 3; shot 14i3-removed; fi
adb shell input keyevent KEYCODE_BACK; sleep 4; shot 14j-start-continue
seeText "Continue filmstrip" && echo "continue banner" >> out/filmstrip.txt
tapText "Continue filmstrip"; sleep 8; shot 14k-continued
fi
if want develop; then
# Pro develop tools on a photo: presets + amount, auto, clipping, eyedropper, point colour, sharpening mask, zoom.
adb shell am force-stop com.dronedirector.veyrocutpro
adb shell am start -W -n com.dronedirector.veyrocutpro/.MainActivity --es screen photo --ez bgsetup true; sleep 10; dismiss
seeText "Continue filmstrip" && tapText "Continue filmstrip" || tapText "JPEG"; sleep 10; dismiss; shot 15a-open
tapExact "Presets"; sleep 3; scrollTo "PRESETS ("; tapExact "Film"; sleep 4; shot 15b-presets; tapExact "Portra"; sleep 4; scrollTo "Amount"; shot 15c-preset-applied
tapExact "KEEP"; sleep 1
tapExact "Light"; sleep 2; tapExact "AUTO"; sleep 3; tapText "CLIPPING"; sleep 4; shot 15d-auto-clip; tapText "CLIPPING"; sleep 1
tapExact "Color"; sleep 2; tapExact "EYEDROPPER"; sleep 1; adb shell input tap 540 600; sleep 4; shot 15e-wb-pick
scrollTo "PICK A COLOUR"; tapText "PICK A COLOUR"; sleep 1; adb shell input tap 400 500; sleep 4; scrollTo "Hue shift"; shot 15f-point-color
scrollTo "CALIBRATION"; shot 15g-calibration
adb shell input tap 540 600; sleep 0.1; adb shell input tap 540 600; sleep 6; shot 15h-zoom; adb shell input tap 540 600; sleep 0.1; adb shell input tap 540 600; sleep 2
tapText "Detail"; sleep 2; scrollTo "SHOW MASK"; tapText "SHOW MASK"; sleep 4; shot 15i-sharpen-mask; tapText "SHOW MASK"; sleep 1
fi
if want develop2; then
tabTo() {  # photo editor tabs scroll sideways: swipe the tab row until "$1" shows, then tap it
  for k in 1 2 3 4 5 6; do
    seeText "text=\"$1\"" && break
    Y=$(adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | python3 -c "
import re,sys
t=sys.stdin.read()
for lab in ('Presets','Light','Curve','Color','HSL Mixer','Grading','Detail','Effects','Masks','Heal','Lens'):
    m=re.search(r'text=\"'+lab+r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',t)
    if m: print((int(m.group(2))+int(m.group(4)))//2); break
")
    [ -n "$Y" ] && adb shell input swipe 900 $Y 200 $Y 300; sleep 1
  done
  tapExact "$1"; sleep 2
}
# Masks (object, depth, combine), Lens Blur, Upright, dust spots, export presets / TIFF / colour space.
adb shell am force-stop com.dronedirector.veyrocutpro
adb shell am start -W -n com.dronedirector.veyrocutpro/.MainActivity --es screen photo --ez bgsetup true; sleep 10; dismiss
seeText "Continue filmstrip" && tapText "Continue filmstrip" || tapText "JPEG"; sleep 10; dismiss; shot 16a-open
tabTo "Masks"; tapText "Brightness range"; sleep 3; scrollTo "COMBINE"; tapExact "− Subtract"; sleep 1; tapExact "Colour range"; sleep 3; shot 16b-combine
scrollTo "ADD A MASK" || true; tapText "Depth (AI)"; for i in $(seq 1 60); do sleep 5; seeText "Depth range" && break; seeText "work out the depth" && break; done; shot 16c-depth-mask
tapText "Object (AI)"; sleep 2; adb shell input tap 540 600; for i in $(seq 1 40); do sleep 4; seeText "Object selected" && break; seeText "select that" && break; done; shot 16d-object
tabTo "Effects"; scrollTo "LENS BLUR"; tapText "ANALYSE DEPTH"; for i in $(seq 1 60); do sleep 5; seeText "Blur amount" && break; done; scrollTo "PICK FOCUS"; tapText "PICK FOCUS"; sleep 1; adb shell input tap 540 600; sleep 12; scrollTo "Bokeh"; tapExact "Hexagon"; sleep 12; shot 16e-lens-blur
tabTo "Crop & perspective"; scrollTo "UPRIGHT"; tapExact "Auto"; sleep 8; shot 16f-upright; tapExact "Full"; sleep 8
tabTo "Heal"; scrollTo "DUST SPOTS"; tapText "VISUALISE SPOTS"; sleep 4; shot 16g-visualise; tapText "VISUALISE SPOTS"; tapText "FIND DUST"; sleep 8; shot 16h-dust
tapExact "EXPORT"; sleep 2; scrollTo "EXPORT PRESETS"; tapText "Print (TIFF"; sleep 2; scrollTo "OUTPUT"; tapText "Soft proof" ; sleep 1; shot 16i-export-opts
scrollTo "EXPORT PHOTO"; tapText "EXPORT PHOTO"; for i in $(seq 1 60); do sleep 4; seeText "EXPORTING" || break; done; sleep 3; shot 16j-exported
adb shell ls -la /sdcard/DCIM/VeyroCutPro/ > out/exports.txt 2>&1 || true
fi
if want photo; then
adb shell am force-stop com.dronedirector.veyrocutpro
adb shell am start -W -n com.dronedirector.veyrocutpro/.MainActivity --es screen photo; sleep 10; shot 07-photo
fi
adb logcat -d -b crash > out/crash.txt || true
adb logcat -d > out/logcat-full.txt || true
grep -n -A40 "FATAL EXCEPTION" out/logcat-full.txt > out/fatal.txt || echo "no fatal exception" > out/fatal.txt
cat out/fatal.txt | head -80
