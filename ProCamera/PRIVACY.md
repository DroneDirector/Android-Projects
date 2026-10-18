# VeyroCut Pro — Privacy Policy

*Last updated: 16 October 2026*

VeyroCut Pro ("the app") is a camera, photo editor and video editor for Android, published by
DroneDirector. This policy explains what the app does with your information. In short: **your
photos, videos and sound stay on your phone, unless you choose to send them somewhere** (Cloud AI
or live streaming). We have no servers, no user accounts, no ads and no analytics.

**Contact:** [simon@dronedirector.fo](mailto:simon@dronedirector.fo)

## 1. What the app uses on your phone

| Permission | Why | Where the data goes |
|---|---|---|
| **Camera** | Taking photos and recording video | Saved on your phone (DCIM/VeyroCutPro or the folder / SD card you choose) |
| **Microphone** | Sound for videos, the audio meter, headphone monitoring and voiceovers in the video editor | Saved on your phone with your recordings |
| **Photos and videos (storage)** | Showing your gallery and opening media in the editors | Stays on your phone |
| **Internet** | Only for the optional Cloud AI and live streaming features below | See sections 2 and 3 |

Edits, projects, presets, LUTs, AI selections and settings are stored inside the app on your phone.
Exported files keep the camera details (date, camera, lens, and location if the original had it)
only when you leave "Keep camera info" on; you can remove all of it or only the location.

All on-phone AI features (AI upscale, AI noise reduction, AI subject selection, auto-reframe, the
on-phone AI erase and the privacy **face blur**) run **entirely on your phone**. Nothing is uploaded.
Face blur and portrait mode use Google ML Kit's on-device face detection and selfie segmentation; what they find is not stored or sent
anywhere. Google may collect anonymous ML Kit usage statistics (no images) as described in the
[ML Kit terms](https://developers.google.com/ml-kit/terms).

## 2. Cloud AI (optional, off by default)

Cloud AI only works after **you** add your own [fal.ai](https://fal.ai) API key in the app's
settings and switch it on. When you then use a cloud feature, the app uploads **only the media
needed for that task** directly from your phone to fal.ai, using your fal.ai account:

| Feature | What is uploaded | Model run by fal.ai |
|---|---|---|
| Cloud AI upscale / noise reduction (photos) | The photo | Topaz Labs |
| Cloud AI noise reduction (video) | The used part of the clip | Topaz Labs (Nyx) |
| AI captions | The sound of your edit | Whisper |
| AI slow motion | The used part of the clip | RIFE |
| AI subject / sky selection | The photo (preview size) | BiRefNet / EVF-SAM2 |
| AI erase | The photo and the painted area | Bria Eraser |

- The upload goes **straight to fal.ai** over an encrypted connection (HTTPS). It never passes
  through us, and we cannot see it.
- fal.ai processes and stores these files under **your** fal.ai account and its own terms and
  privacy policy: <https://fal.ai/privacy>. Charges for these tasks are billed by fal.ai to you.
- Your **API key** is stored encrypted on your phone (Android Keystore) and is sent only to fal.ai
  to authorise your requests. You can delete it in the app at any time.
- The "Wi-Fi only" option (on by default) stops uploads over mobile data.

## 3. Live streaming (optional)

When you start a live stream, the camera picture and sound are sent **directly from your phone**
to the streaming service and server you choose (for example YouTube, Twitch, TikTok, Facebook or
your own RTMP / SRT server), using the stream key you enter. That service's privacy policy applies.
Stream keys are stored only on your phone.

## 4. What we do not do

- We do not collect, store or sell personal data, and we have no servers that receive your media.
- No advertising, no analytics or tracking SDKs, no crash-reporting services, no user accounts.
- The app does not request your location.

## 5. Children

The app is not directed at children under 13 and does not knowingly collect their data.

## 6. Deleting your data

Everything the app keeps is on your phone. Uninstalling the app, or clearing its storage in
Android settings, deletes its projects, presets, LUTs, settings and your fal.ai key. Photos and
videos you saved stay in your gallery until you delete them. Files you sent to fal.ai can be
managed or deleted through your fal.ai account.

## 7. Changes

If this policy changes, the new version will be published at this address with a new date, and
mentioned in the app's release notes.
