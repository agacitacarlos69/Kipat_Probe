# Ambient Kipat Tap — Android feasibility

Question: can two unlocked Android phones, **neither showing Kipat**, touch and both show the
other person's Kipat Reveal, using only public Android APIs?

Short answer: **PARTIAL, pending hardware.** Every individual step is supported by public APIs
and by the current AOSP NFC stack, and the design below uses only those steps. Whether two real
phones complete the whole chain — which one polls, OEM NFC stacks, Activity-launch latency
inside one RF contact — can only be proven on two physical devices. Nothing here has been run
on hardware yet.

Sources were read on 2026-10-14: developer.android.com guides, and AOSP source fetched from
android.googlesource.com (`packages/modules/Nfc` main, `packages/apps/Nfc` android14/15-release,
`frameworks/base` main). The AOSP line numbers quoted below are from those revisions.

## What Android public APIs allow

| Step | Evidence | Status |
|---|---|---|
| HCE service started by the system with Kipat not running | HCE guide: "tapping the device against the NFC reader starts the correct service if it is not already running and executes the transaction in the background." | Supported |
| HCE answering while another app is in front | Same; the service is bound by the NFC stack, not by Kipat UI | Supported |
| Locked phone never answers | `HostEmulationManager.onHostEmulationData`: if `requiresUnlock() \|\| isSecureNfcEnabled()` and the device is locked → `AID_NOT_FOUND` + `launchTapAgain(...)` | Supported (Android shows its own "unlock and tap again" UI on the locked phone) |
| An app may register the NFC Forum Type 4 NDEF AID `D2760000850101` | The NFC stack only special-cases it when **no** service registered it (`"Can't route NDEF AID, sending AID_NOT_FOUND"` suppresses a bug report); otherwise it resolves like any AID | Supported |
| A phone in normal mode reads another phone's HCE Type 4 NDEF | Normal discovery polls when `screenState >= NFC_POLLING_MODE` (`SCREEN_STATE_ON_UNLOCKED`, `NfcService`). Android NDEF-checks ISO-DEP Type 4 tags and dispatches by the first record. The user's own observation ("New tag collected / Empty tag" when two phones touched) shows one phone *did* read the other as a tag | Supported in principle; real-device proof pending |
| Dispatch straight into a Kipat activity | NFC guide: `ACTION_NDEF_DISCOVERED` is matched on the **first** record's MIME type; an AAR elsewhere in the message pins the package and falls back to Play | Supported |
| Use the same tag/connection after dispatch | NFC guide: `EXTRA_TAG` is required on NFC intents; `IsoDep.get(tag)` + `transceive` is the documented reader API | Supported while the phones stay in contact |
| Responder shows UI from the HCE service | Android 14+: binding no longer grants BAL by default (`Context.BIND_ALLOW_ACTIVITY_STARTS` Javadoc). **But** the NFC stack binds HCE services with `Context.BIND_AUTO_CREATE \| Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` (`HostEmulationManager.bindServiceIfNeededLocked`, android14-release, android15-release and modules/Nfc main). That `@SystemApi` flag "allow[s] background activity starts from the bound service's process" when the binder holds `START_ACTIVITIES_FROM_BACKGROUND`, which the NFC app does | Supported by AOSP; OEM builds must be verified |
| Reader mode | `enableReaderMode` belongs to a **foreground Activity** and suspends card emulation while active | Usable only once Kipat is foreground; used here only as a fallback |
| Android Beam / NDEF push | Deprecated in API 29; the Beam APIs are gone from current SDKs | Not used |

## What Android public APIs do not allow

- **Reader mode in the background.** It needs a resumed Activity. No public API keeps the
  controller in reader mode for an app that is not visible.
- **Choosing the RF role.** Apps cannot decide which phone polls and which listens in normal
  mode. The NFC controller alternates poll/listen. Whichever phone happens to poll while the
  other listens becomes the reader (initiator).
- **Background Activity starts for ordinary app code.** The general rule is that they are blocked
  (`Background activity launch blocked!` in Logcat, no exception). Kipat relies only on two
  exemptions: the NFC system dispatch (initiator) and the system HCE binding's
  `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` (responder). It does not use `SYSTEM_ALERT_WINDOW`,
  accessibility services, full-screen notifications or foreground services.
- **Winning AID conflicts in the background.** If another installed app also registers
  `D2760000850101` (some NFC tool apps do), Android asks the user which service to use.
  `setPreferredService` only helps while Kipat is in the foreground.
- **Turning NFC on.** Kipat can only show "NFC is off" with a link to Settings.

## Version-specific rules

