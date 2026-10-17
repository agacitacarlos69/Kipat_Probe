# NFC feasibility decision gate (phone-to-phone Kipat Tap)

**Status 2026-10-17**

| Capability | Status | Evidence |
|---|---|---|
| Mutual Kipat APDU exchange (SELECT, HELLO, download, upload, COMMIT, FINALIZE) | **Simulated; hardware UNVERIFIED** | 276 unit tests, including both controlled roles. Never run on two phones with an explicit reader. |
| Ambient Kipat Tap (both phones in other apps, touch, both Reveals) | **EXPERIMENTAL / UNVERIFIED; failing on the only tested pair** | Two Xiaomi phones, pilots 1 to 3: Android's "New tag collected / Empty tag" and no Kipat launch |

We do not claim pure ambient NFC works. Simulations prove the protocol and the state machines. They
do not prove radio behaviour, OEM routing or background starts.

## Platform facts (verified in source)

1. **Reader mode belongs to a foreground Activity.**
   - `NfcAdapter.enableReaderMode`: *"Limit the NFC controller to reader mode **while this Activity
     is in the foreground**… disabling any peer-to-peer… and card-emulation modes"*.
   - *"For interacting with tags that are emulated on another Android device using Android's
     host-based card-emulation, the recommended flags are `FLAG_READER_NFC_A` and
     `FLAG_READER_SKIP_NDEF_CHECK`."*
   - Source: `packages/modules/Nfc` `framework/java/android/nfc/NfcAdapter.java`.
   - So Kipat controls the reader side only while a Kipat Activity is in front. Kipat's reader uses
     exactly these flags.
2. **HCE alone never makes two background phones talk.** One phone must act as a reader and SELECT the
   other's AID. With both apps in the background, the only reader is Android's own tag discovery.
   It runs the NDEF check (`rw_t4t.cc`), and it dispatches only an NDEF message it actually read.
3. **"Empty tag" means no NDEF was read.** With Kipat's bootstrap (MIME first, `com.kipat.probe` AAR),
   `NfcDispatcher.tryNdef` opens Kipat or Kipat's launcher. It cannot end at the system Empty tag
   screen ([P1-NFC-AMBIENT-EMPTY-TAG](P1-NFC-AMBIENT-EMPTY-TAG.md)).
4. **NDEF AIDs are contested.**
   - Several services can claim `D2760000850101`. With no default, Android asks the user and drops
     the tap.
   - AOSP registers its own `NdefNfceeService` for that AID on devices with the NDEF-NFCEE feature.
   - The HCE guide: *"If more than one service has registered the AID, then Android asks the user
     which service to invoke."*

## Evidence so far

| Build | Physical result | Exports |
|---|---|---|
| pilot-1 `063019e` | Kipat did not exchange | Both phones: **0 events, 0 attempts**, the same profile id (fixed in pilot-2) |
| pilot-2 `4c2a092` | Empty tag | none provided |
| pilot-3 `70d7274` | Empty tag, Kipat does not launch | **none provided**: the HCE routing answers and per-step events from pilot-3 are not yet available |

The only exports we have (pilot-1) show no Kipat code ran on either phone during the touch.
On that evidence: **Kipat's HCE service, and so its Type 4 implementation, was never executed by
the physical encounter.** Pilot-3 or pilot-4 exports are needed to confirm this for the current build.

## Hard rule

If the NDEF SELECT never reaches Kipat (`Ambient test evidence → NDEF SELECT reached Kipat: No`),
then no change to the CC, NLEN, READ BINARY, NDEF record, MIME or AAR can fix it. All of those happen
after the SELECT. **No further Type 4 changes without new physical evidence.**

## The two experiments (pilot-4)

**Controlled**: Developer tools → Physical pilot → Controlled mutual NFC test. It is never normal
product UX.
- **A:** Kyle's phone: *Act as Kipat presenter*. Randy's phone: *Act as Kipat reader*. Touch once.
- **B:** the same with the roles swapped.

PASS for each, only if all of these hold:
- AID SELECT, HELLO, download, upload, COMMIT and FINALIZE all pass;
- Kyle's phone holds Randy, and Randy's phone holds Kyle;
- both Reveals show;
- it took one contact.

**Ambient**: both phones in other apps, Kipat closed, touch once. Record whether Android's Empty tag
appeared (checklist).

## Decision

| Controlled A and B | Ambient: NDEF SELECT reached Kipat | Conclusion |
|---|---|---|
| FAIL | any | Diagnose the APDU protocol from the controlled stages. Do not touch ambient. |
| PASS | **No** | **The protocol is physically valid. The blocker is ambient Android/OEM NFC discovery (P1-AMBIENT-NFC-DISCOVERY), not the Kipat protocol.** Freeze `KipatApduProtocol`, chunking, SHA, FINALIZE and the mutual exchange. |
| PASS | Yes, then a later stage fails | Ambient reaches Kipat. Fix that named stage only. |
| PASS | Yes, and both Reveals | Ambient works on this pair. Validate other OEMs. |

`HCE routing → NDEF v2 AID: No` on the HCE phone means Android won't give Kipat the AID at all
(conflict, disabled, or claimed by the platform). There is no supported way for an ordinary app to
override that while backgrounded: `setPreferredService` works only in the foreground. That would be a
**platform/OEM limitation**.

## If the limitation is established

Only then write `docs/architecture/ADR-KIPAT-PHONE-TO-PHONE-DISCOVERY.md`, comparing:

1. foreground NFC reader mode;
2. the current ambient NDEF/HCE path;
3. hybrid: BLE discovery plus NFC proximity confirmation plus a Kipat session;
4. BLE/Nearby-first;
5. system share flows.

The product contract is the same for every option:
- no Present or Receive for users;
- one session carries both public profiles;
- both phones reach MUTUAL_READY, then both reveal;
- one-sided = FAIL.

Do not call a BLE transfer "NFC". Nothing in this list is implemented, and none of it will be
before the gate says so.

## Validation matrix

| Pair | Controlled | Ambient |
|---|---|---|
| Xiaomi M2007J3SG (Android 12) + Xiaomi 2406APNFAG (Android 15) | not run | failing (Empty tag) |
| Pixel, Samsung, OPPO | not tested (no devices) | not tested |

A single OEM pair cannot show that all Android phones fail, and simulations cannot show that they work.
