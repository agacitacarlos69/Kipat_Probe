# Kipat Pilot APK: install on two phones without a laptop

The physical pilot runs from a cloud-built APK. Cloud Claude Code changes the code, runs the tests and
pushes to `main`. The **Pilot APK** workflow (`.github/workflows/pilot-apk.yml`) then builds, tests,
signs and publishes the APK as a GitHub **pre-release** called "Kipat Pilot …". You download that same
APK on both phones. No Android Studio, adb or local Gradle is needed.

```
Cloud Claude ─push main─▶ GitHub Actions: unit tests, lint, debug + pilot APK, SHA-256, signature check
                                   │
                                   ▼
            Releases ▸ "Kipat Pilot 0.2.1-pilot.N (abc1234)"  (pre-release, never "Latest")
                                   │
                     ┌─────────────┴─────────────┐
                  Phone A                     Phone B        same APK, same commit
                     └──── Kipat Tap test ───────┘
                                   │
               Export diagnostic (ZIP) on each phone ─▶ upload both to Cloud Claude ─▶ fix ─▶ next pilot
```

## Where to download

- **Releases:** `https://github.com/agacitacarlos69/Kipat_Probe/releases`. Pilot builds are marked
  *Pre-release* and named `Kipat Pilot <version> (<commit>)`. Each one lists:
  - the APK (`KipatPilot-0.2.1-pilot.N-<commit>.apk`);
  - its `.sha256` file and `build-info.json`;
  - the APK SHA-256 and signing certificate SHA-256;
  - the unit-test summary.
- **Actions artifact** (backup, kept 30 days): Actions, then **Pilot APK**, then the run, then
  Artifacts, then `KipatPilot-…`. Artifacts download as a ZIP, so the Releases page is easier on a
  phone.
- A new pilot is built for every code push to `main`. To build one without a push, use Actions,
  then **Pilot APK**, then **Run workflow**.

## Install on Phone A and Phone B

Do this on **both** phones with the **same** release. Check that the commit in the title matches.

1. Open the release page in the phone's browser and tap the `.apk` file to download it.
2. Open the download. If Android asks, allow **Install unknown apps** for that browser or the Files
   app. Then tap **Install**, or **Update** if an earlier pilot is installed.
3. Open Kipat. Go to **Profile**, then **Developer tools**, then **Developer info**. Check:
   - the title (`Kipat Pilot 0.2.1-pilot.N`);
   - the commit (the 7 characters in the release title);
   - the build type (`pilot`).
   Both phones must show the **same commit**. If they differ, install the same release on both.
4. If Android shows *App not installed*, *package conflicts* or *not compatible*, the phone has a
   Kipat build signed with a **different key** (for example a debug build from Android Studio).
   Uninstall that build and install the pilot. Uninstalling removes its local data, including its
   Connections.

**Updates keep your data.** Each pilot is signed with the same release key and has a higher version
code (100 + build number). Installing a newer pilot is an in-place update that keeps:
- the Room database (Connections, recent taps);
- your profile;
- the Reveal and the settings.

## Run the test (on the phones)

1. Go to **Profile**, then **Developer tools**, then **Physical pilot**.
2. Follow **How to test two phones** on that screen, step by step. Also follow
   [PILOT_TEST_PLAN.md](PILOT_TEST_PLAN.md) for the full set of cases.
3. The screen shows, measured by the app:
   - the device and NFC status;
   - the last Kipat Tap, stage by stage, with times;
   - the failure code, if any.
   Things only a person can see (the right person, return to the previous app) go in the
   **Checklist** checkboxes. The app never reports an overall PASS.
4. Tap **Export diagnostic** on each phone. You get a small ZIP named like
   `kipat-pilot-xiaomi-20261017-183200.zip`, which contains `summary.json`, `events.json`,
   `device.txt` and `README.txt`. Share it to Files, Drive or chat, and upload both ZIPs to Cloud
   Claude. **Copy diagnostic** puts the same summary on the clipboard as text you can paste.
5. Tap **Clear results** before the next round.

**What the diagnostic contains:**
- build, device model, Android version, NFC/HCE capability;
- stage results with elapsed milliseconds, and short failure codes;
- a one-way pseudonym for the profile id (`p-xxxxxxxx`), plus the Reveal and accent names;
- the last card-test result and the checklist.

**What it never contains:**
- names, profile text, emails or raw profile JSON;
- session ids, IMEI, Android ID, location, contacts, tokens or passwords.

The event buffer keeps at most 200 NFC events and 10 attempts, stored in app-private storage.

## One-time setup: stable signing (repository owner)

The workflow signs with the **same release key** as `release.yml`. It needs four repository secrets
(GitHub repo, then **Settings**, then **Secrets and variables**, then **Actions**):

| Secret | Value |
|---|---|
| `KIPAT_KEYSTORE_BASE64` | base64 of `kipat-release.jks` (one line) |
| `KIPAT_KEYSTORE_PASSWORD` | keystore password |
| `KIPAT_KEY_ALIAS` | key alias (e.g. `kipat`) |
| `KIPAT_KEY_PASSWORD` | key password |

**If v0.2.0 was published with `release.yml`, these secrets already exist and nothing is needed.**

If they are missing, the Pilot APK run **fails** at "Check signing secrets" and names the missing
ones. It never signs with a throwaway key, because a new key per build would make every update fail
and wipe Connections on reinstall.

To create a key without a laptop, open the repository in a **GitHub Codespace** from the browser
(Code, then Codespaces, then Create). Then, in its terminal:

```bash
keytool -genkeypair -v -keystore kipat-release.jks -alias kipat \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Kipat, O=Kipat, C=PH"            # asks for the passwords
base64 -w0 kipat-release.jks > kipat-release.jks.b64
```

Paste the contents of `kipat-release.jks.b64` into `KIPAT_KEYSTORE_BASE64`, and add the other
three secrets.

Keep a private backup of `kipat-release.jks` and both passwords **outside GitHub**, for example in a
password manager. Then delete both files from the Codespace. `*.jks` is gitignored; never commit it.
If the key is lost, phones must uninstall and reinstall, which loses their local data.

To check stability, every release lists the **signing certificate SHA-256**. It must be identical
on every pilot release.

## Advanced: ADB diagnostics (fallback only)

The normal test needs no computer. Use this only if a phone cannot export, or if Claude asks for
raw logs. Steps:
- enable Developer options and USB debugging on the phone;
- connect it to any computer with platform-tools;
- run:

```bash
adb devices -l
adb logcat -c
adb logcat -s KipatReveal KipatAmbientNfc KipatHce KipatPeerNfc KipatPeerReader KipatPeerProtocol \
  KipatType4 KipatTapLaunch KipatCards KipatLocalStore KipatPilot ActivityTaskManager:W > phone-a.log
```

Logs are content-free by design: no names or profile text. Keep logs and videos out of git.
