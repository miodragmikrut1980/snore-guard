# Changelog

## v0.3.1 — Visible installed version
- Show `v<versionName> · build <versionCode>` in the home screen header.
- Read version from Android PackageManager for the installed APK, without hard-coded version strings.
- No changes to snore detection, 3× alarm schedule, night window, or saved sessions.
- Android build metadata bumped to versionName 0.3.1, versionCode 31.

## v0.3.0 — Burst alert (Huawei GT 2)
- 3 distinct notifications: immediately, +8 s, +16 s.
- No overlapping bursts; cooldown between burst starts remains 60 s.
- Cancel pending steps when snoring ends or monitoring stops.
- Manual **Test jačeg alarma ×3** works in daytime without the microphone.
- Count emitted notification attempts separately from detected alarm series.
- Added alarm scheduling tests and updated Serbian setup/test documentation.

Known limitations: Huawei Health may coalesce/ignore notifications, especially during sleep. Real phone/watch validation and full Android build remain pending. No automatic Viber/WhatsApp night filtering is included.

## v0.1.2
- Fixed XML Button `android:textAllCaps` attribute.

## v0.1.1
- Fixed Gradle Kotlin DSL `File` and `URL` imports.

## v0.1.0
- Initial microphone/YAMNet MVP, night mode, notification bridge to GT 2, session history.
