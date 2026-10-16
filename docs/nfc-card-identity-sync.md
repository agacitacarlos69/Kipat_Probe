# One Kipat identity: phone, card and preview

**Status: LOCAL PROTOTYPE PARITY. This is not production global card sync.**

On one phone, a linked physical Kipat card, a phone-to-phone Kipat Tap and the in-app preview
all read the same canonical identity. If you change your Reveal, accent or profile, the next tap
of the same card shows the change, and the card is never rewritten. This is covered by unit and
Robolectric tests (`LinkedCardIdentityTest`). It has **not been verified on physical hardware**.

What does **not** exist yet: a shared backend. Without one, a linked card resolves only on the
phone that linked it. Another phone that taps the card sees "This card can't be opened on this
phone yet" and no profile. Nothing in this repository claims that "every Kipat card worldwide
updates".

## The model

```
                         KIPAT IDENTITY (KipatRepository.identity + profileRevision)
                         persisted atomically by KipatIdentityStore → kipat_identity.json
                                          │
                                          ▼
                         PublicKipatProfileSnapshot (published fields only,
                         Reveal template, accent, revision, bytes, SHA-256)
               ┌──────────────────────────┼────────────────────────────┐
               ▼                          ▼                            ▼
   Phone-to-phone Kipat Tap     Linked physical card            Preview / QR / link
   (HCE serves the snapshot)    card id → KipatCardResolver      (reads the same identity)
               │                → owner identity → snapshot              │
               └──────────────────────────┼────────────────────────────┘
                                          ▼
                 NfcProfileReveal.toRevealPayload (one mapping) → RevealPayload → Reveal engine
```

- **One owner of presentation.** `Identity` holds the profile fields, profile template,
  `revealTemplate`, `revealTheme` (accent) and avatar. A card has no Reveal, accent or profile of
  its own. Every Reveal setting is per identity.
- **Revision.** `KipatRepository.publishIdentity` raises `profileRevision` by one on every
  published change, and does nothing if the change is identical. The new identity is persisted
  before it becomes visible, so a crash cannot leave the app showing one profile while the disk
  holds another. The snapshot and the resolver both report the revision a tap was served at.
- **One snapshot.** `KipatIdentityStore.publicSnapshot(context)` is rebuilt once per revision.
  `KipatTapStore.snapshot` (HCE) is this same snapshot; there is no separate Kipat Tap profile
  file any more. An edit is therefore served on the very next tap, with no publish step.
- **Stable ids.** Reveal templates are stored by stable id: `contact_ripple`, `precision_orbit`,
  `line_assembly`, `aperture`, `spatial_card`, `minimal_fade`. Old wire ids and aliases are still
  read. If a stored id is unknown (renamed or removed), the template falls back to the default and
  `unknown_reveal_template_fallback` is logged.
- **Multiple identities later.** Cards point at an `ownerIdentityId`, and the resolver looks up
  the snapshot for that owner. Today a phone has one identity, so a card owned by any other
  identity resolves to `ProfileUnavailable`.

## Linked cards

`cards/LinkedKipatCard.kt`:

| Field | Meaning |
|---|---|
| `cardId` | `kc_` followed by 16 Crockford base32 characters: 80 random bits from `SecureRandom`. Not derived from the tag UID, a name or a counter. |
| `ownerIdentityId` | The identity the card opens |
| `displayName` | The owner's own label ("Kipat card", "Kipat card 2"); never shown to others |
| `status` | `ACTIVE`, `DISABLED` (turned off, reversible) or `REVOKED` (removed, final) |
| `createdAt`, `lastSeenAt`, `lastSyncedAt` | When it was linked, last resolved on this phone, and when the route was last written and read back |
| `cardType` | `NDEF_URI` (NTAG213/215/216 with a URI record) |
| `authenticityCapability` | `NONE` today. `SUN_SIGNATURE` is reserved for NTAG 424 DNA. |

The registry is `KipatCards`: `filesDir/kipat_cards.json`, replaced atomically. Many cards can
point at one identity.

### What is on the card

```
NDEF message (69 bytes)
  1. URI record   https://kipat.app/c/kc_xxxxxxxxxxxxxxxx    36 bytes  (4 header + 1 prefix byte for "https://" + 31)
  2. AAR          android.com:pkg / com.kipat.probe          33 bytes  (3 header + 15 type + 15 payload)
```

