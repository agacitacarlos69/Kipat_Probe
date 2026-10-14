# Real-device validation

Kipat's Reveal is a translucent activity over *whatever app was open*. Renders and Robolectric
can't show OEM backdrop behaviour, real timing, haptics, display colour or TalkBack, so they must
be checked on physical phones. Emulators and other devices never count as evidence for a phone.

## Status

| What | State |
|---|---|
| Physical device tested | **None yet.** The development environment is a cloud container with no USB; `adb devices` lists nothing. |
| Off-device preflight | Done: four issues found and fixed with regression tests ([preflight-issues.md](preflight-issues.md)) |
| Kit | `tools/device-validation/kipat-device-validate.sh` + [`_template/checklist.md`](_template/checklist.md) |
| OEM matrix | [oem-matrix.md](oem-matrix.md) — every row NOT TESTED for this build |

## Run it (on a computer with the phone attached)

```bash
./gradlew :app:assembleDebug
adb devices -l                       # exactly one physical phone, USB debugging on
tools/device-validation/kipat-device-validate.sh            # all phases, ~6 min
tools/device-validation/kipat-device-validate.sh templates font   # or individual phases
DRY_RUN=1 tools/device-validation/kipat-device-validate.sh  # print the commands only
```

Phases: `info install shell apps templates timing repeat interrupt rotation font motion`, plus
opt-in `nfcprofile` (not in `all`: it needs a person holding a card; `NFC_WAIT_S`, default 120 s).
Output: `docs/device-validation/<manufacturer-model>/` with `device-info.md`, `performance.md`
(measured numbers only), `screenshots/`, `raw/` (videos and logs; `*.mp4` is git-ignored — keep
large raw files out of git and commit only screenshots/contact sheets), and `checklist.md`.
The script restores dark mode, font scale, animator scales and rotation on exit.

Every deep link it uses is the debug route, e.g.
`kipatprobe://tap/demo?t=orbit&th=aurora` (own card, Precision Orbit, Teal) or
`kipatprobe://tap/KPT-ORG?n=Globe%20Labs&k=org&a=authentic` (company, verified — debug overrides
are always labelled "Sample data"). Parameters: `n` name, `s` role, `o` organisation, `t`
template (`ripple orbit lines aperture spatial minimal-fade`), `th` accent
(`night aurora ember mono`), `a` authenticity (`authentic checking unverified revoked error`),
`k=org` organisation.

## What a person must do on the phone

1. Watch every video in `raw/` and the live Reveal; fill the template table in `checklist.md`.
2. Feel the haptics; press Connect / Save contact / Open profile / Close and confirm the effect.
3. TalkBack (Settings → Accessibility → TalkBack): swipe through a settled Reveal and the style
   picker; expected order is in the checklist.
4. Physical NFC: Profile → Developer tools → Write to tag, tap an NTAG213/215/216; then with
   another app open, tap the tag (warm), then `adb shell am force-stop com.kipat.probe` and tap
   again (cold). Capture `adb logcat -s KipatReveal:I` during each tap.
