# The physical card's role

**Product role only.** Artwork, materials, finishes, editions, QR placement and print files belong to
the card-design session (`design/card/**`) and are deliberately not prescribed here.

## The question

If two phones can already exchange Reveals with Kipat Tap, and Android 17 now ships Tap to share,
why would anyone buy a card?

Because **the card and the phone tap are not competing transports. They are two carriers of the
same identity, and each is better in different moments.**

> **CARD = physical expression.**  **PHONE TAP = frictionless availability.**

## Reasons to buy a card, ranked

Ranked by *how often the reason applies in a real meeting* × *how much the phone cannot do it*.
This is a reasoned ranking, not survey data.

| Rank | Reason | Why it ranks here | Works today? |
|---|---|---|---|
| 1 | **Works with non-Kipat people (QR/link/web)** | Most people you meet won't have Kipat. Only the card (and QR) reaches them. This is the card's biggest job **and its biggest gap** | **No**: needs backend + web profile |
| 2 | **No need to hand over or unlock your phone** | Kipat Tap needs both phones unlocked. Handing a phone to a stranger is awkward and unsafe | Yes, on the owner's phone only |
| 3 | **Always available** | Phone in a bag, dead battery, Kipat Tap off, the other phone locked or without HCE. The card has no battery and no settings | Partly: the tapping phone still needs NFC on |
| 4 | **Conversation starter** | The object is visible before anyone taps, which starts "Ano 'yan?" (`word-of-mouth-loop.md`) | Yes, as an object |
| 5 | **Tangible identity object** | Like an amiibo: the object *is* you, and the screen recognises it. People keep and care for objects | Yes |
| 6 | **Self-expression** | People buy objects that say something about them (Nothing's lesson). This is legitimate as long as it isn't ranking people | Depends on card design |
| 7 | **Ritual** | Handing over, placing on a phone, the haptic. It is a small ceremony that paper cards also have | Yes |
| 8 | **Status** | Real but fragile. Status that comes from price or rarity invites the collectible/edition trap. Kipat should earn status from craft, never from tiers | Not a goal |

**Product conclusion:** reasons 1–3 are *utility*, and they justify the purchase. Reasons 4–7 are
*meaning*, and they make people talk. Reason 8 is a side effect, not a strategy. **Reason 1 is not
yet true, so the card should not be sold as a product before the backend resolver and web profile
exist.** It can be shown, tested and refined.

## The phone tap's role

- **The fastest zero-accessory interaction** between two Kipat users. Nothing to carry, mutual by
  design, and both people get a Reveal.
- **The habit layer:** it is how people who already have Kipat meet each other at the second,
  third and tenth event.
- **Platform reality:** Android's Tap to share already exchanges contact cards by touching phones on
  Pixel 6+ with Android 17 ([Google Help](https://support.google.com/android/answer/17161144?hl=en)).
  Kipat Tap differs because it shows a **chosen identity in a Reveal and ends in an intentional
  Connection**, not because the touch is new.

## When people prefer each

| Moment | Prefer | Why |
|---|---|---|
| Both have Kipat, both phones out | **Phone** | Mutual, no object needed |
| Other person has no Kipat (most first meetings) | **Card** (→ web, future) or **QR** | Only path that reaches them |
| Your phone is in your bag, or you're carrying things | **Card** | Hand it over or hold it to their phone |
| Their phone is locked or NFC is off | **QR / link** | No NFC dependency |
| Remote, chat, email, slides | **Link** | No physical contact |
| A table of six at a hackathon | **Card** passed around, or QR on screen | One object, many taps |
| Formal meeting where showing a phone feels rude | **Card** | Closest to the paper-card ritual |
| iPhone recipient | **Card** or **QR** (→ web, future) | Kipat Tap is Android-only |

## Why someone installs Kipat

| Situation | Today | After backend + web profile (documented, not built) |
|---|---|---|
| **Kipat ↔ Kipat**, phone tap | Mutual Reveal, Connect | Same, plus Connections that stay current |
| **Kipat ↔ Kipat**, card tap | Only on the owner's phone; others see "can't be opened on this phone yet" | Owner's current Reveal on any Kipat phone |
| **Kipat card ↔ non-Kipat phone** | Browser → kipat.app → nothing | Web profile: name, lines, accent, a reduced Reveal, Save contact, then a quiet "Get Kipat" |
| **QR / link** | Browser → nothing | Same web profile |
| **App install** | A recipient installs to *tap back* | A web visitor installs to *make their own* after seeing one |

The install reason is never "to see who tapped you". The recipient always sees the person first,
free, with no account. Kipat earns the install by being the way to give that experience back.

**No new web infrastructure is proposed here.** The web profile and backend resolver contract
already exist on paper (`docs/nfc-card-identity-sync.md`, "Future web profile"). This document
only says the card's product value depends on them.