Type 2 TLV framing adds 3 bytes, for 72 bytes in total. That fits NTAG213 (137 bytes usable),
NTAG215 and NTAG216. The size is asserted in `linkedCardHoldsOnlyTheRouteAndFitsAnNtag213`. The
card never holds a name, a template hint (`t=`) or an accent. The legacy Developer card writer
also no longer writes the template hint.

### Linking (Profile → Kipat cards → Add Kipat card)

1. Reader mode is on while the screen is open, so a card held there is not dispatched to the Reveal.
2. `CardLinkPlan.decide` runs before any byte is written:
   1. NDEF support
   2. Ownership: a `kc_` id on the card is checked against this phone's registry
   3. Writable
   4. Exact capacity
   5. Ask before replacing other data
3. Generate the id → write the route → reconnect and read back → only an exact match of type,
   TNF and payload calls `KipatCards.add`.
4. The screen says "Card linked" and "Uses your current Kipat profile".

Ownership rules (§52): a card is never taken over.

| Card holds | Result |
|---|---|
| A `kc_` id of this identity (active or disabled) | "This card is already linked", nothing written |
| A `kc_` id this phone doesn't know, or owned by another identity | "This card belongs to another Kipat", nothing written, checked before writability |
| A `kc_` id this identity removed | May be linked again under a **new** id, after confirmation |
| The id this session just wrote but couldn't confirm | Finishes the read-back instead of writing a new id |
| Anything else (blank, URL, self-contained test profile) | Confirm, then replace |

A known limit: a card you linked on your **previous** phone shows as "belongs to another Kipat"
on a new phone. Moving cards between phones needs the backend's proof of ownership.

### Tapping a linked card

```
https://kipat.app/c/kc_… (NDEF_DISCOVERED, own task above the current app)
  → TapExperienceActivity.linkedCard(intent)       kc_ ids only; other ids keep the old RevealResolver path
  → KipatIdentityStore.init / KipatCards.init      a card tap may start a fresh process
  → KipatCardResolver.resolve(cardId)              suspend; local resolver never suspends
       Malformed? → Unknown? → status first (DISABLED / REVOKED) → owner snapshot
  → LinkedCardReveal.payloadFor                    same mapping as Kipat Tap; authenticity UNVERIFIED
  → RevealScreen
```

| Resolution | What the tapper sees | Recorded |
|---|---|---|
| `Resolved(card, snapshot)` | The owner's current Reveal, template and accent; Connect available | Recent tap, `lastSeenAt` |
| `Disabled` | "Kipat card unavailable", "Its owner turned this card off"; no profile; can't connect | No |
| `Revoked` | "Kipat card removed", trust state Revoked | No |
| `Unknown` | "This card can't be opened on this phone yet" | No |
| `ProfileUnavailable` | "Its Kipat profile isn't available" | No |
| `Malformed` | The generic unrecognised card | No |

The status is checked before the profile is read, so a disabled card never touches profile data.
Logs carry event metadata but no profile values:
`card_resolved result=Resolved card=••••B871 source=nfc_tag revision=7` and `card_seen …`.

The resolver interface leaves room for a backend:

```kotlin
interface KipatCardResolver { suspend fun resolve(cardId: String): CardResolution }
class LocalPrototypeCardResolver(cards, snapshotFor) : KipatCardResolver   // exists
// class SupabaseCardResolver(...) : KipatCardResolver                     // future; not built
```

The activity already handles a resolver that suspends: it shows the same invisible pending state
Kipat Tap uses and never a stale profile. Any future network resolver must add a timeout.

## Edits

| Where | Copy | Effect |
|---|---|---|
| Profile → Reveal style | "Used by your phone and linked Kipat cards" | `publishIdentity(identity.copy(revealTemplate, revealTheme))`: revision +1, persisted, next tap of the phone or card shows it |
| Profile → Edit profile | "Changes apply everywhere you share this Kipat identity" | Same path |
| Card detail | Presentation: "Uses your current Kipat profile"; the Reveal row opens the identity's Reveal style | No per-card Reveal and no "Change Reveal" |

Nothing ever says "Updating NFC card…": an edit doesn't touch any card.

## Self-contained cards (developer and offline mode)

`application/vnd.kipat.profile+json` cards ([nfc-self-contained-profile.md](nfc-self-contained-profile.md))
carry a full copy of a profile. They work with no lookup, but **they go out of date**, and the app
says so:

- Developer tools → NFC full profile test → read a card holding your profile. If it no longer
  matches the current identity, it is labelled **Offline test card / Out of date**, with a
  **Tap card to update** button that rewrites it with the current profile.
