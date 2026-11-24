# Test plan — SnoreGuard v0.3.3 / GT 2

## Build and unit tests
- [ ] ./gradlew testDebugUnitTest assembleDebug succeeds (JDK 17 / SDK 35)
- [ ] Two separated positive audio groups within 15 s start one alarm series
- [ ] Overlapping 250 ms windows do not double-count one continuous positive group
- [ ] Positive groups >15 s apart do not start an alarm
- [ ] Cooldown remains 60 s
- [ ] Sliding 15,600-sample PCM window retains 11,600 overlapping samples
- [ ] Installed version visible as v0.3.3 · build 33
- [ ] Old sessions retain legacy hop size / episodes interpretation

## Real device: Xiaomi 14T + Huawei GT 2
- [ ] Confirm 4,000 new PCM samples each ~250 ms on real microphone
- [ ] Test isolated short snore: one event, no alarm
- [ ] Test two separated short snores within 15 s: one alarm
- [ ] Test normal exhalation: no premature cancellation of future vibration
- [ ] Stop recognizing snoring for >20 s: remaining alerts canceled
- [ ] Confirm phone notifications at 0/16/32 s during burst
- [ ] Confirm three GT 2 vibrations in daytime (already worked in v0.3.2)
- [ ] Confirm real sleep behavior: Huawei Sleep/DND may suppress notifications
- [ ] Test with screen off, Bluetooth connected and HyperOS battery optimization
- [ ] Check CPU usage, battery drain and overheating: 4x more TFLite evaluations
- [ ] Evaluate false positives with TV, speech, fan and household noise

## Privacy and limitations
- [ ] No raw audio stored and no INTERNET permission
- [ ] Night service only starts by user action between 22:00 and 08:00
- [ ] Recording stops on STOP or after 08:00
- [ ] Not a medical apnea monitor or validated treatment
