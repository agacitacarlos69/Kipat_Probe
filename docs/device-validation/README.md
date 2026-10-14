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

Phases: `info install shell apps templates timing repeat interrupt rotation font motion`.
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
5. Record findings in `<device>/issues.md` as P0–P3. Change UI only for a recorded issue, with
   `DEVICE ISSUE / WHAT WAS OBSERVED / WHY THIS CHANGE FIXES IT` in the commit, then re-run the
   affected phase and mark the issue FIXED with the new evidence.
6. Add a row to `oem-matrix.md`. One phone verifies one phone.
