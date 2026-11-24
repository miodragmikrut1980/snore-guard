# SnoreGuard — Android + Huawei Watch GT 2

**Current version: 0.5.0 (build 50).** Android 8+; Kotlin; local offline YAMNet.

## New in v0.5.0

- Hour-by-hour recognition chart (22:00 to 07:59) for **new** sessions. Every distinct event is counted once, not every overlapping 250 ms YAMNet frame. No audio is stored.
- Completed-night comparison chart for up to seven recent nights using the consistent `breaths_v1` event definition. Differences in sensitivity and session duration still make raw counts imperfect comparisons.
- Local CSV export via Android's Storage Access Framework. Estimated snoring seconds are model-window approximations, NOT measured physiological duration. Older sessions have blank hourly columns; no timestamps are invented.
- JSON backup and restore of up to 30 completed sessions plus saved sensitivity/alert/phone-vibration settings via Android's file picker. Restore validates format before merging, preserves local duplicates and sorts newest first.
- The active, unfinished session is intentionally excluded from exported backups; stop monitoring first for a complete backup.
- No INTERNET permission, server, cloud account or automatic data upload. Keep exported JSON private and secure.
- APK is still built with the GitHub Actions debug signature, which may change from run to run. Back up old history **before** uninstalling an incompatible old debug build.

## New in v0.4.0

- Three persistent sensitivity profiles: Low (0.16), Normal (0.10), High (0.07). Existing installs default to High.
- Three alert modes: Gentle (1), Normal (2), Intense (3), spaced 16 seconds apart; existing installs default to Intense.
- Manual pre-sleep readiness check: mic permission, notification permission/channel, battery optimization, night window. Watch vibration remains a manual check.
- Per-session detection threshold, alert mode, analysis heartbeat and termination reason. Stale inference is flagged after 30 seconds.
- All audio processing stays offline without storing or uploading raw audio.
- Changes made while monitoring take effect the following night, not mid-session.
- Sensitivity thresholds are heuristic and not clinically calibrated.

## New in v0.3.4

- Install the chosen sleeping-face / green-shield logo as Android's launcher icon (light variant).
- Show the same logo in the application's home screen header.
- Display the installed APK version as `v0.3.4 · build 34`.
- Keep detection, statistics, night interval and the 0/16/32-second watch vibration schedule unchanged.

## New in v0.3.3

The previous detector required 3 positive classifications in 5 roughly one-second recordings. It could count snoring episodes without notifying the watch. Snoring frequently occurs as a short inhale, so the new detector tracks **two distinct short snore events within 15 seconds**.

- The YAMNet input remains a 975 ms, 15,600-sample audio window at 16 kHz, but now slides every 250 ms (4,000 new samples).
- Overlapping positive windows of the same inhalation count as **one** event. A fresh event requires at least four intervening 250 ms negative hops (at least 1,250 ms between positive classification times).
- A second separate event within 15 seconds triggers an alert burst, subject to a 60-second cooldown between burst starts.
- Watch notifications remain at **0, 16 and 32 seconds**, which all vibrated in a previous daytime GT 2 test.
- Short exhale gaps do not cancel the burst. Remaining notifications stop if **no positive snore classification occurs for more than 20 seconds**, or recording stops/the night window ends.

This is an **experimental heuristic**. It can still miss snores or respond to other sounds. Watch notification forwarding in actual sleep needs verification.

## Usage and notifications

1. Pair GT 2 with Huawei Health, enable SnoreGuard notifications.
2. Start monitoring manually in SnoreGuard during **22:00–08:00** (foreground microphone service).
3. The app analyzes local mono PCM audio; there is no network permission or stored audio recording.
4. Open the app in the morning to check **recognized individual snores**, **alarm series**, and **emitted notifications**. These are distinct measures.
5. Test the 3-notification burst in daytime with the Test button; the GT 2 may still suppress notifications during Sleep/DND.

The version shown in the app's top header comes directly from the installed APK package.

**Statistics compatibility:** New sessions store a 250 ms hop size and detection mode. Legacy v0.3.2 sessions keep their old ~975 ms estimate and old episode definition. Estimated duration is based on positive AI windows, not measured seconds of physiological snoring; estimates across versions are not directly comparable.

## Build / install

The source code is in this repository. Open the latest successful GitHub Actions run and download the debug APK from **Artifacts**, then extract and install it. Or use Android Studio with Android SDK 35 and JDK 17 and run:

    ./gradlew testDebugUnitTest assembleDebug

The first build downloads the Google YAMNet TFLite model. Inference on the phone is offline.

**Debug-signing warning:** Different GitHub Actions runs can generate different debug signing keys. Android may refuse to install a new debug APK over the old one. **Do not uninstall your previous app without saving statistics you want**, as uninstalling can delete local data.

See INSTALACIJA.txt and docs/TEST_PLAN.md for details. Automatic night-only Viber/WhatsApp notification filtering is **not implemented**. Huawei GT 2 is not Wear OS, so SnoreGuard cannot directly control its vibration motor.

## References

- YAMNet model: https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier
- Android AudioRecord: https://developer.android.com/reference/android/media/AudioRecord
- Foreground microphone: https://developer.android.com/about/versions/14/changes/fgs-types-required

Not a medical device, apnea detector, or treatment.
