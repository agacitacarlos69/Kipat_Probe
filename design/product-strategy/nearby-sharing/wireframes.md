# Nearby sharing: wireframes (low fidelity)

Concept wireframes for a feature that is **not built and gated** (`docs/product/feature-gates.md`).
They follow `.claude/skills/kipat-product-design/SKILL.md` (Quiet Graphite: one filled button,
sentence case, no middle-dot meta strings, role and organisation on separate lines, trust in words,
light icon + label row, ✕ top-end) and the frontend-design guidance (spend boldness in one place,
progressive disclosure, words that say exactly what happens, no "→" appended to labels).

Reveal surface constraints (`docs/reveal.md`): width screen − 40 dp (max 380), **content height
≈ 280–370 dp**, padding 24. Heights below are layout estimates from those tokens, not measurements.

Notation: `[ Connect ]` = the one filled button. `◦ Label` = light icon + label action.

## The concept in the brief, critiqued

```
┌──────────────────────────────────┐
│ (VP)                          ✕  │
│ Vash Puno                        │
│ Developer, PUP                   │
│ Nearby Kipat                     │
│ [            Connect           ] │
│ ◦ Save contact   ◦ Open profile  │
│ ──────────────────────────────── │
│ Share something →                │
│ ◦ File  ◦ Photo  ◦ Link  ◦ Contact│
└──────────────────────────────────┘
```

| Problem | Why it matters | Fix |
|---|---|---|
| **Height.** It adds a rule, a heading row and a 4-item row: roughly +100 dp on a surface that is already ~310–340 dp. That gives ~420–440 dp, past the 370 dp ceiling | On a 640 dp phone the surface covers most of the screen and hides the backdrop, which is the point of an over-your-app Reveal. At 1.3× font it scrolls | Nothing below the action row on the first state |
| **Toolbox on the first frame.** Seven tappable things before the person has decided anything | Breaks identity first. The eye goes to the icon row | Progressive disclosure (options A–C) |
| **Sharing before Connect** | Lets you push content to someone you haven't chosen to keep, and invites spam | Send only after Connect, or from a Person |
| **"Share" collides** with Home's **Share** (your identity) | Two meanings of one verb | Use **Send** for content |
| **"Contact" is redundant.** A phone tap already exchanged both profiles | A dead option | Remove from the Reveal (flow E) |
| **"Developer, PUP"** on one line, with the organisation as an abbreviation | The Reveal order is role, then organisation on a quieter line. Outsiders can't read "PUP" | `Developer` / `Polytechnic University of the Philippines` |
| **"Nearby Kipat" alone** | Trust must be stated in words | `Unverified Kipat` + `Nearby Kipat` on the trust row |
| **The "→" in "Share something →"** | A generic-UI tell; the label should say the action | `Send to Vash` |
| **The rule splits actions into two tiers** | Two competing action groups | One action row |

## Option A: Reveal + a small "Send" action

First state: identical to today, plus one light action in the existing row.

```
┌──────────────────────────────────┐
│ (VP)                          ✕  │
│ Vash Puno                        │
│ Developer                        │
│ Polytechnic University of the    │
│ Philippines                      │
│ ⓘ Unverified Kipat               │
│   Nearby Kipat                   │
│ ──────────────────────────────── │
│ [            Connect           ] │
│ ◦ Save contact  ◦ Open profile   │
│ ◦ Send                           │   ← one quiet extra row (+48 dp)
└──────────────────────────────────┘
```
Tap **Send** → the action area is replaced in place (same surface, no sheet):
```
│ ──────────────────────────────── │
│ Send to Vash                     │
│ ◦ Photo        ◦ File            │
│ Cancel                           │
└──────────────────────────────────┘
```
- + Fast: send is reachable before Connect, two taps to the system picker.
- − It is still visible on the first frame (an invitation to treat the Reveal as a tool), it allows
  sending before choosing to keep the person, and it adds ~48 dp to every Reveal, including those of people who never send.

## Option B: Reveal, then a post-connect action (recommended)

First state: **exactly today's Reveal.** Nothing new.

```
┌──────────────────────────────────┐
│ (VP)                          ✕  │
│ Vash Puno                        │
│ Developer                        │
│ Polytechnic University of the    │
│ Philippines                      │
│ ⓘ Unverified Kipat               │
│   Nearby Kipat                   │
│ ──────────────────────────────── │
│ [            Connect           ] │
│ ◦ Save contact  ◦ Open profile   │
└──────────────────────────────────┘
```
After **Connect**: the button settles to the "Connected" accent tint, and *one* text action appears
below the row (+48 dp, only for someone you chose to keep):
```
│ ──────────────────────────────── │
│   Connected                      │   ← accent tint, not filled
│ ◦ Save contact  ◦ Open profile   │
│ ◦ Send to Vash                   │
└──────────────────────────────────┘
```
Tap **Send to Vash** → a bottom sheet (radius 20, two rows, no icons grid):
```
┌──────────────────────────────────┐
│ Send to Vash                     │
│ ◦ Photo                          │   → system photo picker (one item)
│ ◦ File                           │   → system document picker (one item)
│                                  │
│ Vash will be asked to accept.    │
└──────────────────────────────────┘
```
Link and contact are **not here**. They live on the Person screen via the Sharesheet (S0).
- + The first frame is untouched. Send exists only for people you chose. "Vash will be asked to accept" sets expectations about consent.
- − One more tap than A. Discovery depends on Connect, which is intended.

## Option C: Reveal + a compact contextual action row

The light row becomes a fixed slot of **up to three** icon + label actions, chosen by context:

```
before Connect:            after Connect:              on your own preview:
◦ Save contact             ◦ Save contact              ◦ Edit profile
◦ Open profile             ◦ Open profile              ◦ Reveal style
                           ◦ Send
```
```
┌──────────────────────────────────┐
│ ...identity as above...          │
│ ──────────────────────────────── │
│   Connected                      │
│ ◦ Save contact ◦ Open profile ◦ Send │  ← three items on one row; wraps at large font
└──────────────────────────────────┘
```
Tap **Send** → the same two-row sheet as option B.
- + No height change, and the pattern scales to future contexts.
- − Three labelled items at 320 dp width and 1.3× font wrap to two lines. A contextual row
  changes under the thumb right after Connect (a moving target). It invites "just one more slot"
  later, which is the slow road to a toolbox.

## Consent sheet (receiver; same for A–C)

```
┌──────────────────────────────────┐
│ (BR)  Ben Ramos                  │
│       Backend developer          │
│       ⓘ Unverified Kipat         │
│ ──────────────────────────────── │
│ Ben wants to send                │
│ design.pdf                       │
│ 2.4 MB, PDF document             │
│                                  │
│ Decline              [  Accept ] │
└──────────────────────────────────┘
```
No thumbnail, no auto-open. After Accept: "Saved to Downloads" with **Open** as a text button.

## Never on the first state

Six share icons, a send grid, settings, history, pickers, a "recent files" strip, a QR,
counters, or any second filled button.
