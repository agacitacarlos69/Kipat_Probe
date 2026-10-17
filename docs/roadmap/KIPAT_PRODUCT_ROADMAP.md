# Kipat product roadmap

Stages, not dates. Each is marked **NOW**, **NEXT** or **LATER** against the repository as it is
at `0efff69`. Nothing here has been physically validated, so the first job of NOW is evidence, not features.

Release names used below:

| Release | Means |
|---|---|
| **MVP** | The tap works on real phones, the Reveal is recognisable, Connect keeps people, and Connections survive |
| **V1.1** | The card works for everyone: backend resolver, web profile, and "met where" |
| **V2** | Trust and depth: verified cards, and nearby sending if its gate passes |
| **Later** | Cross-platform and anything not yet earned |

## Overview

| # | Stage | Mark | Release | State in repo |
|---|---|---|---|---|
| 1 | Identity foundation | **NOW** (finish) | MVP | Built and persisted. Six Reveal templates |
| 2 | Kipat Tap | **NOW** | MVP | Built and simulated. **Zero physical runs** |
| 3 | Physical card desirability + linking | **NOW** (design, parallel session) / **NEXT** (sell) | MVP (link) / V1.1 (sell) | Linking built locally. Card resolves on the owner's phone only |
| 4 | Connections polish | **NOW** (persistence) / **NEXT** (context) | MVP / V1.1 | In memory; "Met by" exists, "where" doesn't |
| 7a | Backend resolver + web profile | **NEXT** | V1.1 | Contract documented, not built |
| 7b | Verification (NTAG 424 DNA SUN) | LATER | V2 | `authenticityCapability` reserved |
| 5 | Nearby sharing discovery prototype | LATER (gated) | V2 at the earliest | Concept only |
| 6 | Nearby transfer | LATER (gated) | V2 at the earliest | Concept only |
| 8 | Cross-platform (iOS, web) | LATER | Later | Android only |

Stage 7 is split because its two halves have different urgency: the **resolver and web profile
are a prerequisite for the card's social job** (`docs/product/physical-card-role.md`), while
cryptographic verification can wait.

## 1. Identity foundation: NOW (finish)
- **Done:** one canonical identity, revisioned, persisted atomically; six templates; accent; one snapshot served to every transport.
- **Finish:** replace "Verified, demo" sample states with plain "Sample data" everywhere a new user can see them, and make sure first-run asks only for name, role and organisation before the first Preview.
- **Exit:** a new user can make an identity they'd show a stranger and see their Reveal in under a minute (observed, not assumed).

