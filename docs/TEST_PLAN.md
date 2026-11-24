# SnoreGuard v0.7.1 — complete backup / safe update test plan

## GitHub build and static checks
- [ ] ./gradlew testDebugUnitTest assembleDebug succeeds on Android SDK 35 and JDK 17
- [ ] Existing detection, monitor schedule, WAV review, CSV, and notification tests remain green
- [ ] FullBackupArchiveTest: rejects traversal paths, invalid WAV headers, bad checksums
- [ ] Never add *.jks, *.keystore, passwords, or base64 keys to Git
- [ ] Regular CI still emits DEBUG APK only; manual release signing workflow blocks without secrets

## Device tests (Xiaomi 14T)
- [ ] Before exporting ZIP, stop monitoring; start test / monitor should not run during ZIP I/O
- [ ] ZIP export: inspect manifest.json, statistics.json and clips/<wav> for actual saved WAVs
- [ ] Export with no recorded clips: ZIP still valid and imports just stats/settings
- [ ] Export with two WAVs labeled Hrkanje and Lažni alarm plus notes: restore and confirm labels, WAV playback, counts and notes
- [ ] Import same backup twice: existing sessions and clips not duplicated
- [ ] Import ZIP on clean installation: restore all user data including sound
- [ ] Import ZIP on device with >0 local WAVs: local WAVs and notes unchanged
- [ ] Import when all 40 slots occupied: new WAVs skipped, count shown, existing WAVs unchanged
- [ ] Corrupt statistics digest or WAV byte: reject before changing existing storage
- [ ] ZIP with ../ path, nested file path, unknown entry, >40 clips or oversized decompression rejected
- [ ] Import ZIP while monitoring active: blocked
- [ ] No INTERNET permission; no automatic uploading to Google Drive or elsewhere
- [ ] Audio ZIP is plain, not encrypted: retain on a private encrypted storage location

## Signing (manual, requires your private key)
- [ ] Create long-lived JKS only on owner's trusted computer
- [ ] Configure five GitHub Actions secrets and backup JKS securely outside GitHub
- [ ] Run Signed SnoreGuard release APK workflow; APK must match expected SHA-256 cert
- [ ] Back up old debug installation to ZIP before first signed-release migration
- [ ] After first signed release, later releases with the SAME cert update in place and preserve all settings/data

## Clinical note
Audio clips and AI labels are not a medical diagnosis or replacement for a sleep study.

# SnoreGuard v0.7.0 — review and diagnostic test plan

## CI
- [ ] `./gradlew testDebugUnitTest assembleDebug` succeeds on SDK 35 / JDK 17
- [ ] Existing alarm, mic schedule, night stats, CSV and WAV tests pass
- [ ] New ClipLabel tests: backward-compatible unreviewed default, valid labels
- [ ] New AnalysisHeartbeat tests: exact 5-second threshold, longer gaps and backwards time

## Real-device verification
- [ ] Record a WAV with opt-in enabled; tap the clip and mark Hrkanje
- [ ] Change to Lažni alarm / Nisam siguran / Nije ocenjen, close and reopen the app
- [ ] Add a 160-character note, edit or remove the note; verify updates persist
- [ ] Save another WAV and verify original labels/notes remain intact
- [ ] Delete a WAV; verify metadata and actual private file are deleted
- [ ] Export feedback CSV and confirm label, timestamp, duration and notes
- [ ] Export with notes beginning '=' or '+' and ensure Excel does not execute formulas
- [ ] Confirm CSV contains no audio data; share WAV explicitly via file provider
- [ ] Start 90-second detection test anytime; same button stops safely
- [ ] Confirm new sessions display analysis gaps and older sessions show 'not available'
- [ ] Lock Xiaomi 14T during a long monitoring session and compare heartbeat gaps
- [ ] Confirm alert notifications reach GT 2 in awake/sleep/DND states as applicable
- [ ] Stop then export JSON stats; verify 365-night history migration works

## Privacy and clinical boundaries
- [ ] Labels and notes do not train YAMNet or leave the device automatically
- [ ] Feedback CSV is export-only (audio excluded), and is not included in JSON history backup
- [ ] No medical diagnosis, apnea claims or watch-vibration delivery confirmation
- [ ] No INTERNET app permission
- [ ] Debug-signature upgrades can require uninstall: export JSON, feedback CSV, and selected WAVs first


