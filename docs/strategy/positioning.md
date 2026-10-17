# Positioning

## CATEGORY

The category decides who Kipat is compared with. Candidates tested:

| Candidate | What buyers compare | Fit | Verdict |
|---|---|---|---|
| NFC business cards | Card price, chip, number of links, CRM export (Blinq, Popl, V1CE, …) | Describes the card, misses the phone and Connections | **Reject.** Commodity, and it makes NFC the story |
| Digital business cards | Templates, fields, team admin (HiHello, Blinq) | Misses the Reveal and the in-person moment | Reject. Professional-only and crowded |
| Contact exchange | NameDrop, Android Tap to share (free, built in) | Accurate for the touch, fatal for value | **Reject.** The platform already owns it |
| Networking app | LinkedIn, Bumble Bizz | Implies a graph, a feed and discovery | Reject. Kipat refuses these |
| **Personal identity exchange** | Nothing exactly. It forces the question "what's the identity?" | Accurate, but it sounds like government ID or KYC | Use internally; too abstract for users |
| **In-person introductions** | Paper cards, saying your name twice, typing a handle | Describes the moment and the job | **Recommended (external)** |

**Recommendation:** internally, *personal identity exchange*. Externally, *how you introduce
yourself in person*. Never lead with NFC.

## TARGET USERS

Primary: university students and early-career developers in the Philippines who meet people in
bursts (hackathons, org events, career fairs, conferences). Secondary: professionals and founders
at events. Detail: `audience-prioritization.md`.

## PROBLEM

Meeting someone in person still ends with one of three bad options: spell your name and handle
while they type it, hand over a paper card that ends up in a pocket, or show a QR that pulls them
into a browser or another app. Afterwards, nobody remembers *where* they met whom, and the
saved contact is out of date the next time they need it.

## INSIGHT

People don't want to exchange **data**. They want the other person to **know who they are** in the
moment, and to be **findable** afterwards. The touch itself is no longer special: Android and iOS
now exchange contact fields by touching phones. What is still missing is a chosen, presented
identity, and a reason to keep it.

## PROMISE

**Tap, and the person in front of you sees who you are, then chooses to keep you.**

## REASONS TO BELIEVE

Each is marked with its real state.

1. The Reveal opens **over the other person's current app**, with no browser or download. *Built; backdrop varies by OEM; not device-tested.*
2. **Save contact in one tap** into Android Contacts, with no .vcf. *Built.*
3. **One identity everywhere**: edit once, and every phone tap and linked card serves it with no rewrite. *Built locally; global needs a backend.*
4. **Never auto-connects.** Connections are chosen. *Built.*
5. **Works offline**, phone to phone. *Built; simulated only.*
6. **Honest trust labels**: unverified things say "Unverified". *Built.*

## DIFFERENTIATION

| Versus | Kipat is different because |
|---|---|
| Card vendors | The recipient sees a person over their app, not a web page. The phone works with no accessory |
| NameDrop / Tap to share | A presented identity in the owner's chosen style, and a Connection with context, not just contact fields. Works with a card when phones can't |
| LinkedIn | No feed, no graph, no discovery. A memory aid, not a social network |
| Quick Share / AirDrop | Kipat is about *who*, not *what*. It does not compete on file transfer |

## WHAT KIPAT IS NOT

Not NFC business cards. Not technical networking. Not contact management or a CRM. Not a
file-transfer utility. Not a social network. Not an ID or verification service (yet, and only with
real cryptography).

## One-line descriptions, scored

1–5, higher is better. Scored by the strategy author against the criteria; this is not user data.
**Student** and **Pro** mean appeal to that audience.

