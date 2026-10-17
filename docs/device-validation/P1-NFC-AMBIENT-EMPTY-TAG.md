# P1-NFC-AMBIENT-EMPTY-TAG

**Status:** diagnosis instrumented and avoidable risks removed; physical retest pending.

**Physical evidence:** two Android phones touched with Kipat not open. One phone showed Android's own
**"New tag collected" / "Empty tag"** screen. In the previous round, both phones' pilot exports held
**0 events**: neither phone's Kipat code ran during the encounter.

## What "Empty tag" means, from current AOSP

These are read from `packages/modules/Nfc` (HEAD).

| Layer | Source | Fact |
|---|---|---|
| Poller NDEF check | `libnfc-nci/src/nfc/tags/rw_t4t.cc` | Sends `00A4040007D276000085010100` (v2, Le=00). On any error it retries `00A4040007D2760000850100` (v1). Then `00A4000C02E103`, `00B000000F` (CC validated by `rw_t4t_validate_cc_file`), `00A4000C02E104`, `00B0000002` (NLEN), and READs of at most MLe. Every answer must arrive within **`RW_T4T_TOUT_RESP` = 1000 ms**. |
| Poller dispatch | `NfcNci/.../NfcDispatcher.java` `tryNdef` | With a `com.kipat.probe` AAR, it starts a matching Kipat activity, or else Kipat's launcher. **A read Kipat bootstrap can never end at the system "Empty tag" screen.** |
| Responder routing | `cardemulation/HostEmulationManager.java` `onHostEmulationData` | Unresolved NDEF AIDs are answered `AID_NOT_FOUND` ("Can't route NDEF AID"). If several services claim the AID, Android shows a chooser and **drops the tap**. A cold Kipat process is started (`BIND_AUTO_CREATE`) and the SELECT waits for it. |
| Responder AID cache | `RegisteredAidCache.java`, `RegisteredServicesCache.java` | Conflicts in category `other` have no default, unless one is the foreground-preferred or wallet-role service. A disabled `other` service is skipped. On devices with the NDEF-NFCEE feature, the platform itself registers `D2760000850101` (`NdefNfceeService`). |

So "Empty tag" means the other phone's **SELECT of the NDEF AID never got a 9000 from Kipat**.

The Kipat Type-4 implementation is not the cause in simulation. The new
`KipatType4BootstrapTest` replays the exact `rw_t4t` APDUs, applies every CC check, and parses the
result with `android.nfc.NdefMessage`. It passes.

The remaining causes are runtime, and the previous exports could not tell them apart:

1. **AID not routed to Kipat:** not registered, a conflict, or Kipat's `other` service disabled in NFC settings.
2. **OEM blocks the cold background start:** for example, MIUI/HyperOS Autostart off for a sideloaded app.
3. **Cold start slower than 1000 ms:** process start plus the service's `onCreate`.
4. **Default route or controller table:** the AID lands on an eSE that answers 6A82. This is invisible to apps.

## Changes in this build

**Bootstrap**
- **NDEF mapping 1.0 AID** `D2760000850100` is registered and served with a 1.0 CC. Android's own
  fallback can then reach Kipat when the 2.0 SELECT is answered with an error elsewhere.
- **Faster cold start:** the pilot history no longer loads synchronously in the HCE service's `onCreate`.
- **Specific refusal codes:** a refused NDEF SELECT reports `KIPAT_TAP_DISABLED`,
  `KIPAT_TAP_NO_PUBLIC_PROFILE` or `KIPAT_TAP_DEVICE_LOCKED`, never a bare "not eligible".

**HCE routing self-check** (Developer tools → Physical pilot, and the export)
- **AID routing:** `CardEmulation.isDefaultServiceForAid` for the NDEF v2, NDEF v1 and Kipat AIDs. This
  is the same resolution the NFC service uses, so the answer is YES, NO, or UNKNOWN; never a guess.
- **Service state:** whether `KipatHceService` is enabled.
- **Background state:** background restriction, battery optimisation and standby bucket.
- **Xiaomi guidance:** a note on Autostart, plus a button that opens Kipat's system app settings.

**Per-stage diagnostics (responder)**
- **Service start:** `hce_service_created` (cold, `since_process_ms`, `create_ms`) and `hce_first_apdu`.
- **Type 4 steps:** `ndef_aid_selected`, `cc_file_selected`, `cc_file_read`, `ndef_file_selected`,
  `ndef_nlen_read`, `ndef_payload_read`, `ndef_bootstrap_served` and `hce_custom_aid_selected`.

**FINALIZE barrier**
- **Responder:** holds the committed peer and reveals only after answering FINALIZE. If the link ends
  after COMMIT, it still reveals, because COMMIT already proved both profiles crossed.
- **Initiator:** reveals after FINALIZE's answer. A lost FINALIZE after an answered COMMIT is still mutual.
- **Both sides** log `mutual_complete`.

**Pilot stages** are grouped as Ambient HCE, Custom Kipat, Peer exchange and UI, with a per-phone
**Mutual exchange** verdict.

## Reading the next export

| Phone that showed "Empty tag" (poller) | Other phone (HCE) | Meaning |
|---|---|---|
| no attempt | HCE routing NDEF v2 = **NO** | Android does not route the NDEF AID to Kipat (conflict, disabled, not registered) |
| no attempt | v2 = YES, **no `hce_service_created`** | The OEM never started Kipat in the background (Autostart or battery) |
| no attempt | `hce_service_created` with `since_process_ms` ≳ 1000, then nothing | Cold start too slow for the poller's 1 s timeout |
| no attempt | `type4_refused` | Kipat Tap off, no profile or device locked (named code) |
| no attempt | NDEF steps stop at a named stage | Type-4 exchange problem at that step |
| dispatched | bootstrap served | Bootstrap works: continue with the Custom Kipat and Peer exchange stages |
