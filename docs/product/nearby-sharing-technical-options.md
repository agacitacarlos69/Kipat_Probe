# Nearby sharing: technical options

**Feasibility study only. Nothing here is built, and nothing here changes the current Kipat Tap
APDU protocol (v1, `docs/nfc-phone-to-phone.md`).** Research date 2026-10-17. Sources are linked
inline. Where an official page gives no number, none is invented: throughput and range are given
as the source states them, and anything else is marked *(unverified)*.

## The rule

> **NFC is for discovery, proximity confirmation, identity/bootstrap and small metadata. Never for large files.**

NFC runs at 106–424 kbit/s over about 4 cm, nominally ([IEEE TechNav](https://technav.ieee.org/topic/nfc/)).
Kipat's own protocol caps a profile at 4096 bytes in ≤ 200-byte chunks, and the link drops when
people move their hands. Even a 2 MB photo would need people to hold their phones still for far
longer than a tap.

## Future architecture

```
 1. NFC TAP              2. IDENTITY / PROXIMITY       3. NEGOTIATED TRANSPORT        4. CONTENT
 ─────────────           ───────────────────────       ───────────────────────        ──────────
 existing Kipat Tap  ──► both Reveals shown;       ──► sender picks ONE item;     ──► receiver sees
 (v1, unchanged)         people choose Connect         phones bring up a local        sender identity +
                                                       high-bandwidth link using      item name/size/type
                         [future protocol version,     a one-time session secret      → Accept / Decline
                          separate from v1: an         exchanged over NFC          ──► transfer, verify
                          ephemeral session secret                                    hash, save via
                          + transport hint, small]                                    system storage
```

- Step 2 is what makes it Kipat: **identity before content**.
- Step 3 must never begin without step 2, and must never send the content before the receiver accepts.
- Any NFC extension is a **new, separately versioned exchange** designed when sharing is approved
  (`feature-gates.md`). Protocol v1 stays as it is. Its HELLO capability flags (`01` download,
  `02` upload) show where negotiation could later live, but nothing is to be added now.

## Options compared

| | Quick Share / Tap to share (system) | Nearby Connections | BLE (discovery) | Wi-Fi Direct (P2P) | Wi-Fi Aware (NAN) | Local Wi-Fi (same network) | WebRTC data channel |
|---|---|---|---|---|---|---|---|
| **What it is** | OS sharing, AirDrop-like | Google Play services P2P API over Bluetooth, BLE and Wi-Fi | GATT / advertising | Direct Wi-Fi link, one device acts as group owner | Wi-Fi discovery and data path with no access point | NSD/mDNS + sockets on the shared LAN | Encrypted P2P channel, needs signaling + ICE |
| **Permissions (target 37)** | None for Kipat (user uses the Sharesheet) | `BLUETOOTH_SCAN/ADVERTISE/CONNECT` (31+), `NEARBY_WIFI_DEVICES` (32+), **`ACCESS_LOCAL_NETWORK` (37+, for WIFI_LAN)**; runtime "Nearby devices" ([guide](https://developers.google.com/nearby/connections/android/get-started)) | `BLUETOOTH_SCAN` (`neverForLocation`), `ADVERTISE`, `CONNECT`; runtime ([docs](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)) | `NEARBY_WIFI_DEVICES` (33+), Location mode on for discovery ([docs](https://developer.android.com/develop/connectivity/wifi/wifip2p)) | `NEARBY_WIFI_DEVICES` (33+), Wi-Fi + Location on ([docs](https://developer.android.com/develop/connectivity/wifi/wifi-aware)) | **`ACCESS_LOCAL_NETWORK`**, blocked by default for target 37 ([docs](https://developer.android.com/privacy/local-network-permission)) | Same as its transport (LAN → local network permission) |
| **Background** | System-owned | Foreground only in practice; a background transfer needs a foreground service with a declared type *(see Play)* | Background scans are throttled *(unverified detail)* | Foreground | Foreground; sessions should be closed when unused (battery) | Foreground | Foreground |
| **Throughput** | "Fast, local Wi-Fi", with Bluetooth fallback ([android.com](https://www.android.com/articles/quick-share-on-android)) | P2P_POINT_TO_POINT: "the highest possible throughput", suited to large files ([strategies](https://developers.google.com/nearby/connections/strategies)) | Low. Metadata only | "Speedy … much longer than Bluetooth" (no figure) | "Higher throughput … than Bluetooth" (no figure) | Network-dependent | Transport-dependent |
| **Range** | ~30 cm for nearby share, per Google's Android guidance | Radio range; Cluster quoted at ~100 m | Metres *(unverified)* | "Much longer than Bluetooth" | Longer than Bluetooth; RTT ranging can set min/max distance | The LAN | The path |
| **Offline** | Yes | Yes, fully offline | Yes | Yes | Yes, no AP needed | Needs a shared network (no internet) | Needs signaling; on a LAN, ICE can use host candidates |
| **OEM support** | Pixel 6+ on Android 17 for Tap to share; Samsung foldables announced ([fonearena](https://www.fonearena.com/blog/?p=489498)) | Any device with Google Play services (no GMS = no API) | Universal | Broad but OEM-variable *(unverified)* | **Hardware-dependent** (`FEATURE_WIFI_AWARE`); may be unavailable during hotspot/Wi-Fi Direct | Universal | Universal (library) |
| **Battery** | System | Moderate during a session | Low | High while active | "Consumes resources and battery" | Low | Moderate |
| **Complexity for Kipat** | **Lowest**: one Intent | Medium: mature API, Kipat adds identity binding | Low for discovery, high for transfer | High: group negotiation, OEM quirks | High: availability churn | Medium, but events often isolate clients *(unverified)* | High: library size, ICE |
| **Google Play implications** | None | Dangerous runtime permissions need a clear rationale. A background transfer needs an FGS type plus a Play Console declaration with a demo video ([Play Help](https://support.google.com/googleplay/android-developer/answer/13392821)) | As left | As left | As left | Local network permission UX on 37 | As left |
| **Privacy / pairing** | Contacts / Everyone-for-10-min visibility; receiver accepts | Both sides accept a connection; encrypted. Auth digits for out-of-band comparison exist in the API reference *(not re-checked here)* | Advertising leaks presence if left on | System dialogs vary | PSK passphrase on the data path; `PeerHandle` is not a stable id | mDNS advertises on the whole LAN | DTLS; signaling must be authenticated |
| **Large files** | Yes | Yes (FILE payload, any size) | No | Yes | Yes | Yes | Yes, chunked |
| **Cross-platform** | AirDrop interop on some devices (Pixel 10 first) ([Android Authority](https://www.androidauthority.com/quick-share-works-with-airdrop-3618023)) | Google's repo lists iOS, but its Swift package release is dated 2021 and Android↔iOS discovery issues were reported ([google/nearby](https://swiftpackageregistry.com/google/nearby)) | Yes (BLE is everywhere) | No iOS | iOS support *(unverified)* | Yes | **Best**, including browsers |
| **Verdict** | **Baseline to beat.** Can't target a person from Kipat | **First spike candidate** | Discovery helper only | Avoid as primary | Watch | Fallback only | Only if the web profile ever needs browser-to-app transfer |

### Notes

- **Quick Share interoperability:** no public developer API for targeting a specific Quick Share
  device was found. Google has described third-party Quick Share integration (WhatsApp first) as
  partner-driven and GMS-dependent, per press reports that could not be fetched directly
  *(unverified)*. Kipat can always hand an item to the **system Sharesheet**, where the user can
  pick Quick Share or touch phones. That costs nothing and needs no permission.
- **Kipat targets SDK 37.** That makes `ACCESS_LOCAL_NETWORK` mandatory for any LAN path,
  including Nearby Connections' `WIFI_LAN` medium. This is a real UX cost: another runtime prompt
  in the "Nearby devices" group.
- **Photo and document pickers:** a future share must use the **system photo picker and
  `ACTION_OPEN_DOCUMENT`**, never broad media permissions. Google Play restricts
  `READ_MEDIA_IMAGES`/`VIDEO` to apps whose core function needs them; occasional use must use the
  picker ([Play policy](https://support.google.com/googleplay/android-developer/answer/14115180)).
  Receiving should write through MediaStore / the Storage Access Framework, so no storage permission is needed.
- **NFC bootstrap + high-bandwidth transport** is the pattern Android's own Tap to share follows
  (touch, then local Wi-Fi). It is feasible with Nearby Connections: NFC exchanges a one-time
  session secret, and Nearby Connections advertises an endpoint name derived from it, so neither
  phone advertises a human-readable name or discovers strangers. *Concept; not prototyped.*

## Transfer consent

The receiver always sees **who** before **what**, and nothing downloads before Accept.

```
┌──────────────────────────────────────────┐
│  (AR)  Ana Reyes                         │   ← identity first: anchor, name,
│        Product designer                  │     role and org, trust in words
│        Nearby Kipat, unverified          │
│ ──────────────────────────────────────── │
│  Ana wants to send                       │
│  design.pdf                              │   ← file name, size, type
│  2.4 MB, PDF document                    │
│                                          │
│  [ Decline ]              [  Accept  ]   │   ← one filled button; Decline is a text button
└──────────────────────────────────────────┘
```

- No auto-download, no preview fetch, no thumbnail before Accept (a thumbnail *is* content).
- Accept saves to a user-visible location via the system. Open is a separate tap.
- Decline tells the sender only "Not accepted". It never says the receiver saw it, and never why.
- The prompt expires if the phones separate or after a short timeout, and is then gone.
- There is no "always accept from Ana" toggle in the first version.

## Security threats and requirements

**NFC proximity must never equal trusted identity.** Any Android app can emulate the Kipat AID,
and the profile a phone presents is **not cryptographically verified**. "Nearby" proves that a
device was close, not who holds it (`docs/nfc-phone-to-phone.md`, "Trust").

| Threat | Requirement |
|---|---|
| **Malicious nearby device** joins or discovers the session | No open discovery. The transport only accepts a peer that proves knowledge of the one-time secret exchanged over the NFC contact. No advertising outside an active session |
| **Spoofed identity** (a fake "Ana") | The Reveal says "Unverified" in words. Consent copy never says "from Ana" without the trust state. Verified identity only comes later, from a backend or NTAG 424 DNA, never from the transport |
| **Unexpected file** | Per-item Accept; nothing is written before Accept |
| **Oversized file** | Sender declares the size up front. Receiver enforces a cap and free-space check before Accept, and aborts if bytes exceed the declared size |
| **Malware / dangerous MIME** | Never auto-open. Reject or warn on executable types (`.apk`, scripts). Determine the type from content as well as the name, and show the type in words |
| **Path traversal** (`../../`, absolute paths, control chars in names) | Never use the sender's name as a path. Sanitise to a display name; the system chooses the storage location |
| **Spam** (repeated offers) | One pending offer per peer; a rate limit per session; no offers without a fresh tap |
| **Replay** of a captured session or offer | Session secrets are single-use and short-lived; per-offer nonces; hash verified at the end |
| **Session hijack** (a third device joins mid-transfer) | Point-to-point only; peer bound to the secret; encrypted transport; the session ends after one item or on separation |
| **Downgrade** (forcing Bluetooth or a weaker path) | Transport choice comes from the negotiated session, not the peer's claim; refuse unknown transports |
| **Integrity** | SHA-256 over the whole item, checked before it is shown as received (the same discipline as protocol v1). Partial files are deleted |

## Privacy

| Never expose | Why / how |
|---|---|
| Device name | Endpoint names are random per session, never "Ana's Galaxy S24" |
| Phone number, email (beyond what the public profile already publishes) | The share session carries no profile fields; identity comes from the Reveal snapshot |
| IMEI, Android ID, advertising ID, Bluetooth/Wi-Fi MAC | Not read, not sent; randomised MACs left on |
| Location | `neverForLocation` / `NEARBY_WIFI_DEVICES` paths only; no location permission |
| Contacts | Never read; Save contact remains `ACTION_INSERT` |
| Storage | System picker (send) and MediaStore/SAF (receive); no broad storage permission |

- **Only the explicitly selected item is offered.** No folders, no "recent photos", no multi-select in the first version.
- **The receiver chooses:** Accept, Decline, or ignore (which expires).
- **Nothing is advertised when no share is in progress.**

## Recommended future transport research (when the gate opens)

1. **Baseline (no new code path):** after Connect, the existing Share flow hands a link or item to
   the system Sharesheet. Measure how often that is enough.
2. **Spike 1:** NFC-bootstrapped **Nearby Connections, P2P_POINT_TO_POINT**, under `experiments/`,
   marked NOT PRODUCTION. Measure time to Accept, throughput and failure rate on the OEM pairs used for
   Kipat Tap validation, against Quick Share on the same pair.
3. **Watch:** Wi-Fi Aware availability across the target OEMs, and iOS interop.
4. **Skip:** custom Wi-Fi Direct, BLE file transfer, local-Wi-Fi-only paths.
