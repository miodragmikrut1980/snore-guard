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
