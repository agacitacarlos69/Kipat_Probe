# Product ladder

The ladder ranks what must be **true and valuable for a person**, from the base up. Each rung
exists to make the one below it worth more. A rung that does not strengthen the rungs below it is
a feature, not a step.

## The proposed order, and the test

| | Proposed (brief) | Recommended | Why it moved |
|---|---|---|---|
| 1 | Identity | **Identity** (including its Reveal) | Unchanged. Every transport resolves to it |
| 2 | Kipat Tap | **The tap** (phone first) | Unchanged in position. Renamed to the act, because card and phone are one gesture with two carriers |
| 3 | Kipat Card | **Connections** | Up one |
| 4 | Connections | **Kipat Card** | Down one |
| 5 | Kipat Share (future) | **Kipat Share (future, conditional)** | Unchanged, now gated (`feature-gates.md`) |

**Why Connections now sits above the card:**

1. **A gesture without an "after" does not last.** Bump reached a reported 125 million downloads
   with a memorable knock and no lasting relationship, and was shut down within months of its
   acquisition ([Wikipedia](https://en.wikipedia.org/wiki/Bump_(application))). NameDrop and
   Android's Tap to share now give that same moment away free with the OS
   ([Apple](https://support.apple.com/guide/iphone/namedrop-iphone-share-contact-info-iph1b6c664b7/ios),
   [Google](https://support.google.com/android/answer/17161144?hl=en)). Kipat's reason to exist
   after the tap is Connections.
2. **The card cannot do its social job yet.** A linked card resolves only on the owner's phone,
   and kipat.app serves no web profile (`docs/nfc-card-identity-sync.md`). Selling a card before
   a backend resolver exists sells an object that fails the first time someone else taps it.
3. **Connections is cheap to make real, and the card is not.** Connections needs local
   persistence and meeting context. The card needs a backend, a web profile, manufacturing and
   physical validation.

**What does not change:** the card is still the product's most visible symbol and its best
word-of-mouth object (`word-of-mouth-loop.md`), and its design work continues in parallel in the
card-design session. The order says only what must be true **before you ask people to buy a card**.

## The rungs

### 1. Identity
One canonical, owner-controlled public identity: name, role, organisation, anchor (photo, logo or
monogram), accent and Reveal style. Edited once, served by every transport, with no rewrite.
**Built.** The value it adds is that **you decide how you are met.**

### 2. The tap
Physical contact → the other person sees your Reveal above whatever they were doing → they choose.
The phone is the zero-accessory carrier; the card is the physical carrier. **Built, simulated,
unvalidated.** The value it adds is that **there is nothing to type, nothing to scan, and no app
to open.**

### 3. Connections
The people you chose to keep after a tap: how and when you met, their profile as it is now, and
the Reveal you can replay. **Partly built, in memory only.** The value it adds is **a reason to
open Kipat when nobody is tapping.**

### 4. Kipat Card
A physical object that carries your identity when your phone is in your pocket, and works for
people without Kipat (once the web profile exists). **Linking is built. Social resolution needs a
backend.** The value it adds is **availability, ritual and self-expression** (`physical-card-role.md`).

### 5. Kipat Share (future)
After a Reveal, and only to a person you can see, send one thing they accept. **Not built, gated.**
The value it adds is **content with context**, but only if it beats Android's Tap to share at
something that matters (`feature-gates.md`).

## The core loop

```
CREATE IDENTITY ──► TAP (phone or card) ──► REVEAL ──► CONNECT ──► REMEMBER ──► RECONNECT
       ▲                                       │            │            │           │
       │                                       │ dismiss    │            │           │
       │                                       ▼ (no trace  │            │           │
       │                                   back to their    │            │           │
       │                                   app; a Recent    │            │           │
       │                                   item only)       │            │           │
       └──── edit once: every card and phone serves it ◄────┴── profile stays current ┘

future, after CONNECT or on a Person:  ──► SHARE SOMETHING (receiver accepts)
```

- **REMEMBER** means the Connection holds *how* you met (phone tap, card, QR, link), *when*, and,
  next, *where* (an event name the user types or accepts). It does not mean a feed.
- **RECONNECT** means opening a Person to call, message or save them, or replaying their Reveal
  to remember the face. The current profile is what you see, not the one from that day.

## Definitions (no invented metrics)

These are definitions of *what to observe*. No numbers are claimed. Thresholds belong in
`feature-gates.md`, and they are set before testing, not reported.

| Term | Definition for Kipat | Observable event |
|---|---|---|
| **Activation** | A person has an identity they would show to a stranger **and** has seen their own Reveal | Profile edited from sample + Preview played once |
| **First value (owner)** | Someone else saw their Reveal | First external tap that produced a Reveal on another phone |
| **First value (recipient)** | They chose to keep someone | First Connect |
| **Habit** | Kipat is the default way they introduce themselves at in-person events | Taps in at least two separate events or weeks |
| **Retention** | They return to Connections for a reason other than tapping | Opening a Person, Save contact, or Replay without a tap that day |
| **Word of mouth** | A recipient asks what it is, or an owner shows it unprompted | Recipient installs after a tap (attribution: first Reveal received before install) |

## Questions

**What makes someone tap a second time?**
The first tap felt good and **worked the first time**, and the next meeting comes soon. For
students that means the next org event, hackathon or class. The real driver is *reliability*: one
failed tap in front of a stranger undoes several good ones, so physical validation is a product
priority, not QA. Secondary: they changed their Reveal or profile and want to see it on someone
else's phone.

**What makes someone show it to a friend?**
The Reveal appears *over the other person's app*, with the owner's chosen style. That is visible
and it surprises people. A friend asking "paano mo ginawa 'yan?" (how did you do that?) is the
moment. Nobody shows a friend a setting, a QR or a list.

**What makes someone buy a card?**
In rank order (`physical-card-role.md`): it works when your phone is away or locked, you don't
hand over or unlock your phone, it is *yours* (self-expression), and it reaches people without
Kipat. It does not work until the card opens on other phones.

**What makes a recipient install the app?**
Two honest triggers: (1) they received a Reveal and want to *give* one back. Kipat Tap is mutual,
so it only works if both have Kipat. (2) They tapped a card, saw a web profile (future), and want
their own. Never a prompt on their first Reveal: the Reveal is identity first, and an install ask
there would be an advert (`design/research/reference-matrix.md` H2, P2).

**What are the legitimate network effects?**
- **Direct, local:** phone-to-phone works only between Kipat phones, so each new user makes the
  tap work with everyone in their circle (org, class, team). The network is *physical clusters*,
  not a global graph.
- **Cross-side:** every card someone else taps is a demo to a non-user, and becomes an
  acquisition path once the web profile exists.
- **Not legitimate:** contact-book uploads, "invite 5 friends", auto-messages to the people you
  tapped, referral credits. None of these will be built.

**What retention reasons exist? (no streaks, points or badges)**
1. **Finding someone again:** "who was the designer from the hackathon?" Search by name, org or event.
2. **Their profile stays current:** they changed jobs or schools and the Connection shows it, which a contact entry does not. *This needs the backend resolver. Today a Connection keeps the snapshot from the tap.*
3. **Replay:** remembering a face through their own Reveal.
4. **The next event:** you will meet people again, and your identity is ready.
5. **Your own identity changed:** you edit once, and the card and phone serve it.