- **Convert to linked Kipat card** opens the normal link flow. That flow confirms the replacement,
  writes the route, reads it back and verifies it. From then on, the card follows the identity.
- "Load my Kipat profile" puts the current public snapshot into the editor as an offline copy.

## Offline behaviour (honest)

| Situation | Today |
|---|---|
| Owner's phone, no network | Works: everything is local |
| Owner's phone, cold process (killed) | Works: identity and registry are read from disk on the tap |
| Another Kipat phone | Shows "This card can't be opened on this phone yet". Needs the backend. |
| A phone without Kipat | Android opens `https://kipat.app/c/kc_…` in the browser (AAR: Play Store). There is no web profile yet. |
| Self-contained card | Works anywhere offline; it shows the copy written at write time |

With a backend, a future version may cache the last resolved snapshot per card, labelled with its
revision, for offline viewing. That is not built.

## QR and links

Share's QR and link use `https://kipat.app/c/<identity public id>`. On the owner's phone this
resolves to the same canonical identity, with its current Reveal. The web path below is the future
route for everyone else.

## Future web profile (documented, not built)

`https://kipat.app/c/<cardId>` will be served by a web profile with the same contract:

1. Look up the card.
2. Check its status first.
3. Fetch the owner's published snapshot at its current revision.
4. Render name, lines, accent and a reduced Reveal.
5. Offer "Get Kipat".

It must never expose unpublished fields. It must return the same response for an unknown card and
a removed card, so ids can't be probed.

## Security

- **Anti-enumeration:** 80 random bits per id. The id is unrelated to the UID, name or order.
  A future backend must also rate-limit lookups and answer unknown and removed ids the same way.
- **Only published fields:** the snapshot is built from `Identity.publishedFields()` through
  `KipatPeerProfile.fromIdentity`. A test checks that an unpublished field leaves the served bytes.
- **Status before data:** disabled and removed cards never read the profile.
- **Ownership:** linking never overwrites a card with someone else's `kc_` id, and the check
  happens before writability.
- **Authenticity is separate from styling:** every NTAG card is shown as **Unverified**, whatever
  its Reveal. A plain URI can be copied byte for byte. Cloning a card gives the clone the owner's
  current profile, which is public anyway. Disabling the card stops both.
- **NTAG 424 DNA (future):** SUN/SDM appends a per-tap encrypted counter and CMAC to the URL. A
  backend checks it and returns `authenticity = AUTHENTIC` without changing the Reveal path, and
  `authenticityCapability = SUN_SIGNATURE` marks such cards. Not built.
- No credentials, tokens or private test identities are stored or committed. The JSON files live
  in app-private storage.

## Physical test instructions (not yet performed)

Use one phone with this build and NFC on, plus blank NTAG213/215/216 cards. Record the results in
`docs/device-validation/<device>/linked-card-checklist.md`, starting from the
[template](device-validation/_template/linked-card-checklist.md).

1. Profile → Kipat cards → **Add Kipat card**. Hold a blank NTAG215 to the back of the phone.
   Expect "Card linked" and "Uses your current Kipat profile". Read the card with NFC Tools:
   exactly one URI `https://kipat.app/c/kc_…` and one AAR, with no name.
2. Leave Kipat, open another app and tap the card. Expect your Reveal with your current template,
   shown as Unverified.
3. Profile → Reveal style → choose a different style → **Apply**. Tap the **same** card without
   rewriting it. Expect the new style. Repeat for the accent and for a profile edit (name or
   program).
4. Kill Kipat (`adb shell am kill com.kipat.probe`) and tap the card. Expect the latest style.
5. Card detail → **Disable card**, then tap. Expect "Kipat card unavailable" and no profile.
   Enable it again and tap: the profile is back.
6. Hold the linked card in Add Kipat card again. Expect "This card is already linked".
7. Write a self-contained test profile (Developer tools) and change your Reveal. Read the card:
   it shows **Out of date**. Use **Convert to linked Kipat card**, then tap: it now follows edits.
8. On a second phone with Kipat, tap the card. Expect "This card can't be opened on this phone yet".
   This is the documented prototype limit, not a failure.
9. On a phone without Kipat, the browser or Play Store opens. There is no web profile yet.
10. Regression: Kipat Tap between two phones still shows each other's current Reveal, and an edit
    on phone A appears on B's next tap.

Useful logs: `adb logcat -s KipatCards KipatIdentity KipatReveal`.