## 2. Kipat Tap: NOW
- Run `tools/device-validation/kipat-ambient-two-phone-validate.sh` and the checklists on **at least four OEM pairs**. Fill `oem-matrix.md` from devices only.
- Fix only recorded device issues (the existing DEVICE ISSUE commit discipline).
- Unify user-facing naming to one verb for the gesture (`current-product-map.md`, confusion #1).
- **Exit:** feature-gate condition 1 (≥ 9/10 ambient touches on ≥ 4 pairs).

## 3. Physical card desirability + linking: NOW / NEXT
- **NOW:** the card-design session owns how it looks. This roadmap only requires that the card is recognisably Kipat and obviously tappable without the word "NFC". Physically validate linking on NTAG213/215/216.
- **NEXT:** do not sell cards until 7a lets a card open on any phone, including non-Kipat phones through the web profile.
- **Exit:** feature-gate condition 2.

## 4. Connections polish: NOW / NEXT
- **NOW:** persist Connections and Recent (the retention layer currently resets).
- **NEXT:** an optional "met at" event label after Connect; search by event; with 7a, profiles that stay current; Share link / Share contact from a Person through the system Sharesheet (feature-gate scope S0, no new transport).
- **Never:** feed, followers, likes, auto-connections, requests (`docs/product/connections-purpose.md`).
- **Exit:** feature-gate condition 4.

## 7a. Backend resolver + web profile: NEXT
- `SupabaseCardResolver` (or equivalent) behind the existing `KipatCardResolver` interface, with a timeout and no network before the first frame. The web profile follows the documented contract: status first, published fields only, the same response for unknown and removed ids, and a quiet "Get Kipat".
- Publish to Google Play so the AAR fallback lands somewhere real *(verify current listing status)*.
- **Exit:** a stranger's phone, with or without Kipat, shows the owner's current identity from a card tap.

## 7b. Verification: LATER
- NTAG 424 DNA SUN/CMAC verified by the backend → `AUTHENTIC` without changing the Reveal path. HCE phones stay "Unverified" until a separate key scheme exists.

## 5. Nearby sharing discovery prototype: LATER (gated)
- Only when feature-gate conditions 1–6 hold. A spike under `experiments/`, marked NOT PRODUCTION: an NFC-bootstrapped Nearby Connections point-to-point session, measured against Quick Share on the same pairs.

## 6. Nearby transfer: LATER (gated)
- Only after the spike meets condition 7. Scope S1: one photo or file, after Connect, consent sheet, option B UX (`design/product-strategy/nearby-sharing/scorecard.md`).

## 8. Cross-platform: LATER
- The web profile (7a) is the first cross-platform surface: iPhone users can receive a card. A native iOS Kipat Tap is a separate project: HCE is Android-specific, and iOS NFC/HCE rules differ by region *(unverified; research before scoping)*.

---

## Decision: where file transfer belongs

**Recommendation: not MVP and not V1.1. V2 at the earliest, and only if the seven gate conditions
pass. Otherwise it stays Later. Link and contact sharing through the system Sharesheet (no new
transport) may ship with Connections in V1.1.**

The working hypothesis was "not MVP". Research supports it and goes further: file transfer
should also wait for the card and the backend. Here is why, criterion by criterion:

| Criterion | Finding | Pushes it |
|---|---|---|
| **Differentiation** | Android 17's Tap to share already sends files by touching phones, with receiver acceptance, on Pixel 6+ (Samsung foldables announced). Quick Share is in every Sharesheet, and some devices interoperate with AirDrop ([Google Help](https://support.google.com/android/answer/17161144?hl=en), [fonearena](https://www.fonearena.com/blog/?p=489498), [Android Authority](https://www.androidauthority.com/quick-share-works-with-airdrop-3618023)). Kipat's only distinct angle is identity first, which needs a working tap and Connections | Later |
| **Engineering risk** | New runtime permissions in the "Nearby devices" group (Bluetooth, `NEARBY_WIFI_DEVICES`, and `ACCESS_LOCAL_NETWORK`, which is mandatory at Kipat's target SDK 37); a Google Play services dependency; Play declarations if a foreground service is needed; OEM variance on top of the NFC variance not yet measured; a security surface (files, MIME, traversal) the app doesn't have today | Later |
| **UX clarity** | Adding Send to the Reveal risks turning the signature into a toolbox, and "Share" already means *share my identity* | After Connect only (option B) |
| **Physical validation** | **Zero** phones have run Kipat Tap. A transfer would be bootstrapped by a tap that isn't yet proven | Blocked |
| **Card maturity** | The card resolves only on its owner's phone, and its design is in progress in another session. The card story should be finished first because it is the word-of-mouth object | Blocked until 7a |
| **User value** | Real ("send me the deck"), but met today by the Sharesheet and the platform. The marginal value is the *history on a Person*, which needs persistent Connections first | V2 at the earliest |

## NOW / NEXT / LATER at a glance

- **NOW:** physical validation of Kipat Tap and linked cards on ≥ 4 OEM pairs; persistent Connections; one verb for the tap; finish identity first-run; card design continues in its own session.
- **NEXT:** backend resolver + web profile; Google Play listing; "met at" context and current profiles in Connections; Share link and Share contact from a Person via the Sharesheet; then start selling cards.
- **LATER:** NTAG 424 DNA verification; the nearby sharing spike and transfer (only through the gate); iOS.

## Features to avoid for now

File/photo transfer; any discovery of nearby strangers; feeds, followers, likes, messaging; CRM,
leads, team admin; paid tiers; new Reveal templates; per-card Reveals; an install prompt on a
recipient's first Reveal; auto-connections; any NFC protocol change.
