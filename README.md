# SnoreGuard MVP — Android + Huawei Watch GT 2

**Status:** kompletan izvorni Android Studio projekat (MVP, ne medicinski uređaj).  
**Verzija:** 0.3.0 • Kotlin • Android 8+ (API 26+) • target API 35.  
**Postavke:** HIGH prag YAMNet Snoring `0.07`, približno 5 sekundi potvrde, tri odvojena obaveštenja na 0, 8. i 16. sekundi (dok se hrkanje nastavlja), 60 sekundi između početaka serija, 22:00–08:00.

**Novo v0.3.0:** jači alarm kao niz **tri nova Android obaveštenja** na 0/8/16 sekundi, ručni test serije (i van noćnog termina), zaustavljanje preostalih obaveštenja ako detekcija prestane, i brojanje serija naspram obaveštenja. Zadržani su HIGH, lokalna analiza zvuka, noćni prozor i cooldown od 60 s.

**Važno:** GT 2 ne prima direktnu komandu za trajanje vibracije. Huawei Health može grupisati, utišati ili odbaciti uzastopna obaveštenja; zato tri poslata Android obaveštenja **ne garantuju** tri vibracije sata. **Test jačeg alarma** testira isporuku/primetnost, ne hardversku jačinu vibracije.

**Istorija ispravki:** v0.1.1 — importi Java klasa u Gradle Kotlin DSL; v0.1.2 — XML `textAllCaps` atributi. Potpuni Android build i fizički test GT 2 nisu verifikovani u okruženju izrade.

## Šta tačno radi

1. Korisnik otvara aplikaciju tokom noćnog intervala i pritiska **Pokreni noćno praćenje**.
2. Foreground service sa tipom `microphone` čita mono PCM 16 kHz sa mikrofona (16-bit) u blokovima od 15.600 uzoraka (~0,975 s).
3. Svaki blok se obrađuje **lokalno** preko **TensorFlow Lite YAMNet** modela; koristi se class index 38, `Snoring` (AudioSet).
4. Za aktiviranje alarma potrebno je 5 obrađenih blokova, od kojih su najmanje 3 pozitivna, uključujući poslednji. Time se izbegavaju pojedinačni udari, ali algoritam NIJE klinički validiran.
5. Alarm je serija do tri odvojena obaveštenja na 0/8/16 sekundi, samo unutar 22:00–08:00 i dok detekcija hrkanja traje. Pauza između početaka serija je 60 sekundi. Posle 08:00 se praćenje automatski zaustavlja.
6. Huawei Health može preslikati **obično Android obaveštenje** na Huawei Watch GT 2. Sat može vibrirati, ali to NIJE direktna komanda za motor vibracije sata.
7. Statistika se čuva u privatnim Android `SharedPreferences` (poslednjih 30 sesija). Ne čuva se nijedan audio fajl; aplikacija nema INTERNET permission i ništa ne šalje na mrežu.

### Najvažnije ograničenje

Huawei GT 2 može **potisnuti vibracije** tokom Sleep ili Do Not Disturb režima, čak i kada su obaveštenja inače uključena. Android i Huawei Health takođe mogu blokirati tihu notifikaciju ili ponovljene alarme. Zato je **Test jačeg alarma ×3 / Test GT 2** ključan, ali dnevni uspešan test nije garancija noćnog rada.

Aplikacija ima dve kategorije obaveštenja:
- **Upozorenja za sat (bez vibracije telefona)** — podrazumevano, bez zvuka i bez vibracije telefona; GT2 možda i dalje vibrira ako Huawei Health prosledi obaveštenje.
- **Upozorenja (sa vibracijom telefona)** — opcionalni rezervni režim. Nije garantovano da će sat vibrirati; telefon može vibrirati.

Podrazumevano se na telefonu ne pušta zvuk. Nikad ne menjamo sistemski DND ili režim spavanja sata bez korisnika.

## Prvo pokretanje / instalacija

Detaljni koraci su u **`INSTALACIJA.txt`**. Ukratko:

1. Otpakuj ZIP, otvori fasciklu **SnoreGuard** u Android Studio (File > Open), sa mrežom i Android SDK 35.
2. Sačekaj Gradle Sync. Prvi build povlači standardne Gradle biblioteke i model **YAMNet** (~4 MB) sa zvaničnog Google MediaPipe skladišta.
3. Poveži Android telefon (USB debugging) i izaberi **Run > Run 'app'**. Ili Build > Build APK(s) i instaliraj `app/build/outputs/apk/debug/app-debug.apk`.
4. Prihvati dozvole za obaveštenja i mikrofon.
5. Huawei Health > Devices > Huawei GT 2 > Notifications: uključi **SnoreGuard**, pa klikni **Test obaveštenja → Huawei GT 2**.
6. Klikni **Test jačeg alarma ×3** i proveri da li sat vibrira na 0, 8. i 16. sekundi. Tokom ovog testa ekran aplikacije ostavi otvoren. Ako sat ne vibrira: proveri Bluetooth, dozvole, Sleep/DND na satu i rezervnu opciju **Vibracija i na telefonu**.
7. Praćenje je moguće pokrenuti samo između 22:00 i 08:00. Telefon postavi blizu kreveta, ali bez prekrivanja mikrofona i poželjno na punjaču. Ostavi Android dozvolu za mikrofon aktivnom.

