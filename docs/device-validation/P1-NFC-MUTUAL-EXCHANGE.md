# P1-NFC-MUTUAL-EXCHANGE

**Status:** fixed in code, physical retest pending.
**First physical test:** 2026-10-17, two Xiaomi phones, Kipat Pilot 0.2.1-pilot.1 (`063019e`).

## Physical evidence (both diagnostic exports)

| | Phone A | Phone B |
|---|---|---|
| Export | `kipat-pilot-xiaomi-20261017-205912.zip` | `kipat-pilot-xiaomi-20261017-210038.zip` |
| Build | 0.2.1-pilot.1, commit `063019e`, pilot | 0.2.1-pilot.1, commit `063019e`, pilot (same) |
| Device | Xiaomi M2007J3SG, Android 12 (API 31) | Xiaomi 2406APNFAG, Android 15 (API 35) |
| NFC / HCE / Kipat Tap | yes / yes / READY | yes / yes / READY |
| Reveal / accent | Contact Ripple / Cobalt | Precision Orbit / Graphite |
| **Profile pseudonym** | **`p-8fd2af7c`** | **`p-8fd2af7c`** (identical) |
| Events / attempts | 0 / 0 | 0 / 0 |

`p-8fd2af7c` is the pseudonym of `KPT-DEMO-0001`, which is `SampleData.DEMO_CARD_ID`.

**Both phones published the same public profile id.**

Neither buffer holds the encounter, so a stage timeline **cannot be reconstructed from these
exports**. The NFC code maps every event it emits, so this can only mean one of the following:
- the encounter happened before pilot-1 was installed;
- Clear results was pressed after the test;
- the touch never reached Kipat.

Pilot-2 adds `installedAt`, `updatedAt` and `resultsClearedAt` to `summary.json`, so this case is
unambiguous next time.

## Root cause

Every install started from `SampleData.identity` with `cardId = "KPT-DEMO-0001"`. The first launch
wrote it to `kipat_identity.json`. Editing the profile changed the name, Reveal and accent, but never
the id. Kipat Tap uses that id to tell people apart, so each phone saw the other as "you":

| Layer | Code | Effect with equal ids |
|---|---|---|
| Initiator exchange | `KipatPeerExchange.run` | `isSelf`, so `SKIPPED_OWN_PROFILE`: the reverse upload never starts, and the responder never receives the initiator |
| Initiator Reveal | `KipatTapInitiator.deliver` | "This is your own Kipat profile." and no Reveal |
| Responder Reveal | `KipatTapLauncher.deliverAsResponder` | `responder_reveal_skipped` |
| Recent | `KipatRepository.recordTap` | the tap is dropped as "your own card" |

Failure class: **B, reverse profile upload never started**, caused by false self-detection
(a session or identity bug). The transport itself was not broken. The protocol simulations always
used two different ids, so they never caught it.
`KipatMutualExchangeTest.physicalFailureReproduced_bothPhonesCarriedTheSharedSampleId` now
reproduces it.

## Fix

- **Unique profile id per install** (`KipatIdentityStore`):
  - The first run assigns `KPT-` plus 12 random Crockford base32 characters.
  - An install that still has `KPT-DEMO-0001` on disk is migrated once on the next start, by the app
    or by the HCE service. It keeps its name, Reveal and accent, and the revision increases by one.
- **Mutual or nothing** (`PeerExchangeResult.Success.reverseTripIncomplete`):
  - If our profile was not confirmed by COMMIT (link lost, or BEGIN/WRITE/COMMIT rejected), the
    initiator shows no one-sided Reveal. It keeps the phones together and retries on the same touch
    or a new one.
  - This applies both to the dispatched initiator and to reader mode.
  - `TOO_LARGE` and `NOT_SUPPORTED_BY_PEER` stay final.
- **Diagnostics:**
  - Correlation id `e-xxxxxx` (a hash of both HELLO session ids), identical on both exports.
  - New failure codes: `OWN_UPLOAD_SAME_PROFILE_ID`, `REVEAL_REQUESTED_SAME_PROFILE_ID`,
    `OWN_UPLOAD_BEGIN_REJECTED`, `OWN_UPLOAD_WRITE_REJECTED` and `OWN_UPLOAD_COMMIT_REJECTED`.
  - Install, update and clear timestamps in `summary.json`.
- Old URL-only tags carrying `KPT-DEMO-0001` still open your own demo profile.

Already correct and unchanged:
- The initiator Reveal is shown only after upload, COMMIT and COMPLETE, and after IsoDep is closed.
- The responder delivers on COMMIT.
- `onDeactivated` clears only an uncommitted partial upload.

## Retest (same two phones, pilot-2)

1. Install the **same** pilot-2 APK on both phones as an update. Open Kipat once on each.
2. Go to Developer tools → Physical pilot. Check that the commit is the same on both phones, and that
   the profile pseudonyms now **differ**.
3. Tap **Clear results** on both phones.
4. Set up the profiles:
   - **Phone 1:** Kyle Jimenez, Precision Orbit, Cobalt. Open Chrome.
   - **Phone 2:** Randy Orton, Contact Ripple, Amber. Open Settings.
5. NFC on, both phones unlocked. Touch the backs together and hold them until a Reveal appears.
6. **Expected:**
   - Phone 1 shows Randy Orton, Contact Ripple, Amber.
   - Phone 2 shows Kyle Jimenez, Precision Orbit, Cobalt.
   - Neither phone connects automatically.
7. Export the diagnostic on both phones. The `exchange` value must match.

**Anything else is a FAIL.**
