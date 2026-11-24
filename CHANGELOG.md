# Changelog

## v0.7.0 — Clip review and inference diagnostics
- Subjectively tag saved WAVs as snoring / false alarm / unsure / unreviewed.
- Attach an optional local 160-character note to each saved audio clip; preserve user reviews when more clips are stored.
- Export a separate CSV report with local clip timestamps, reviewed labels, notes, duration and filenames; no WAV audio in CSV.
- Extend per-session reliability metadata with maximum delay between consecutive AI classification completions and count of gaps over 5 seconds.
- Show metrics in the monitoring diagnostics and include them in the regular statistics CSV (blank for older sessions).
- Tests for stored-label compatibility and deterministic monotonic analysis-gap accounting.
- Does not claim self-training, improved clinical detection accuracy, proof of GT 2 vibration or automatic battery optimization.

## v0.6.2 — Start/stop detection test using the same button
- In the 90-second snore detection test, the original button changes to **Zaustavi test hrkanja**.
- Tapping it requests the existing microphone foreground service STOP action and shows **Zaustavljam test…**.
- Once stopped, the button returns to **Testiraj prepoznavanje hrkanja (90 s)**.
- The test button remains unavailable during normal night monitoring to prevent inadvertently stopping that session.
- Preserves all prior recording, stats import/export, watch notification and manual test functionality.

## v0.6.1 — Clearly labeled statistics export/import
- Move the data transfer panel to just below the current monitoring statistics; label JSON export/import and CSV spreadsheet export separately.
- JSON export includes completed sessions and settings. JSON import verifies input, deduplicates, merges and restores preferences on confirmation.
- Increase stored history and backup/import capacity from 30 to 365 completed nights.
- Increase import read cap from 500,000 to 1,000,000 characters; the importer enforces a 2 MB UTF-8 payload cap.
- Count only successfully retained imported sessions in confirmation status.
- Validate all 24 hourly counters in backup records, including custom daytime monitoring schedules.
- Prohibit a JSON export while monitoring is active, to avoid losing the unfinished current night in a backup.
- CSV remains export-only; audio WAV files are not included in JSON.

## v0.6.0 — Optional short WAV recordings, flexible hours and anytime microphone test
- Explicit opt-in for 3s pre-roll + 9s post-roll WAV excerpt recording after two distinct snoring events.
- Private on-device 16 kHz mono PCM storage; at most 10 clips per night, at least 20 min between starts and 40 total.
- Play, share via Android FileProvider and delete individual WAV clips or all clips after confirmation.
- Configure local allowed monitoring start/end via time picker (cross-midnight, daytime and 24-hour windows supported).
- Auto-stop when the allowed monitoring window ends; **do not** silently auto-start background mic services.
- 90-second live snoring detection test at any time, with own in-app status; test recordings do not pollute saved night history.
- Persist custom interval in JSON settings backup and capture events across all 24 possible clock hours.
- Add deterministic custom-schedule, time-label and WAV header tests.
- Preserve all legacy snore detection and GT 2 notification behaviour unless user changes settings.
- Audio clips excluded from JSON backup. Sleep-apnea diagnosis is not provided.

## v0.5.0 — Night timelines, comparisons and local backup
- Persist per-hour recognized distinct snore events for each new session (22:00–08:00).
- Graph one session by hour and compare up to seven completed nights with matching detector semantics.
- Export a CSV with 30-session history, count metrics, model metadata and available hourly counts.
- Export/import versioned JSON of completed sessions and settings via Android system file picker; validate input and merge by start/end timestamps.
- Keep all microphone inference and report creation offline, without storing raw sound.
- Unit tests for midnight hour ordering, CSV escaping and spreadsheet formula safety.
- Preserve historical records, but do not invent hourly data for versions older than 0.5.0.

## v0.4.0 — Configurable AI and night readiness
- Three persistent sensitivity profiles, keeping previous HIGH threshold by default.
- Gentle (1), Normal (2) and Strong (3) separate GT 2 notification series.
- Ready-for-bed checklist and quick links to application and battery settings.
- Frame heartbeat diagnostics, chosen threshold and reason for stopping a session.
- Additional unit tests for profile fallback and configurable alert bursts.
- No change in core offline YAMNet detection or medical claims.

## v0.3.4 — Selected SnoreGuard app logo
- Add approved sleeping pillow/face + green shield branding to Android launcher and app home header.
- Use `mipmap-nodpi/ic_launcher.webp` converted from the selected light-background icon.
- Bump to versionName 0.3.4 / versionCode 34 without altering snoring detection, notification timings or session history.

## v0.3.3 — Two short snores in 15 seconds
- Replace 3-of-5 full-second snore rule with distinct-event detection.
- Analyze 975 ms YAMNet windows every 250 ms, with 4,000 new and 11,600 overlapping PCM samples.
- Group overlapping positives within the same inhale; count a new event after >=1,250 ms between positive windows.
- Trigger after two distinct events within 15 s, subject to 60-second cooldown.
- Keep tested GT 2 alerts at 0/16/32 s; normal exhalation no longer cancels subsequent alerts immediately.
- Cancel pending alerts after >20 s without a positive classification, on STOP, or after night ends.
- Mark new sessions as breaths_v1 with hopMs=250 and preserve old statistics interpretation.
- Build version 0.3.3 (33), shown from installed package info.
- Add deterministic event-detector and sliding-window tests. Real overnight validation remains necessary.

## v0.3.2 — GT 2 alarm interval
- Separate alerts spaced 0/16/32 s; user confirmed three daytime watch vibrations.

## v0.3.1 — Visible installed version
- Show installed APK version in the home header.

## v0.3.0 — Burst alerts
- Three independent notifications, manual daytime test, cooldown, night mode, YAMNet model.

## v0.1.2
- Fix textAllCaps in Android XML.

## v0.1.1
- Fix Gradle Kotlin File and URL imports.

## v0.1.0
- First YAMNet microphone prototype.
