# Demo storyboard (sound off)

A demonstration plan for a live table, a judge's demo, or a phone-recorded clip. It is not a
video production brief. **It works with the sound off**, because events are loud and social video
autoplays muted. Total about 35 s.

**Rule: the subject is two people meeting. The phone is a prop. Never say "here is our NFC technology".**

**Honesty rule:** until physical validation passes, any demo recorded from simulation, deep links
(`kipatprobe://tap/…`) or Robolectric renders must say so on screen ("Simulated"). Do not show an
on-device tap that has not happened.

## Cast and setup

- **Ana** (has Kipat, Reveal: Contact Ripple) and **Ben** (has Kipat, Reveal: Precision Orbit).
- Both phones unlocked and showing *other* apps: Ana in a chat app, Ben in a maps app.
- Ana also holds a linked Kipat card. *The card only resolves on the phone that linked it. Until
  the backend exists, demo the card on Ana's own phone and label it as such, or skip it.*
- One on-screen caption at a time, sentence case, at most 6 words.

## Shots

| # | Time | Frame | Caption (on screen) | What it proves |
|---|---|---|---|---|
| 1 | 0–4 s | Two people at a hackathon table, mid-conversation. Phones on the table showing chat and maps | "Two people meet." | Context: people first |
| 2 | 4–7 s | Close-up: Ben picks up his phone; Ana slides hers over | "They tap." | The gesture, plainly |
| 3 | 7–9 s | Phones back to back, upper halves together. Hold still for a beat | — | Physical act. No UI yet |
| 4 | 9–13 s | **Ben's screen:** contact mark at the top, a ring uncovers Ana's surface **over the maps app**. Name readable | "Ben sees who Ana is." | **Identity appears over his own app** |
| 5 | 13–16 s | **Ana's screen:** Ben's Precision Orbit settles over her chat | "Ana sees Ben." | Mutual, and each person has their own style |
| 6 | 16–20 s | Ben's thumb on **Connect**; it settles to "Connected" | "He chooses to connect." | Intentional; nothing automatic |
| 7 | 20–23 s | Ben dismisses with ✕; the maps app is exactly where he left it | "Back to what he was doing." | The Reveal is an interruption that respects you |
| 8 | 23–28 s | *(optional, labelled)* Ana hands her card to a third person, Cara, whose phone shows a browser or web profile **only if one exists**. Otherwise cut | "No phone handy? The card works too." | Card as the carrier for non-users |
| 9 | 28–33 s | Later, at home: Ben opens Connections, searches "hack", finds Ana and taps Replay | "Later, he remembers where they met." | Connections: the "after" |
| 10 | 33–35 s | Kipat mark on a plain background | "Introduce yourself with a tap." | One line, no feature list |

## What not to show

- Protocol diagrams, logcat, "HCE", "APDU", "NFC" as the hero
- All six Reveal templates in a montage (it reads as an effects demo). Show at most two: Ana's and Ben's
- Settings, Developer tools, the Reveal style picker
- Any file-sending UI (not built, not on the roadmap for the demo)
- Fake numbers ("10,000 connections made")
- A "Verified" label on anything that is not verified

## Live-table variant (no recording)

1. Put two phones on the table with other apps open.
2. Ask the visitor to hold one phone to the other.
3. Say nothing until the Reveal appears. Then: "That's me. Want to connect?"
4. If the tap fails, try once more, then show a **labelled** deep-link Reveal and say plainly that
   the hardware tap is still being validated. Never fake it.
