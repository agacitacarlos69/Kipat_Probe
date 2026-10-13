# Kipat design principles

Derived from `design/research/reference-matrix.md` (Linear, Raycast, Android/M3, Blinq, HiHello,
Popl) and `design/audit/current-ui-audit.md`. Each principle names its evidence and a test you
can apply to a screen.

Kipat: **Tap. Know who I am. Connect.** An identity and physical-to-digital connection system for
students, developers, professionals, founders, creators, speakers and organisation members.

## 1. Identity is the hero
People open Kipat to present or manage *who they are*. The person's name, face and role are the
largest, highest-contrast things on any screen that shows them; nothing decorative competes.
*Evidence:* L1 ("don't compete for attention you haven't earned"), P2/H2 (competitors put forms
and prompts before the person).
*Test:* squint at the screen — is the name the first thing you see?

## 2. One primary action per context
| Context | Primary action |
|---|---|
| Home | Share |
| Share | Show QR / copy link |
| Connections | Open a person |
| Person | Save contact |
| Profile | Edit profile |
| Reveal | Connect (or Save contact when connecting isn't possible) |
Everything else is a text button, a row, or an overflow/long-press menu.
*Evidence:* R1 (primary on Enter, rest in Action Panel), L3 (verb always reachable).
*Test:* is there exactly one filled button?

## 3. Quiet shell, memorable Reveal
The app never performs: no ambient animation, no glow, no gradients, no glass. The NFC Reveal is
the one orchestrated moment and may be kinetic — a contact ring and an edge trace — but still
restrained.
*Evidence:* R3 (materials "in tasteful ways to enhance the functional nature"), frontend-design
("a single orchestrated moment lands better than scattered effects").
*Test:* does anything move that the user didn't cause, outside the Reveal?

## 4. Typography and spacing before containers
Before adding a Card/Surface ask: can spacing, alignment, type, a divider or a background tone do
it? If yes, no container. The shell has exactly one card: the preview of the card a recipient
sees.
*Evidence:* L1 ("structure should be felt not seen"), A3 (lists without per-row containers).
*Test:* count containers; any that only group two lines get deleted.

## 5. Physical → digital
Kipat's motif is **contact**: a point touched, a line drawn from it. It appears as the Kipat mark,
the Reveal's contact ring at the phone's antenna position, and the cobalt line that traces the
card edge from that point. Never as NFC clip-art, waves wallpaper or particle energy.
*Test:* is the motif doing a job (acknowledging a tap) or decorating?

## 6. Professional, not corporate
Plain words, sentence case, real names and roles. No CRM vocabulary ("leads"), no dashboard
metrics, no upsell tiles. Works for a student and an executive.
*Evidence:* H3/P1 (CRM drift in competitors), audit (plan row, greeting).

## 7. Trust looks calm
Verified = a small filled check-seal + the words "Verified card" in a quiet green. Unverified and
revoked are stated in words with their colour. Demo data says "Sample data" on its own line — never
glued to the verified label. Trust is shown where a decision is made (Reveal, person screen) and
only when abnormal in lists.
*Test:* would this look like an achievement unlock? If so, tone it down.

## 8. Speed is part of design
Navigation is instant (fade ≤ 150 ms, no slides between tabs). The Reveal acknowledges contact on
the first frame with a haptic and the contact ring; the name is readable by ~1 s; everything
settles by ~1.55 s. No network before first visual.
*Evidence:* R3, A4.

## 9. Native beats web for the recipient
Kipat's concrete advantages over competitors: an in-context overlay instead of a browser tab, and
**one-tap Save contact** into Android's contact editor instead of a .vcf download.
*Evidence:* B1, B2, H2.

## 10. Honest UI
Show only what works. Unbuilt capabilities are labelled "Later" and disabled, not dressed up
("Ready to share"). Platform features already covered by Android (phone-to-phone Tap to share)
are not headlined.
*Evidence:* A6, audit (Touch).
