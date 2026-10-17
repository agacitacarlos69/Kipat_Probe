# Kipat product brief

For users, judges, partners, developers and designers. One page. Every status is stated honestly:
**no feature has yet been tested on a physical phone.**

## WHAT IS KIPAT

An Android app that lets you introduce yourself in person with a tap. Touch your phone to
someone's phone, or hold your Kipat card to it, and they see who you are: your name, role and
organisation, in a short "Reveal" over whatever app they had open. Then they choose whether to
connect.

> **Tap. Know who I am. Connect.**

## WHO IS IT FOR

First: **university students and student developers** who meet many people at once (org events,
hackathons, career fairs). Next: founders, early-career professionals and creators at events.
Organisations later, as a way to distribute Kipat, never as an admin system.

## MAIN ACTION

**The tap → the Reveal → Connect.** Everything else supports it.

## WHY DIFFERENT

- **The person, not a page.** The Reveal appears over the other person's app, with no browser,
  download or form. Save contact is one tap into Android Contacts.
- **One identity, many ways to arrive.** Phone, card, QR and link all show the same identity in the
  style you chose. Edit once, and every linked card follows, with no rewrite.
- **Kept by choice.** A tap never creates a connection. Only Connect does.
- **Quiet.** One expressive moment (the Reveal), and a calm app everywhere else. No feed,
  followers, likes, streaks or badges.

## CURRENT CORE FEATURES

| Feature | State |
|---|---|
| Identity: profile, six Reveal styles, accent | Built, persisted on the phone |
| Reveal over the current app, with Connect, Save contact, Open profile | Built; backdrop varies by phone maker; not device-tested |
| Kipat Tap: phone-to-phone, mutual, Kipat doesn't need to be open | Built and simulated; **physical validation required** |
| Linked Kipat card that follows your identity | Built; **works only on the phone that linked it** until a backend exists |
| Connections: search, pin, replay, "met by" | Built; **in memory only** (resets) |
| Share screen: QR, link, system share | Built; **no web profile yet**, so links lead nowhere |
| Verification | Not built. Everything shows "Unverified" |

## WHAT IS NEXT

1. **Prove the tap on real phones** (several makers, phone-to-phone and card).
2. **Make Connections real:** persistence, and *where* you met.
3. **Make the card work for everyone:** backend resolver and a web profile for people without Kipat.

Full sequence: `docs/roadmap/KIPAT_PRODUCT_ROADMAP.md`.

## WHAT WE ARE DELIBERATELY NOT BUILDING YET

- **File or photo sending ("Kipat Share").** Android's own Tap to share already sends files by
  touching phones on supported devices. Kipat will only add sharing if identity context makes it
  clearly better, and only after the tap, the card and Connections are proven (`feature-gates.md`).
- Feeds, followers, likes, messaging, discovery of strangers nearby.
- CRM, leads, team admin, badge scanning, analytics on people.
- Paid tiers and new Reveal templates.
- iPhone support.

## Narrative

**Before.** You meet someone at a hackathon. You spell your handle over the noise, they mistype
it, and a week later neither of you remembers where you met. A paper card ends up in the wash.

**With Kipat.** You touch phones, or hold out your card. Your name and what you do appear on their
screen, over the chat they had open, in a style you picked. They tap Connect. You both go back to
the conversation.

**Later.** "Who was the designer from the hackathon?" They search, find you, and see your profile.
If you've changed schools or jobs, it shows where you are now (once the backend exists). Nothing
pinged them in the meantime.
