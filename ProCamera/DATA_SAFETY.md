# AcluxPro — Google Play "Data safety" answers

Use this to fill in **Play Console → App content → Data safety**. It matches
[PRIVACY.md](PRIVACY.md) and the app's code as of version 1.0.15x. Re-check it whenever a
feature that sends data off the phone is added. (Not legal advice — Google's own help pages
decide how each question is meant.)

Privacy policy URL to enter in Play Console:
`https://github.com/DroneDirector/Android-Projects/blob/main/ProCamera/PRIVACY.md`

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** — only because the optional Cloud AI features upload media the user chooses (see below). |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS to fal.ai; RTMPS / SRT where the streaming service supports it). |
| Do you provide a way for users to request that their data is deleted? | **Yes** — nothing is stored by the developer; on-phone data is deleted by clearing app storage / uninstalling, and uploaded files are deleted through the user's own fal.ai account (explained in the privacy policy). |

## Data types

Declare these as **Collected**, **Optional** (user can choose / feature is off by default),
**Not shared**, **Not processed ephemerally**:

| Data type (Play category) | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|
| Photos and videos → **Photos** | Yes | No | Yes | App functionality |
| Photos and videos → **Videos** | Yes | No | Yes | App functionality |
| Audio → **Voice or sound recordings** | Yes | No | Yes | App functionality |
| Audio → **Other audio files** (music in AI captions "Everything") | Yes | No | Yes | App functionality |

Why "not shared": the upload goes from the phone to fal.ai, the cloud service the **user**
chose, under the **user's own fal.ai account and API key**, as a direct result of the user
tapping a Cloud AI feature. Google counts user-initiated transfers like this, and transfers to a
service provider acting on the user's behalf, as not "sharing". If Google's reviewers disagree,
change "Shared" to **Yes → recipient: fal.ai (service provider), purpose: App functionality**.

## Do NOT declare (not collected)

- **Location** — the app has no location permission (GPS already inside a photo's EXIF stays on
  the phone; it is only uploaded inside the photo itself when the user runs a Cloud AI task, and
  "Remove location" strips it from exports).
- **Personal info, financial info, health, messages, contacts, calendar, app activity, web
  browsing, device or other IDs, crash logs, diagnostics** — none collected.
- **Live streaming**: the picture and sound go directly to the user's chosen streaming service at
  the user's request (a user-initiated transfer, not collection by the developer).
- **fal.ai API key / stream keys**: stored encrypted on the device only; sent only to fal.ai / the
  chosen streaming server for authentication.

## Security practices

- Data is encrypted in transit: **Yes**.
- Independent security review: **No** (optional section).
- Committed to Google Play Families policy: **No** (the app isn't designed for children).

## Permissions shown on the store page (for reference)

Camera, Microphone (record audio), Photos / videos (read media), Internet,
storage write on Android 9 and older.
