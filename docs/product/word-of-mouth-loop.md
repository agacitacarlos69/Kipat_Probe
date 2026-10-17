# Word-of-mouth loop

> **"Ang ganda. Ano 'yan?"** (That's nice. What is that?)
> **"Tap mo phone mo."** (Tap your phone.)

This is the loop Kipat is designed around. The owner answers by **doing the tap**, not by
explaining it. It only works if the next three seconds deliver.

```
someone notices ──► "Ano 'yan?" ──► "Tap mo phone mo." ──► Reveal on THEIR phone ──► "Paano?"
   (card, or                                                    │                       │
   a Reveal over                                                ▼                       ▼
   a friend's app)                                         Connect / Save        install, make
                                                           (they keep you)       their own, tap back
```

## BEFORE TAP

| What exists | What it must do |
|---|---|
| The card in hand, on a lanyard or on the table | Look like an object worth asking about. Visual design belongs to the card-design session. Product requirement: it must be **recognisably Kipat** and **obviously tappable** without the word "NFC" |
| The owner's phone | Nothing visible. Kipat Tap works with Kipat closed |
| The owner's line | Four words: "Tap mo phone mo." Never "it has an NFC chip" |

Failure modes: the owner must open an app, unlock something, or explain settings. Each one
turns a gesture into a demo.

## DURING TAP

| Second | Recipient sees | Why it matters |
|---|---|---|
| 0 | A haptic tick and a contact mark at the antenna point, over **their own app** | Acknowledges the physical act at once. They know something happened *because they touched it* |
| ~0.2–0.9 | The Reveal resolves (Contact Ripple by default), and the name is readable by ~1.1 s (design target, not measured) | Their screen shows a person, not a browser or a file |
| ~1.4 | Settled: name, role, organisation, trust in words, **Connect** | One decision, one filled button |

Failure modes, ranked by how much they hurt the loop:
1. **Nothing happens** (OEM blocks the bootstrap, NFC off, phone locked). This is the worst outcome, so physical validation comes first.
2. "New tag collected / Empty tag" from the system viewer, which makes Kipat look broken.
3. A browser opens with no profile (card on a non-Kipat phone, today).
4. A permission or onboarding screen appears before the person.

## AFTER TAP

| Recipient | Owner |
|---|---|
| **Connect**: the owner is kept with "Phone tap", time and (next) event | Phone tap: their phone shows the recipient's Reveal too (Kipat Tap is mutual). Card tap: one-way, so the owner learns nothing unless the recipient connects back later (needs a backend) |
| **Save contact**: one tap into Android Contacts | Recent shows "Tapped just now, not connected" until they Connect back |
| **Dismiss**: back to their app, a Recent item only | Nothing is sent, and nothing nags |
| Asks "Paano?" | Shows Profile → Reveal style: "you pick how people see you" |

## WHY RECIPIENT CARES

- **It is about the person, not the app.** They learn a name, a role and a face in the middle of a
  conversation, without typing or spelling anything.
- **It is respectful:** it doesn't move them to a browser, ask them to join, or fill their contacts.
- **It is useful later:** if they Connect, they can find this person again by name, org or event.
- **It is a reason to reciprocate:** with Kipat, the tap would have been mutual, so the install
  has a clear purpose: *to give back what they just received.*

## WHY OWNER CARES

- **They are introduced well,** in a style they chose, which a paper card or a typed handle cannot do.
- **They don't hand over or unlock their phone** (card), or don't need an accessory at all (phone).
- **They know who kept them:** a mutual tap puts the other person on their phone too.
- **It reflects on them:** a well-made card and a calm Reveal say something about the owner.
  That self-expression is a legitimate motive, and there is no status scoring.

## What breaks the loop today (honest)

| Break | Fix | Roadmap |
|---|---|---|
| The recipient has no Kipat and there is no card → no tap is possible | Show QR/link → **web profile** (future) | NEXT |
| Card on a non-Kipat phone → browser → nothing | Backend resolver + web profile with a reduced Reveal and "Get Kipat" | NEXT |
| Card on another Kipat phone → "can't be opened on this phone yet" | Backend resolver | NEXT |
| No phone has done the tap physically | Two-phone and card validation on several OEMs | NOW |
| The AAR may point at a Play listing that does not exist (distribution is GitHub Releases) *(unverified)* | Publish to Play, or accept browser fallback until then | NEXT |
| iPhone recipients | Card + web profile only. Kipat Tap is Android-only | LATER |

## What we will not do to accelerate the loop

No auto-invites, no "you were tapped by X, join to see them" SMS, no contact-book upload, no
referral credits, no install prompt on the recipient's first Reveal.
