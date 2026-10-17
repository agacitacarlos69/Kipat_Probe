# Kipat product principles

These complement the design principles (`design/KIPAT_DESIGN_PRINCIPLES.md`), which govern how
screens look. These govern **what Kipat does and refuses to do.** Each has its evidence and a
test you can apply to a proposal.

## 1. Identity first
Every interaction resolves to a person before it offers anything else. No form, prompt, file or
install ask appears before the name.
*Evidence:* Popl's lead-capture mode shows a form before the person, HiHello asks the recipient to
join (`design/research/reference-matrix.md` P2, H2). NameDrop's poster shows that the person *is*
the moment.
*Test:* on the first settled frame, is the largest thing a human name?

## 2. Physical action, digital result
The physical act (phone or card) produces a visible result in about a second, on the other
person's screen, over their current app. The result is a person, not a page, file or setting.
*Evidence:* Bump and AirDrop are remembered as gestures with outcomes, Apple Wallet NFC as
"hold near" ([competitive-positioning.md](competitive-positioning.md)).
*Test:* can you describe the feature as "you touch X and Y appears"? If not, it isn't a Kipat feature.

## 3. One identity, multiple transports
Phone, card, QR, link and any future share all serve the same snapshot, in the same Reveal and
hierarchy. Presentation belongs to the identity, never to a transport.
*Evidence:* already the architecture (`docs/architecture.md`, "One identity, many transports").
Blinq and Popl point every method at one live profile.
*Test:* does this change make one transport show something the others don't? Then it's wrong.

## 4. Intentional connections
Taps record a meeting, and only an explicit Connect keeps a person. Connections are one-sided
memory, never automatic, never requested, never ranked.
*Evidence:* `model/Connection.kt`. Bump's lesson (gesture, no relationship). LinkedIn's lesson
(relationship becomes feed).
*Test:* could someone end up in Connections without pressing Connect? Then it's wrong.

## 5. Delight at the tap
Spend all of the product's expressiveness in one place: the first ~1.4 s after contact. A haptic,
one primary motion, a person in their chosen style.
*Evidence:* design principle 3. The frontend-design guidance that "a single orchestrated moment
lands better than scattered effects".
*Test:* is this delight happening anywhere other than the Reveal? Move it or remove it.

## 6. Quiet everywhere else
Home, Connections, Profile, settings and every future sheet are calm, flat and fast. One filled
button per context. No ambient motion, no counts, no badges.
*Test:* does anything move or ask for attention that the user didn't cause?

## 7. Technology disappears
Users never need to know NFC, HCE, APDU, BLE or Wi-Fi. Copy says "tap", "hold your card to their
phone", "touch phones". Failures are explained as actions ("Unlock both phones and hold them
together"), not protocols.
*Evidence:* Apple Wallet's "Hold Near Reader". Current product confusion #1, three names for one gesture (`current-product-map.md`).
*Test:* does any user-facing string name a radio or protocol? Outside Developer tools, that's a bug.

## 8. Sharing requires consent
Nothing arrives on a phone that its owner did not accept, and nothing leaves a phone that its owner
did not choose. Kipat Tap needs a one-time disclosure. Any future content needs **per-item
acceptance, with the sender's identity shown first.** Proximity is never consent, and never trust.
*Evidence:* AirDrop's "Everyone" mode was abused, and Apple now limits it to 10 minutes. Quick
Share also requires receiver acceptance ([fonearena](https://www.fonearena.com/blog/?p=489498)).
*Test:* can any byte beyond the public profile reach the other phone without a tap on Accept?

## 9. No feature for feature's sake
A feature must strengthen identity, the tap or Connections, and must be able to live **after** the
Reveal. If the platform already does it well (contact-field exchange, generic file transfer), Kipat
only adds it when its identity context makes it measurably better.
*Evidence:* Android 17 Tap to share. Kipat's own principle 10 "Honest UI" (don't headline
platform-duplicated features). See `feature-gates.md`.
*Test:* would removing it make Kipat clearer? Then remove it.

## 10. Honest about trust and readiness *(added from research)*
Styling is never verification, and simulation is never validation. "Unverified" stays in words
until cryptography (NTAG 424 DNA SUN, a backend) says otherwise. Unbuilt paths say so.
*Evidence:* HCE lets any app emulate the Kipat AID (`docs/nfc-phone-to-phone.md`). The current
card resolves only locally. NXP's SUN requires a backend check per tap ([NXP AN12196](https://www.nxp.jp/docs/en/application-note/AN12196.pdf)).
*Test:* could a user infer something is verified, synced or tested when it isn't?

## Order of precedence

When principles conflict: **8 (consent) > 1 (identity first) > 10 (honesty) > 4 (intentional) > the rest.**
Example: a faster share that skips the Accept step loses to consent. A prettier Reveal that hides
"Unverified" loses to honesty.
