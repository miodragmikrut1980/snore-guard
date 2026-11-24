# SnoreGuard — Android + Huawei Watch GT 2

**Current version: 0.7.1 (build 71).** Android 8+; Kotlin; local offline YAMNet.

## New in v0.7.1 — Complete Backup & Safe Updates

- **Export kompletne kopije (ZIP)** now backs up all completed statistics (up to 365 sessions), user settings, every available private WAV clip (up to 40), and each saved clip's subjective review label and personal note.
- **Import kompletne kopije (ZIP)** securely checks version, allowed ZIP paths, each item's byte length, WAV PCM header, ZIP CRC and SHA-256 hashes. Extraction is bounded to 25 MB and runs off the UI thread. Existing WAVs are NOT overwritten. New clips are imported only into free slots up to 40; skipped items are explicitly counted in the result.
- Files are stored only on the device and selected backup location; **no cloud upload**. The ZIP itself is **not encrypted** and contains potentially sensitive room conversations. Keep backups private and only share with consent.
- Full backup is separate from the existing JSON export/import (statistics and preferences only) and CSV export (Excel only). Audio and review metadata are intentionally excluded from the legacy JSON export.
- Stop monitoring before importing/exporting complete backups, and **verify that the ZIP exists outside the app** before uninstalling an incompatible debug build. Partial imports are recoverable by retrying the same validated ZIP.
- A **manual signed-release workflow** is prepared. It signs production APKs using an external private keystore stored in GitHub Actions secrets and verifies the expected SHA-256 certificate. **A stable release signing key is NOT yet configured**; current GitHub Actions APKs remain debug signed, and one migration from debug to release requires uninstall after a verified backup. See [Release signing guide](docs/RELEASE_SIGNING.md).
- The microphone AI, alarms, custom hours and 90-second test remain unchanged.

## New in v0.7.0 — review feedback and analysis reliability

- **User review of saved snoring WAVs:** tap any recording, choose **Označi: hrkanje / lažni alarm**, then select *Hrkanje*, *Lažni alarm*, *Nisam siguran*, or *Nije ocenjen*. These are user-supplied labels, not medical judgments.
- **Private notes:** add a note of up to 160 characters (e.g. sleep position or possible TV noise) to any WAV. Labels and notes remain in local app storage alongside that clip, including when additional clips are saved.
- **Export review metadata CSV:** separate button within the **ZVUČNI SNIMCI ZA LEKARA** section exports filenames, local recording timestamps, audio duration, user labels and notes. WAV audio is **not included**; share any desired WAV explicitly using Android's chooser. This CSV cannot be reimported. Since WAV and annotations are private app files, export the CSV and WAVs individually before uninstalling an APK.
- **Inference heartbeat diagnostics:** a monotonic timer records the largest gap between completed AI inference windows and how many gaps exceeded five seconds. These appear under current-session diagnostics and in statistics CSV for new sessions.
- **Limits:** Long inference gaps are merely a troubleshooting signal; they do **not** prove the microphone stopped, a snore was missed or the Huawei watch failed to vibrate. Neither user feedback nor notes retrain or automatically recalibrate YAMNet. There is no clinical validation for apnea or sleep-disordered breathing.
- The existing adjustable schedule, 90-second stop/start test, optional audio clips, and JSON history export/import stay unchanged. Raw audio is not uploaded to any server.

## New in v0.6.2

- The 90-second **Testiraj prepoznavanje hrkanja** button now becomes **Zaustavi test hrkanja** while the detection test is running, without scrolling to the top of the screen.
- Tapping that button immediately sends the existing safe monitoring-stop action to the microphone service; a short **Zaustavljam test…** state prevents repeat requests while the service closes.
- Once monitoring ends, the original test label and enabled state return. While normal night monitoring runs, the button remains disabled and does not stop the main session accidentally.
- No changes to detection thresholds, actual snore WAV saving, data export/import or Huawei GT 2 alert intervals.

## New in v0.6.1

### Export and import statistics — easier to find and safer for APK reinstalls

The statistics transfer area now appears **immediately below the current session's diagnostics**, rather than at the very bottom. Three explicit actions are available:

1. **Export statistike (JSON za čuvanje)**: save all completed session history (up to 365 nights) plus sensitivity, alert mode, phone-vibration option and monitoring start/end times to a local `.json` file. Stop monitoring first so the current night is included.
2. **Import statistike (JSON)**: select a SnoreGuard JSON export from Android's file picker, then explicitly confirm. Sessions are merged by start/end timestamps, existing duplicates are preserved, and only the newest 365 are kept. Saved settings are restored. Malformed/oversized files are rejected.
3. **Export statistike (CSV za Excel)**: report for spreadsheet analysis only. **CSV is not importable**; use JSON for transferring statistics to a new installation.

The export does **not** include private WAV audio excerpts, active monitoring, or microphone recordings. Existing older v0.5.0/v0.6.0 JSON backups remain importable. Save the JSON outside the app before uninstalling a debug-signed APK. No network upload is performed.

## New in v0.6.0

### Optional snore audio for discussion with a doctor

- A **separate, OFF-by-default switch** enables saving short snippets of actual sound. An explicit confirmation warns that sleeping partners' voices may also be captured; obtain their consent.
- Uses the existing local microphone stream, with **no second microphone session** and no audio sent to a server.
- When AI recognizes **two separate snoring events within 15 seconds**, it saves a WAV excerpt with approximately 3 seconds of pre-roll and 9 seconds after the alert; actual duration can be shorter when stopping early.
- Saves **up to 10 excerpts per monitoring session**, at least 20 minutes between excerpt starts, and retains **at most 40 excerpts on the device** (oldest are pruned after the limit).
- Local **16 kHz mono 16-bit PCM WAV** files are stored in a private app folder. Tap a saved excerpt to play it, share it using Android's share chooser (e.g. with a doctor), or delete it. Delete-all requires explicit confirmation. Audio snippets are **not** included in the JSON statistics backup.
- Short excerpts are **not an apnea diagnosis or substitute for a sleep study**. Detection can miss snoring or capture unrelated sounds, and the snippets may contain other private room audio.

### Configurable monitoring interval, plus anytime test

- Choose **start and end times** from Android time pickers; defaults are 22:00–08:00. Windows may cross midnight or be during the day. Equal start/end means 24 hours.
- Tap **Start monitoring** while the app is visible and within that window. Monitoring **stops automatically** when the window ends, or if the user stops it. Editing start/end during an active session takes effect on the next start.
- **Automatic background microphone start at the configured time is not supported**, because Android 14+ restricts starting microphone foreground services from the background. Schedule settings set the allowed window and automatic stop, not an automatic start.
- Tap **Testiraj prepoznavanje hrkanja (90 s)** **at any time**, regardless of window. It uses the same microphone and AI detection algorithm and shows live result counters, including alerts. The test stops automatically after 90 seconds. It does not save WAV excerpts or add a completed night to history.
- Original simple **single notification test** and **GT 2 burst test** remain available. When Android notifications are disabled, the live detector test can still run, but watch alerts may not be shown.
- Saved hourly charts can now show detected snore events in a **custom time window**, and CSV has hourly columns for all 24 hours.
- The JSON backup also preserves configured start and end times, but **not the recording switch**, which intentionally resets to off on a fresh install.

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
2. Start monitoring **manually** in SnoreGuard during your configured window (default **22:00–08:00**) using a foreground microphone service. The test can start at any time.
3. The app analyzes local mono PCM audio; it has no network permission. Audio is never stored unless you explicitly enable short WAV excerpt saving.
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
