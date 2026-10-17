# Kipat pilot test plan (two phones, one card)

This is one operator flow for the first real-hardware pilot. It points to the existing detailed
checklists rather than replacing them:
- [ambient two-phone](_template/ambient-two-phone-checklist.md)
- [linked card](_template/linked-card-checklist.md)
- [device kit](README.md)

**Nothing has been tested on physical hardware yet.** Every result starts as NOT TESTED. Simulations,
emulators and Robolectric are not evidence. Write PASS / FAIL / NOT TESTED, a note, and the log file
name for each row. Save results to `docs/device-validation/pilot-<date>/results.md` (copy this file).
Keep videos out of git.

## You need

| | |
|---|---|
| Phone A, Phone B | Android with NFC, ideally two different brands. Record the model, Android version and OEM skin |
| One writable NFC card | NTAG215 (or 213/216), blank or erasable |
| Build | The **same cloud-built Pilot APK** on both phones, downloaded from the "Kipat Pilot" pre-release. No laptop needed: see [PILOT_DISTRIBUTION.md](PILOT_DISTRIBUTION.md). Check that both phones show the same commit in Developer tools → Developer info |
| Diagnostics | Developer tools → Physical pilot → **Export diagnostic** on each phone, after each round |
| Logs (advanced fallback only) | `adb logcat -s KipatReveal KipatAmbientNfc KipatCards KipatIdentity KipatLocalStore KipatPilot` per phone |

## 0. Setup (both phones)

1. Install the build and open Kipat once.
2. Give each phone a **different** profile: Profile → Edit profile → name, program or role, organisation → Save.
   For example, A is "Vash Puno, BS Computer Science" and B is "Ana Reyes, Product Designer".
3. Give each a **different** Reveal: Profile → Reveal style → for example, A uses Precision Orbit and B uses Aperture → Apply.
   Note both accents.
4. Profile → Kipat Tap → On (accept the one-time disclosure).
5. NFC on, screen unlocked, a case is fine (note it).
6. Pilot builds show no sample data. Note how many Connections exist before you start: expect 0 on a fresh install.
7. Developer tools → Physical pilot → **Clear results**, so the diagnostic covers only this round.

## 1. Phone to phone (Kipat Tap)

Touch the backs of the phones together and hold for about 1 s. Nobody presses anything else on the phones after the touch.

| # | Phone A showing | Phone B showing | A shows B's Reveal | B shows A's Reveal | Template and accent correct | Dismiss returns to previous app | Result |
|---|---|---|---|---|---|---|---|
| 1.1 | Home screen | Home screen | | | | | |
| 1.2 | Chrome | Settings | | | | | |
| 1.3 | Chrome | Chrome | | | | | |
| 1.4 | Kipat closed normally (swiped from Recents) | Home | | | | | |
| 1.5 | Kipat process killed (`adb shell am kill com.kipat.probe` while in background; **not** force-stop) | Settings | | | | | |

For each row also check:
- Exactly one Reveal per phone.
- It shows "Unverified" and "Nearby Kipat", never Verified.
- No Connection is created by the touch alone: Connections is unchanged, and the person appears under Recent.

Force-stop (`am force-stop`) is a separate, expected limitation: Android does not dispatch to stopped
apps. Record it, but it is not a failure.

## 2. Connections persist (the "remember" step)

| # | Step | Expected | Result |
|---|---|---|---|
| 2.1 | On A, after a successful touch, press **Connect** on B's Reveal | The button reads Connected; B appears in Connections, source "Phone tap" | |
| 2.2 | Swipe Kipat away from Recents on A, then reopen | B is still in Connections | |
| 2.3 | `adb shell am kill com.kipat.probe` (backgrounded), reopen | B is still there | |
| 2.4 | Reboot A, reopen Kipat | B is still there | |
| 2.5 | Pin B, swipe Kipat away, reopen | B is still pinned | |
| 2.6 | On B, change the organisation and Apply. Touch phones again | A shows the new organisation; still **one** row for B on A; "connected" date unchanged; still pinned | |
| 2.7 | On A, Remove B (do not Undo). Reopen the app | B is gone | |
| 2.8 | Touch phones again | B appears under Recent, **not** back in Connections, until Connect is pressed | |

## 3. Linked physical card (LOCAL PROTOTYPE ONLY)

The card holds only `https://kipat.app/c/kc_…`; the phone that linked it resolves it to its owner's
current profile. **This is local prototype parity.** It works only on the phone that linked the card.
There is no backend yet, so this pilot does not test or claim global synchronisation.

| # | Step | Expected | Result |
|---|---|---|---|
| 3.1 | On A: Profile → Kipat cards → Add Kipat card, hold the blank card | "Card linked", "Uses your current Kipat profile" | |
| 3.2 | Read the card with NFC Tools (any phone) | Exactly one URI `https://kipat.app/c/kc_…` plus the Android app record; no name or template | |
| 3.3 | On A, with Reveal **Precision Orbit**, leave Kipat and tap the card | A's Reveal, Precision Orbit, Unverified | |
| 3.4 | On A: Profile → Reveal style → **Aperture** → Apply. **Do not rewrite the card.** Tap the same card | **Aperture** | |
| 3.5 | Change the accent, then a profile field; tap after each | Each change appears; the card is never rewritten | |
| 3.6 | Card detail → Disable card; tap | "Kipat card unavailable", no profile | |
| 3.7 | Enable it again; kill Kipat; tap | A's current Reveal (cold start) | |
| 3.8 | Tap the card on **B** | "This card can't be opened on this phone yet". This is the expected prototype limit, **not** a failure | |

## 4. Self-contained test card (contrast: a static snapshot)

Use the same card **after** section 3, or a second card.

| # | Step | Expected | Result |
|---|---|---|---|
| 4.1 | On A: Profile → Reveal style → **Precision Orbit** → Apply | — | |
| 4.2 | Developer tools → NFC full profile test → Load my Kipat profile → Write profile card → confirm replace | "PROFILE WRITE VERIFIED" | |
| 4.3 | Profile → Reveal style → **Aperture** → Apply | — | |
| 4.4 | Leave Kipat, tap the self-contained card | **Precision Orbit**, the old snapshot, labelled "Test profile". This proves the difference from a linked card | |
| 4.5 | Developer tools → NFC full profile test → Read profile card | "Offline test card, Out of date" | |
| 4.6 | Convert to linked Kipat card → confirm → tap | A's current Reveal (**Aperture**); it now follows edits | |

## 5. Regression spot checks

| # | Check | Result |
|---|---|---|
| 5.1 | Kipat Tap off on B: A shows no Reveal of B | |
| 5.2 | Locked phone shares nothing; after unlocking, a retry works | |
| 5.3 | Same profile on both phones: "This is your own Kipat profile", no Reveal | |
| 5.4 | Debug deep link still opens a Reveal: `adb shell am start -a android.intent.action.VIEW -d "kipatprobe://tap/demo"` | |

## Sign-off

| Phone pair | Sections passed | Blocking failures | Tester | Date |
|---|---|---|---|---|
| | | | | |

A pilot is **ready for real users** only when, on at least two different-brand phone pairs:
- sections 1.1–1.4, 2 and 3.1–3.7 pass;
- every FAIL has a filed issue with logs.