# SnoreGuard v0.6.1 — statistics export/import checks

## Automated
- [ ] `./gradlew testDebugUnitTest assembleDebug` succeeds (JDK 17 / SDK 35)
- [ ] All prior schedule, snoring, WAV, CSV, alert-burst tests pass

## Android device
- [ ] Export/import panel appears below current session, before detailed graphs
- [ ] JSON export while monitoring prompts to stop first
- [ ] Completed JSON export is a valid app=SnoreGuard, schema=1 document with history and settings
- [ ] Import the same JSON twice, verify no duplicate night records
- [ ] Import JSON on a fresh installation, verify night history and settings match
- [ ] Legacy v0.5.0 / v0.6.0 JSON backups import successfully
- [ ] With 365 completed sessions, 366th newest pushes oldest out while preserving count=365
- [ ] Imported records with invalid start/end or hourly histogram data are rejected without corrupting existing history
- [ ] Incoming JSON file exceeding configured size is rejected
- [ ] Imported count includes only genuinely new records retained after the 365-night limit
- [ ] CSV opens in Excel; CSV must not be presented as an importable format
- [ ] Confirm no WAV audio is present in the JSON backup
- [ ] Debug-signature upgrade may be rejected; export JSON before uninstalling
- [ ] Verify all original snore detection, manual testing, custom schedule and sound controls on Xiaomi 14T and Huawei GT 2

## Safety
- [ ] App has no INTERNET permission
- [ ] Data export uses Android's system file picker
- [ ] Statistics transfer must not start or record the microphone
- [ ] Import does not imply medical diagnosis or treatment

# SnoreGuard v0.6.0 — test and privacy checklist

## CI
- [ ] ./gradlew testDebugUnitTest assembleDebug (JDK 17, SDK 35)
- [ ] All previous detector, burst, timeline and CSV tests remain green
- [ ] Time windows: 23:30–06:45, 12:15–15:30 and equal start=end (24h)
- [ ] Validate WAV RIFF PCM header, rate 16000 Hz, mono, 16-bit and size limits
- [ ] Verify only recognized unique snore events trigger a clip, not overlapping 250ms frames

## Real Xiaomi 14T / Huawei GT 2
- [ ] Set interval 23:30–06:45; start at 23:40; verify stop by 06:45
- [ ] Test short daytime window (12:00–12:10) after manually starting app
- [ ] Test button starts anytime and stops automatically after 90s
- [ ] Test mode displays AI score, recognized events, and notification counts while running
- [ ] Test mode does NOT save completed nights or WAV files
- [ ] With recording OFF, no WAV files created; default must remain OFF after upgrade
- [ ] Enable recording with consent confirmation; test real snore pair during normal session
- [ ] Check WAV contains one pre-roll + post-roll without overlapping frame duplication
- [ ] Check both very short recording stop and normal 12-second recording produce valid WAV
- [ ] Check at most 10 clips per session, at least 20-minute clip spacing, max 40 retained
- [ ] Play each WAV; send via Android chooser as attachment; receiving app can open it
- [ ] Delete one and delete all; after deletion files should not remain in app storage
- [ ] Confirm JSON backup excludes actual WAV bytes, and excludes opt-in recording preference
- [ ] Confirm custom start/end times are restored from JSON
- [ ] Custom schedule charts and CSV should show daytime hours, not only 22:00–08:00
- [ ] Watch alarm delivery with screen off, Sleep/DND and Bluetooth connected
- [ ] Battery use and 8-hour microphone reliability
- [ ] Voice, TV, fan and overlapping background sounds do not trigger excessive false clips

## Privacy / medical limitations
- [ ] No INTERNET permission
- [ ] Saved sound is private under app filesDir/snore_clips/ until user explicitly shares
- [ ] Provider grants temporary READ-only access only to chosen share targets
- [ ] Consent from others sharing the sleeping room should be obtained
- [ ] Do not claim diagnostic accuracy for obstructive sleep apnea: needs qualified clinician
- [ ] Current CI can't physically test Xiaomi microphone or actual GT 2 vibrations
- [ ] Background auto-start deliberately not implemented (Android microphone FGS restriction)