| # | Line | Clarity | Memorable | Distinct | Credible | Student | Pro | Total |
|---|---|---|---|---|---|---|---|---|
| 1 | Tap your phone or card, and the other person sees who you are. | 5 | 3 | 4 | 5 | 4 | 4 | **25** |
| 2 | Introduce yourself with a tap. | 5 | 5 | 3 | 5 | 4 | 4 | **26** |
| 3 | One identity, many ways to connect. | 3 | 3 | 2 | 4 | 3 | 4 | 19 |
| 4 | Your identity, one tap away. | 3 | 3 | 2 | 3 | 3 | 3 | 17 |
| 5 | Meet someone. Tap. Keep them. | 4 | 4 | 4 | 4 | 4 | 3 | 23 |
| 6 | The way you introduce yourself in person. | 4 | 3 | 3 | 4 | 3 | 4 | 21 |
| 7 | NFC business cards that update themselves. | 4 | 2 | 1 | 4 | 2 | 3 | 16 |
| 8 | Show who you are without handing over your phone. | 4 | 3 | 4 | 4 | 3 | 4 | 22 |
| 9 | Tap to show who you are. They choose to connect. | 5 | 3 | 4 | 5 | 4 | 4 | **25** |
| 10 | The introduction, not the contact card. | 3 | 4 | 4 | 3 | 3 | 4 | 21 |
| 11 | Be met the way you want to be met. | 3 | 4 | 4 | 3 | 4 | 3 | 21 |
| 12 | Touch phones. See who. Stay in touch. | 3 | 3 | 3 | 4 | 4 | 2 | 19 |

**Top 3:** #2 "Introduce yourself with a tap." (headline), #1 "Tap your phone or card, and the other
person sees who you are." (explainer), #9 "Tap to show who you are. They choose to connect." (the
consent story, for judges and partners).

Rejected language: "revolutionise networking", "seamless", "next-generation", "smart", "unlock",
"elevate", "AI-powered". None of it describes what happens.

## Tagline test

The existing in-app promise is **"Tap. Know who I am. Connect."** It is spoken by the owner, as if
the card itself were speaking. That is a strong idea and should be kept, whatever the wording.

| Tagline | English | Filipino / Taglish | Meaning | Risk | Verdict |
|---|---|---|---|---|---|
| **Tap. Know who I am. Connect.** (existing) | Clear. Three beats, but the middle is long | Natural in Taglish speech ("para ma-know mo kung sino ako" style code-switching) | Identity first, explicitly | Echoes "Do you know who I am?", the idiom of entitlement. The three-beat rhythm softens it | **Keep for now** |
| Tap. Know me. Connect. | Short, but "know me" reads as personal or intimate ("get to know me"), a dating-app register | "Know me" is understood. The Filipino equivalent "Kilalanin mo ako" is earnest and heavy | Loses *who*, the identity part | Too personal for a recruiter or professor. Mild dating-app connotation | **Do not adopt** |
| Tap. Meet. Connect. | Natural, but generic | Natural | "Meet" and "Connect" overlap, so nothing says *identity* | Indistinguishable from any networking app; "Meet" evokes Google Meet | Reject |
| Tap. Know. Connect. | Clipped: know what? | Unnatural | Abstract | Reads like a slogan generator | Reject |
| Tap. This is me. Connect. | Natural, warm, not entitled | "This is me" is common in Taglish | Identity first, owner's voice | Slightly emotional; possible song association | **Test** |
| Introduce yourself with a tap. | Natural, instructional | Natural | The job, plainly | Less ownable as a mark | Use as the description line, not the tagline |

**Recommendation:** do not change branding casually. **Keep "Tap. Know who I am. Connect."** in
the product. **Do not adopt "Tap. Know me. Connect.":** it is too personal for professional
contexts and drops the *who*. Before any external launch, run a hallway test on the existing
tagline versus "Tap. This is me. Connect.": students and professionals, English and Taglish
speakers, after watching the demo storyboard. Ask them to repeat it back from memory after one
minute and say what the product does. Keep the existing line unless the alternative is clearly
better on both. Pair the tagline with the plain description "Introduce yourself with a tap."
