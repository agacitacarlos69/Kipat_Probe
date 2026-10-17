# Current product map

What Kipat is **today**, read from the repository at `0efff69` (README, `docs/`, `design/`, and a
read-only pass over `app/src/main/java/com/kipat/probe/`). It describes the product as it is, not
as it should be. **Nothing listed here has been tested on a physical phone.**

## CURRENT PRODUCT

An Android app (Kotlin + Compose, minSdk 26, target 37) that holds **one canonical identity**
(name, headline, role, organisation, avatar, accent, Reveal style). Three tabs, plus Share as a pushed screen:

| Surface | What it does now | Data |
|---|---|---|
| Home | Your identity, **Share** (filled), Preview, Kipat card row, Recent (taps not yet connected, then latest connections) | Identity persisted; Recent in memory |
| Connections | Search, sort, pinned; row opens Person (Save contact, Replay Reveal, "Met by") | **In memory, demo data, lost on process death** |
| Profile | "How others see you", Edit profile, Reveal style (6 templates), Kipat cards, Kipat Tap toggle, Developer tools | Persisted (app-private JSON) |
| Share | QR, Copy link, Share link (system sheet), Kipat card state | Links point at kipat.app, **no web profile exists** |
| Reveal | Translucent overlay above the current app: identity, trust row, **Connect**, Save contact, Open profile | One renderer for every source |

## CURRENT PRIMARY JOB

**Let the person I just met see who I am, in the moment, and let them choose to keep me.**
In code terms: an external tap → `TapExperienceActivity` → Reveal → explicit Connect.

## CURRENT SECONDARY JOBS

1. Save someone into Android Contacts in one tap (`ACTION_INSERT`, no .vcf).
2. Find a person I connected with (Connections search, pin).
3. Control how I appear (Edit profile, Reveal style, accent) once, for every transport.
4. Link and manage physical cards (add, disable, remove) without rewriting them.

## CURRENT TRANSPORTS

All of them converge on `PublicKipatProfileSnapshot` → `RevealPayload` → `RevealScreen`.

| Transport | State | Works between | Limit that matters for product |
|---|---|---|---|
| **Kipat Tap** (phone ↔ phone: HCE Type 4 bootstrap + APDU exchange) | Implemented, simulated, **not physically validated** | Two Android phones, both with Kipat, unlocked, Kipat Tap on | Both people need Kipat; any app can emulate the AID, so it is always "Unverified Kipat"; OEM behaviour unknown |
| **Linked Kipat card** (`https://kipat.app/c/kc_…`, 69 bytes) | Implemented, **local prototype parity**, not physically validated | **Only the phone that linked it** | Another Kipat phone sees "This card can't be opened on this phone yet". A phone without Kipat opens a browser to a URL with **no web profile** |
| QR / link | Working UI; destination missing | Anyone with a camera/browser | Opens kipat.app, which serves nothing yet |
| System Sharesheet | Working | Anyone | Sends a link that leads nowhere yet |
| Self-contained test card (full profile in NDEF) | Developer tools only | Any Kipat phone, offline | Goes out of date; not a product path |
| Debug deep link `kipatprobe://tap/…` | QA only | — | — |

## CURRENT USER FLOWS

```
A. Phone tap (designed, unvalidated)
   both unlocked, Kipat Tap on → touch backs → each phone shows the other's Reveal
   over the current app → Connect (optional) → dismiss → back to previous app

B. Card tap, owner's own phone (works in simulation)
   tap linked card → Reveal of the owner → nothing to connect to (it is you)

C. Card tap, someone else's phone (the flow that matters socially)
   Kipat installed   → "This card can't be opened on this phone yet"     ← dead end today
   Kipat missing     → browser opens kipat.app/c/kc_… → no profile        ← dead end today
                       (AAR falls back to Play; the app is distributed through
                        GitHub Releases, so a Play listing is not assumed to exist. Unverified.)

D. QR / link → browser → kipat.app → no profile                          ← dead end today

E. After a Reveal: Connect → Connection (in memory) → Connections → Person → Save contact
```

## CURRENT RISKS

| # | Risk | Why it matters |
|---|---|---|
| R1 | **No physical validation of any NFC path** | The whole product story is the tap. Ambient phone-to-phone NFC depends on OEM role arbitration and background activity starts (`docs/nfc/ambient-peer-feasibility.md`) |
| R2 | **The card only works on its owner's phone** | The card's social job (someone else taps it) fails until a backend resolver exists |
| R3 | **No web profile** | QR, link and card all dead-end for anyone without Kipat, which is most people |
| R4 | **Connections are in memory** | The retention layer forgets everyone when the process dies |
| R5 | **Platform overlap.** Android "Tap to share" (Quick Share) is rolling out to Pixel 6+ on Android 17, with Samsung foldables announced, and exchanges contact cards and files by touching phones ([fonearena, 2026-08-13](https://www.fonearena.com/blog/?p=489498); [Google Help](https://support.google.com/android/answer/17161144?hl=en)) | Phone-to-phone exchange alone is no longer distinctive. Kipat must win on identity and the Reveal, not on the touch |
| R6 | AID conflicts and blocked tag intents: if another app registers the Type 4 NDEF AID, Android asks the user to choose a service; Android 16+ lets a user block Kipat's NFC tag intents | A silent failure reads as "Kipat doesn't work" |
| R7 | Trust | Everything is Unverified. Sample data shows "Verified, demo" | People may read styling as verification |

## CURRENT PRODUCT CONFUSION

1. **Three names for one gesture.** "Kipat Tap", "Kipat card tap", "Phone tap" (connection source) and
   "Touch another Kipat phone" all describe *the tap*. A user should learn one verb.
2. **Share as a verb vs Share as a screen vs future "Kipat Share".** Home's primary action **Share**
   shows a QR and link, not a tap. A future file-sending feature named "Share" would collide with it.
3. **Card vs phone value.** The card UI says "Hold your card to the back of their phone", but that
   only works on your own phone today. The product promises a social object it cannot yet deliver.
4. **Recent vs Connections.** A tap creates a *Recent* item ("Tapped 2h ago, not connected") with
   an inline Connect. Connections only hold explicit Connects. This is right, but the difference is
   not explained anywhere except by behaviour.
5. **Developer tools hold product-shaped features** (manual peer test, full-profile card writer).
   They are correctly hidden, but they make the NFC surface look larger than the product.
6. **The promise is spoken by the owner** ("Tap. Know who I am. Connect.") while most UI is the
   recipient's. That is a positioning choice, examined in `docs/strategy/positioning.md`.
