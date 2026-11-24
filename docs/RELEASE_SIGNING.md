# SnoreGuard — private, repeatable Android release signing

The v0.7.0 and earlier GitHub Actions artifacts are DEBUG APKs.
They **cannot be seamlessly replaced by a differently signed release APK**.
First export a full ZIP backup and import it after installing the first signed
release. Afterwards, release APKs built using the SAME private keystore can
upgrade in place.

## One-time setup on your own trusted Mac

Do not generate or store the private keystore in GitHub source, chat messages,
public file shares, or an unencrypted shared cloud folder.

1. In Terminal, create a long-lived key:

        keytool -genkeypair -v -keystore snoreguard-release.jks \
          -alias snoreguard -keyalg RSA -keysize 3072 -validity 12000

   Choose strong unique passwords. Keep your keystore in an encrypted offline
   backup, with a separate secure copy of the passwords.

2. Print the public certificate fingerprint:

        keytool -list -v -keystore snoreguard-release.jks -alias snoreguard

   Copy the SHA256 fingerprint line. Do not copy or share the private key.

3. Convert your keystore to a single-line Base64 string on macOS:

        base64 -i snoreguard-release.jks | tr -d '\n' > snoreguard-release.b64

   Base64 is encoding, not encryption. This file is as sensitive as the private
   keystore. Remove it after adding the secret.

4. In GitHub repo Settings > Secrets and variables > Actions, add these
   five repository secrets:

   - SNOREGUARD_RELEASE_KEYSTORE_B64: contents of snoreguard-release.b64
   - SNOREGUARD_RELEASE_STORE_PASSWORD: keystore password
   - SNOREGUARD_RELEASE_KEY_ALIAS: your alias, e.g. snoreguard
   - SNOREGUARD_RELEASE_KEY_PASSWORD: private key password
   - SNOREGUARD_RELEASE_CERT_SHA256: the public SHA256 certificate fingerprint

5. Securely delete snoreguard-release.b64 and save a protected backup of the
   JKS file and password records. Never commit the JKS or raw Base64 to Git.

## Build a release APK

In GitHub Actions, choose Signed SnoreGuard release APK > Run workflow.

The manual workflow fails if secrets are missing, and it verifies the
resulting APK signature against the expected certificate SHA256 fingerprint.
Keystore bytes are decoded only in temporary runner storage and removed at
the end. Regular pushes still build DEBUG APKs.

The first stable release signing key has NOT been configured yet. No claim of
stable upgrades is valid until you safely configure it and verify a signed
APK from this workflow.

## Migration from an installed debug APK

**Important for v0.7.0 and older:** these versions DO NOT have the new ZIP
export option. First use their JSON statistics export, save each WAV through
the share button, and export the clip-review CSV. Verify every file outside
the application BEFORE uninstalling. JSON can be imported in v0.7.1, but the
older individually shared WAVs/clip-review CSV cannot yet be imported back
into app storage automatically. Keep those original WAVs safely for your
doctor and do not uninstall the older app until you have what you need.

**If you can install v0.7.1 without removing the app:**
1. Stop monitoring in v0.7.1.
2. Use Export kompletne kopije (ZIP), and confirm the ZIP contains
   manifest.json and statistics.json plus clips/*.wav if you saved audio.
3. Keep the ZIP privately outside the app; it contains unencrypted audio.
4. Only once you have verified the backup, uninstall the incompatible debug
   APK if Android refuses to update to a differently signed release.
5. Install the first signed release and choose Import kompletne kopije (ZIP).
6. Verify history, WAV playback, clip labels and notes.
7. Future signed releases can update in place with the same package ID,
   same signing certificate and a higher versionCode.

Full ZIP import supports up to 365 historical sessions and up to 40 WAVs.
It does not overwrite local WAVs: when there are not enough free slots, clips
from the backup are skipped with a visible count. The ZIP is **not encrypted**.
