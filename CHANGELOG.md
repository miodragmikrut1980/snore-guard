# Changelog

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