| Android | Rule that affects Kipat Tap | What Kipat does |
|---|---|---|
| 10+ | Background activity starts restricted; Secure NFC disables card emulation with the screen off and on the lock screen | Relies only on the exemptions above; `requireDeviceUnlock="true"` |
| 12+ | `requireDeviceScreenOn` defaults to true for HCE | Kept |
| 14 (API 34) | Bound services lose BAL unless the binder opts in; the NFC stack opts in with `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` | Responder launches the Reveal from inside the HCE transaction, while the binding is active |
| 15 (API 35) | PendingIntent creators must opt in to BAL | Not used: direct explicit `startActivity` |
| 16 (API 36) | Users can block an app from NFC tag intents (`NfcAdapter.isTagIntentAllowed()`) | Shown in Developer tools diagnostics |
| 17 (API 37, Kipat's target) | Activities receiving NFC dispatch must be protected by `android.permission.DISPATCH_NFC_MESSAGE`; NFC intents are not dispatched to apps in the **stopped** state (never launched, or force-stopped) | NFC filters moved to activity aliases: a protected alias is enabled on API 37+ and an unprotected one below it (an undefined permission would block dispatch on older Android). Kipat must have been opened once |

## What is OEM-dependent

- How each NFC controller arbitrates two phones that both poll and listen: which one becomes
  the reader, how quickly, and whether it retries.
- Whether an OEM NFC stack keeps `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` for HCE services, or adds
  its own restrictions (aggressive battery managers on Xiaomi/OPPO may also delay process start).
- Whether the OEM shows its own tag UI before dispatch, or plays platform sounds.
- Antenna position and RF strength: whether the link survives an Activity cold start (often
  300–800 ms).
- Older devices (Android 10–13) that still poll NFC-DEP (P2P/LLCP) may pair as P2P instead of
  ISO-DEP. Nothing is dispatched in that case.
- Whether `IsoDep.getMaxTransceiveLength()` and the ISO-DEP timeout are generous enough.

## What we can prove in the cloud

- The Type 4 Tag state machine (SELECT application/CC/NDEF file, READ BINARY, error words) on
  exact bytes.
- Bootstrap NDEF encoding and parsing, its MIME type, the AAR and record order.
- An Android-like NDEF reader reading the bootstrap, then upgrading on the same logical link to
  the Kipat AID and running the full mutual exchange (`KipatAmbientSimulationTest`).
- Ambient eligibility, encounter sessions, exactly-once delivery, deduplication across roles,
  malicious tags and readers, manifest wiring and intent routing.

## What requires two physical devices

- That a phone in normal mode, showing another app, reads the other phone's Type 4 bootstrap
  and dispatches to Kipat **without** the system tag viewer.
- That the initiator can reuse the dispatched `Tag` (IsoDep link still up after its Activity starts).
- That the responder's HCE service may start the Reveal from the background on each OEM.
- Role arbitration, process-dead behaviour (`am kill`) versus force-stop (`am force-stop`),
  latency, and the return to the previous app.

## Design that follows from this

```
both phones, unlocked, NFC on, Kipat Tap enabled, Kipat not open
  HCE service (system-bound) answers TWO AIDs:
    D2760000850101  NFC Forum Type 4 NDEF app → tiny bootstrap NDEF
                    [MIME application/vnd.kipat.peer {"v":1,"t":"iso-dep","p":1,"s":<nonce>}, AAR]
    F04B4950415401  Kipat protocol v1 (unchanged second stage)

phone that happens to poll (INITIATOR)              phone that listens (RESPONDER)
  NFC stack NDEF-reads the bootstrap  ───────────►  serves CC + NDEF file (encounter session opens)
  dispatches ACTION_NDEF_DISCOVERED (MIME) to
  TapExperienceActivity (protected alias on 37+)
  IsoDep.get(EXTRA_TAG) on the SAME contact
  SELECT Kipat AID → HELLO → META → READ…  ◄──────  serves the public profile snapshot
  BEGIN/WRITE/COMMIT own profile           ───────►  verifies, parses
  COMPLETE                                           starts TapExperienceActivity (BAL via
  Reveal of responder                                system HCE binding) → Reveal of initiator
```

If the IsoDep link dropped during the initiator's Activity start, the initiator — now in the
foreground — enables reader mode for up to 10 s and asks the user to keep the phones together.

### Fallbacks, ranked

1. **Manual mode** (Developer tools → Manual NFC peer test): explicit Present/Receive, reader mode
   on the receiving phone. Kept for diagnosing OEMs; not the product flow.
2. If physical tests show **responder background launch is blocked** on an OEM, the exchange still
   completes and the person is recorded under Recent on Home. The responder sees them when they
   next open Kipat. No overlay, no full-screen notification.
3. If physical tests show **normal-mode phone-to-phone NDEF dispatch does not happen** on common
   phones, pure NFC cannot meet "neither phone opens Kipat". The next candidate is NFC (or a
   physical card) as the proximity confirmation, plus BLE to complete the exchange. Do not build
   it until the NFC spike has produced device evidence.