5. Kipat Tap: see [below](#kipat-tap-two-phones-primary).
6. Full profile NFC test (needs an NTAG215 or NTAG216; an NTAG213 for the too-large case). Run
   `tools/device-validation/kipat-device-validate.sh nfcprofile`, which opens the screen and
   captures `KipatNfcProfile` / `KipatReveal` logs (sizes and result types only, no profile
   fields), then on the phone:
   1. Profile → Developer tools → NFC full profile test (the script opens it).
   2. Load sample profile. Size shows 341 bytes; NTAG213 "Too large", NTAG215/216 "Fits".
   3. Write profile card, hold the card flat on the NFC area until **PROFILE WRITE VERIFIED**.
      With a used card, the replace prompt must appear first; Cancel must leave it unchanged.
   4. Read profile card: all fields, version 1, payload 268 bytes, UID, technologies, writable.
      Copy diagnostic report into `<device>/raw/nfc-profile-report.txt`.
   5. Preview Reveal: name, headline, role, organisation, Precision Orbit, Cobalt, "Unverified
      card" + "Test profile". Never "Verified".
   6. Leave the tool. With another app open, tap the card: the Reveal opens over it (warm). Then
      `adb shell am force-stop com.kipat.probe` and tap again (cold). Tap twice quickly: one Reveal.
   7. NTAG213: Write profile card must show **PROFILE TOO LARGE** (Required 341, Available ≈137)
      and leave the card unchanged. Edit to name only (112 bytes) and write again: verified.
   8. Write a malformed profile with any NFC tool (MIME `application/vnd.kipat.profile+json`,
      payload `{"v":1,"name":`): the tap shows "Kipat profile detected but invalid", no crash.
      `{"v":2,"name":"A"}` shows "Unsupported Kipat NFC profile version".
7. Record findings in `<device>/issues.md` as P0–P3. Change UI only for a recorded issue, with
   `DEVICE ISSUE / WHAT WAS OBSERVED / WHY THIS CHANGE FIXES IT` in the commit, then re-run the
   affected phase and mark the issue FIXED with the new evidence.
8. Add a row to `oem-matrix.md`. One phone verifies one phone.

## Kipat Tap (two phones, primary)

Two physical NFC phones, same build, different profiles. **Neither person opens Kipat for the
test.** Checklist: [`_template/ambient-two-phone-checklist.md`](_template/ambient-two-phone-checklist.md).
Feasibility and limits: [`../nfc/ambient-peer-feasibility.md`](../nfc/ambient-peer-feasibility.md).

```bash
adb devices -l
PHONE_A_SERIAL=<A> PHONE_B_SERIAL=<B> tools/device-validation/kipat-ambient-two-phone-validate.sh            # info install setup apps home killed locked
PHONE_A_SERIAL=<A> PHONE_B_SERIAL=<B> tools/device-validation/kipat-ambient-two-phone-validate.sh forcestop  # separate: force-stop behaviour
DRY_RUN=1 PHONE_A_SERIAL=a PHONE_B_SERIAL=b tools/device-validation/kipat-ambient-two-phone-validate.sh     # print commands only
```

The script installs, opens Kipat once (so you can set profiles and enable Kipat Tap), kills the
process with `am kill` (not force-stop), puts a browser / Settings / launcher in front, then
waits for you to **touch the phones** and captures logs, `dumpsys nfc`, the resumed activity and
screenshots after the touch and after dismissing. Output: `docs/device-validation/ambient/<A>__<B>/`.

Manually, without a computer:

1. Both phones: install the same APK, open Kipat once, set a different profile on each
   (Profile → Edit profile), then Home → **Turn on Kipat Tap** → **Enable Kipat Tap**. NFC on.
2. Close Kipat (Home or swipe it away; don't force-stop). Phone A: open Chrome. Phone B: open Settings.
3. Unlock both and hold the backs together, upper halves first, until both react.
4. PASS only if **A shows B's Reveal and B shows A's Reveal** with no other action, dismissing
   returns A to Chrome and B to Settings, and neither appears in Connections until Connect.
5. Repeat: both on the home screen; A locked (A must share nothing); Kipat Tap off on one phone.
6. Logs: `adb logcat -s KipatAmbientNfc:I KipatType4:I KipatHce:I KipatPeerProtocol:I KipatPeerReader:I KipatTapLaunch:I KipatReveal:I ActivityTaskManager:W`

## Phone-to-phone NFC — manual mode (two phones, diagnostics)

Needs two physical NFC phones with the same build. Checklist:
[`_template/two-phone-checklist.md`](_template/two-phone-checklist.md). Protocol:
[`../nfc-phone-to-phone.md`](../nfc-phone-to-phone.md).

```bash
adb devices -l                                   # note both serials
PRESENTER_SERIAL=<A> RECEIVER_SERIAL=<B> tools/device-validation/kipat-two-phone-validate.sh            # info, install, open, round A→B
PRESENTER_SERIAL=<A> RECEIVER_SERIAL=<B> tools/device-validation/kipat-two-phone-validate.sh swap       # round B→A
DRY_RUN=1 PRESENTER_SERIAL=a RECEIVER_SERIAL=b tools/device-validation/kipat-two-phone-validate.sh     # print commands only
```

Every adb call is bound to one serial; identical serials and emulators are refused. Output:
`docs/device-validation/two-phone/<A>__<B>/` (`device-info.md`, `screenshots/`, `raw/*.log`,
`checklist.md`). Logs carry event names, sizes and short session ids only.

Manually (one computer per phone is fine, or none):

1. Both phones: NFC on, screen unlocked, Kipat installed, profile set (Profile → Edit profile).
2. Both: Profile → Developer tools → **Manual NFC peer test**.
3. Phone A: **Present my Kipat** → "Ready to share". Phone B: **Receive a Kipat** → "Looking for Kipat".
4. Hold the backs together, upper halves first; move slowly until one vibrates; keep still.
5. Expect B: "Kipat found" → "Receiving profile" → "Exchanging profiles" → A's Reveal.
   Expect A: "Kipat found" → "Receiving profile" → B's Reveal. Neither is connected until Connect.
6. Repeat with B's "Send my profile back" off (A shows "Shared"), then swap roles.
7. If Android's "New tag collected / Empty tag" appears, note which phone and capture
   `adb logcat -s KipatPeerNfc:I KipatHce:I KipatPeerReader:I KipatPeerProtocol:I` from both.
