# Test plan — SnoreGuard v0.3.0 / Huawei Watch GT 2

## 1. Build/test gates
- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- Debug APK instaliran na fizičkom Android telefonu; mikrofon se testira samo uz jasnu dozvolu.

## 2. Huawei GT 2 (obavezno fizičko testiranje)
- [ ] U Huawei Health uključiti SnoreGuard Notifications.
- [ ] Tihi "Test GT 2" stiže na sat i izaziva vibraciju (ako ne, probati režim vibracije telefona).
- [ ] Pritisnuti "Test jačeg alarma ×3", potvrditi 3 nova obaveštenja na 0, 8. i 16. sekundi (odvojeni notification ID-ovi).
- [ ] Proveriti koliko puta Huawei GT 2 vibrira (ne pretpostavljati da tri obaveštenja znače tri vibracije).
- [ ] Tokom stvarne detekcije prekinuti hrkanje nakon prve vibracije; druga i treća treba da budu otkazane nakon nekoliko negativnih klasifikacionih blokova.
- [ ] Ponovljeni alarm ne sme početi pre isteka 60 sekundi od prethodne serije.
- [ ] Zabeležiti da li GT 2 vibrira sa uključenim i isključenim Sleep/DND režimom.
- [ ] Proveriti kada je sat na ruci tokom STVARNOG sna (bez insistiranja na buđenju korisnika).
- [ ] Android telefon ostaje bez zvuka; proveriti da li je vibracija telefona zaista ugašena.

## 3. Noćni režim
- [ ] Pre 22:00 dugme start odbija sesiju; test obaveštenja radi.
- [ ] Od 22:00 startuje mikrofon uz foreground obaveštenje.
- [ ] Posle 08:00 servis prestaje, sesija se pojavljuje u istoriji.
- [ ] Lock screen / ugašen ekran ne prekida servis.
- [ ] Ručni STOP i STOP na sistemskom obaveštenju uredno oslobađaju mikrofon.

## 4. Audio i privatnost
- [ ] Nema mrežne (INTERNET) dozvole.
- [ ] Nema audio fajlova, samo zbirne metrike.
- [ ] Model u `app/build/generated/yamnetAssets/yamnet.tflite` je preuzet (~4 MB).
- [ ] AudioRecord 16 kHz mono radi na ciljnom telefonu.
- [ ] Tišina ne izaziva alarm; testirati zvuke ventilatora, govora, TV-a, stvarnog hrkanja.
- [ ] Proveriti lažno pozitivne i lažno negativne slučajeve.
- [ ] Produženi test 2–3 noći radi kalibracije HIGH praga (0.07).

## 5. Očekivana ograničenja
- Android/Huawei može izmeniti ponašanje notifikacija i vibracija.
- Dok detekcija hrkanja nije validirana na stvarnim podacima, skor je probni.
- Nije alat za detekciju respiratornog zastoja ili lečenje apneje.
