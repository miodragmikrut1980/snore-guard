# Test plan — SnoreGuard v0.5.0 / Huawei GT 2

## Automated build and tests
- [ ] `./gradlew testDebugUnitTest assembleDebug` passes under JDK 17 / SDK 35
- [ ] Existing burst and snore-detection tests remain green
- [ ] 22h,23h,00h,...,07h chart order; out-of-window hours excluded
- [ ] CSV escapes quotes, commas, newlines and spreadsheet formula prefixes

## Xiaomi 14T functional validation
- [ ] Start monitoring between 22:00 and 08:00 and confirm no crash
- [ ] Trigger two distinct positive snore events in 15 seconds
- [ ] Verify per-hour count increments for one distinct event, not for each sliding 250ms frame
- [ ] Run across 23:59 to 00:01, confirm counts appear in correct bars
- [ ] Confirm former v0.4.0 session shows no made-up hourly bars
- [ ] Confirm last seven completed compatible sessions show correct daily comparison
- [ ] CSV export via Android file chooser; open CSV in spreadsheet and verify headers/values
- [ ] JSON backup via file chooser; import back, ensure no duplicate sessions
- [ ] Restore backup into a fresh installation; verify settings and completed history
- [ ] Reject malformed JSON; oversized JSON; invalid session times
- [ ] Attempt restore during active monitoring: must be blocked
- [ ] Check original night mode, sensitivity profiles and GT 2 daytime notification tests
- [ ] Verify screen-off behavior, battery optimization, no false notifications due to TV/talking/fan
- [ ] Validate night monitoring with Huawei Watch GT 2 in Sleep/DND (not established by CI)

## Privacy and data
- [ ] No INTERNET permission or raw audio storage
- [ ] Exported data contains only counters, event-hour bins, timestamps and preferences
- [ ] Android `allowBackup=false` unchanged: automatic OS backup not enabled
- [ ] Debug APK from different CI runs may have incompatible signatures; backup existing stats before uninstall

NOTE: YAMNet detection is heuristic, not clinically validated. Count comparisons do not measure sleep quality, apnea, or treatment effect.