### Model i internet

ZIP sadrži **izvorni kod, ne gotov APK**, i NE sadrži binarni model (nije mogao da se pribavi u okruženju za izradu). `:app:downloadYamnet` preuzima model tokom prvog build-a. Bez tog preuzimanja AI detekcija neće raditi; build će prijaviti jasnu grešku, ne lažno tvrditi da koristi AI.

Aplikacija za vreme korišćenja **ne zahteva internet**. Ako build računar nema mrežu, preuzmi `yamnet.tflite` sa [zvaničnog Google MediaPipe URL-a](https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/latest/yamnet.tflite) i sačuvaj ga u `app/build/generated/yamnetAssets/yamnet.tflite` (veličina mora biti preko 3 MB), pa pokreni build. Izvor modela je promenljiva `url` u `app/build.gradle.kts`.

### Gradle wrapper

U ZIP-u je **transparentni minimalni bootstrap JAR**, sa sopstvenim Java izvornim kodom u `tools/wrapper-src`, umesto standardnog Gradle wrapper JAR-a (binary download nije bio dostupan tokom kreiranja paketa). On pri prvom pokretanju preuzima zvanični Gradle **8.11.1** i **proverava SHA-256** distribucije pre izvršavanja. Ako želiš zvanični Gradle wrapper kasnije: u projektu pokreni `./gradlew wrapper --gradle-version 8.11.1 --distribution-type bin`; time se mogu regenerisati wrapper fajlovi.

## Testovi

- `./gradlew testDebugUnitTest` (unit testovi: noćni prozor, cooldown, alarm burst 0/8/16 s i otkazivanje)
- `./gradlew assembleDebug`
- Fizički telefon i Huawei GT2: `docs/TEST_PLAN.md` za test realnog sata i dozvola.

**Napomena:** U okruženju u kome je izrađen ZIP nema instaliranog Android SDK-a ni mrežnog pristupa binarnim Gradle/model fajlovima; zato nije bilo moguće proizvesti i instalirati APK ili izvršiti Android instrumentacione testove. Kotlin logičke funkcije mogu se posebno proveriti lokalno.

## Izvori i principi

- Android foreground mic service: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android FGS microphone permissions: https://developer.android.com/about/versions/14/changes/fgs-types-required
- TensorFlow Lite YAMNet: https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier
- YAMNet AudioSet 521 classes: https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv
- Huawei GT 2 no vibracija u Sleep/DND: https://consumer.huawei.com/ca/support/content/en-us15868556/
- Gradle checksum 8.11.1: https://gradle.org/release-checksums/

## Privatnost i medicinsko upozorenje

Ovo je hobi/prototip aplikacija za podsticaj promene položaja. **Ne meri prestanak disanja, kiseonik, ni kvalitet sna kliničkom preciznošću**. Visok prag osetljivosti može dovesti do lažnih upozorenja; govor druge osobe, TV ili zvukovi iz sobe mogu zbuniti model. Ako postoji glasno hrkanje sa pauzama u disanju, gušenjem ili dnevnom pospanošću, posavetuj se sa lekarom oko procene apneje.

## GitHub / v0.3.0

Objavljuju se izvorni fajlovi, **bez** generisanog APK-a i bez YAMNet modela (model se preuzima prilikom build-a). `CHANGELOG.md` opisuje novosti, a `docs/TEST_PLAN.md` daje test matrice. GitHub nije izvor automatskog ažuriranja instalirane aplikacije; novu verziju treba izgraditi i instalirati.

Noćno automatsko isključivanje Viber/WhatsApp obaveštenja **nije implementirano** u ovoj verziji. Testovi DND/Focus Mode sa Xiaomi HyperOS nisu potvrdili filtriranje obaveštenja na GT 2; takva funkcionalnost bi zahtevala zaseban potvrđen mehanizam.

### Automatska CI provera (nakon GitHub objave)

GitHub Actions workflow `.github/workflows/android.yml` pokreće `testDebugUnitTest` i `assembleDebug` na Ubuntu sa Android SDK 35, zatim postavlja debug APK kao *Actions artifact* ako build uspe. Ovaj CI nije izvršen u okruženju izrade; njegova uspešnost mora da se proveri u GitHub Actions tabu.
