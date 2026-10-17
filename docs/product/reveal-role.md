# The Reveal's role

The Reveal is Kipat's **signature asset**. It is the one thing a person should be able to
recognise from across a table, the way people recognise AirDrop's sheet or NameDrop's poster. Its
job is to make the transport disappear: whether the tap came from a phone, a card, a QR, a link or
(later) a share, **the recipient sees the same person, in the same style the owner chose, with the
same hierarchy.**

```
 phone tap ─┐
 card tap  ─┤                                       ┌─ anchor (photo, logo, monogram)
 QR / link ─┼─► one identity snapshot ─► REVEAL ───►├─ name
 replay    ─┤   (owner's template, accent)          ├─ role, then organisation (quieter)
 future    ─┘                                       ├─ trust, in words
 share                                              └─ Connect, then Save contact / Open profile
```

Already true in code: every source becomes a `RevealPayload` rendered by one `RevealScreen`,
and template and accent belong to the identity, never to a card or phone (`docs/architecture.md`).
Missing: the **web** Reveal for non-Kipat recipients (future web profile, reduced motion).

## What it accomplishes

| Level | What it does | Evidence or reason |
|---|---|---|
| **Functional** | Answers "who is this?" over the recipient's current app with one decision (Connect). No browser, no download, Save contact in one tap | `docs/reveal.md`; competitor recipients land in a browser and download a .vcf (Blinq, HiHello support articles) |
| **Emotional** | Acknowledges the physical act at once (haptic + contact mark at the antenna), then presents a *person* in the style they chose. It feels like being introduced, not receiving a file | NameDrop shows that a self-designed poster at the moment of exchange carries emotion ([Apple](https://support.apple.com/guide/iphone/namedrop-iphone-share-contact-info-iph1b6c664b7/ios)) |
| **Brand** | It is the only orchestrated motion in Kipat, so it *is* the brand. Six curated templates are six recognisable variations of one object, not six products | Design principle 3 "Quiet shell, memorable Reveal"; Nothing's lesson: one consistent signature |

## Rules that keep it a signature (and not a toolbox)

1. **Identity before anything else.** The first settled state shows anchor, name, context, trust and
   one filled action. Nothing else.
2. **The same everywhere.** A card tap and a phone tap of the same person look identical, apart
   from trust words ("Unverified Kipat" plus "Nearby Kipat" for phones).
3. **Owner chooses style, recipient chooses action.** The owner never adds buttons to someone
   else's screen.
4. **Content-height surface** (≈ 280–370 dp). Any future action must fit inside that height or live
   one step later (`design/product-strategy/nearby-sharing/wireframes.md`).
5. **Trust is words, not style.** A beautiful Reveal is not a verified one.

## When the Reveal should NOT appear

| Situation | What happens instead | Why |
|---|---|---|
| You tap **your own** card or phone | Your own Reveal is fine as preview, but it is **never recorded** as someone you met | Already a rule in code |
| The same card or person within the debounce window (1.5 s; 8 s across Kipat Tap roles) | Nothing new | One tap, one Reveal |
| The phone is locked | Nothing. Android asks to unlock, and Kipat shares nothing | `requireDeviceUnlock` |
| Kipat Tap is off on either phone | No Reveal, no exchange | Consent |
| Card disabled or removed | A plain status ("Kipat card unavailable"), **no identity, no motion template** | Status before data |
| Unresolvable or unknown card | Neutral shell with **Done**, not Connect | Already built |
| The user is in a call, navigation, or a full-screen game *(proposal)* | Defer to Recent with a quiet notification, or show Minimal Fade only. **Needs device testing** | An overlay over a call or a game is hostile |
| Inside Kipat's own Connections list | Not on scroll, only on explicit **Replay** | Motion only when the user asks |
| Receiving a file (future) | The Reveal is *not* replayed for every item. Identity appears once, compact, above the consent prompt | Repetition turns a signature into noise |
| Bulk contexts (an organiser scanning 200 attendees, future) | No Reveal. A list instead | The Reveal is for meeting one person |

## How much motion is too much?

Kipat's motion rules already set the ceiling (`design/reveal-templates/README.md`): **one primary
motion + surface resolve + text staging**, settled once in ≤ 1.4 s, name readable by ~1.1 s, no
particles, glow, gradients, blur, loops or sound, and a reduced-motion equivalent of ~200 ms.
For strategy purposes, motion is too much when any of these is true:

1. **It delays the name.** If the name is readable later than ~1.1 s, the motion is decoration.
2. **It repeats.** A second play the user didn't ask for (re-tap within the window, every file, every list row).
3. **It loops or idles.** Anything moving after the clock stops reads as "loading".
4. **It competes with the decision.** Connect must not move after it appears.
5. **It is recognised as an effect before it is recognised as a person.** If someone describes the Reveal as "the sparkle" rather than "it showed who she is", the motion is wrong.
6. **It is used outside the Reveal.** The shell stays still, so the Reveal stays special.

**Strategic consequence:** new templates are *not* a growth lever. Six is enough. Adding more
dilutes recognisability, so future work should make the existing templates more reliable on
devices, not more numerous.
