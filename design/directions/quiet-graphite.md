# Direction A — Quiet Graphite

**Character:** professional, minimal, precise, fast, neutral, confident.
**One-line idea:** the shell is a well-set page of type on a neutral ground; the only colour is
Kipat's cobalt "ink", spent on the share action and on the moment of contact.

Influences at the level of principle, not look: Linear ("don't compete for attention you haven't
earned", structure felt not seen), Raycast (one dominant action, the rest contextual), Material 3
(top app bars, list items, nav bar for 3 destinations, 48 dp targets).

## Palette

| Token | Light | Dark |
|---|---|---|
| background | #F6F6F4 | #131416 |
| surface | #FFFFFF | #1A1B1E |
| surfaceSecondary | #EEEEEB | #212226 |
| textPrimary | #17181C | #EDEDEF |
| textSecondary | #4E525B | #A9ACB3 |
| accent (cobalt ink) | #2747C8 | #9DB0FF |
| verified | #16794A | #5BC48C |

Neutral ≈ 95% of pixels. Accent on: Share button, selected tab, links, Reveal contact line.

## Type & shape

System sans (Roboto/Roboto Flex). Two weights: 400 and 500. Identity name 28 sp/500 is the only
large type in the shell. Radius 12 dp for buttons and the few surfaces; 16 dp for sheets/Reveal
card; pills only for chips. No elevation in the shell; dividers 1 dp.

## Home

```
┌──────────────────────────────────────────┐
│ (•)) Kipat                               │  WHY: small app bar = orientation only; the brand
│                                          │  mark is the one place the logo appears in the shell
│ ( VP )                                   │  WHY: avatar anchors the identity; 64 dp, no ring
│ Vash Puno                                │  WHY: the hero is the identity itself, set as type
│ BS Computer Science                      │  WHY: template-driven subtitle
│ Polytechnic University of the Philippines│  WHY: organisation, second line, secondary colour
│ ✓ Verified card · sample data            │  WHY: trust shown calmly as icon + words
│                                          │
│ [        Share        ]  [ Preview ]     │  WHY: THE primary job; preview = see the Reveal
│                                          │
│ Kipat Card                               │  WHY: section heading only because a list follows
│ ⌁ KPT-DEMO-0001                 Active > │  WHY: one row tells if the physical card works
│ ──────────────────────────────────────── │
│ Recent                                   │
│ (AR) Alyssa Ramos                    2h  │  WHY: last people met; tap → their profile
│      UX Designer, Globe Labs             │
│ (JR) Jonas Reyes                     4h  │  WHY: an unsaved tap is actionable
│      Tapped, not connected     Connect   │
│ See all connections                      │
├──────────────────────────────────────────┤
│  ⌂ Home      ⚇ Connections    ◯ Profile  │  WHY: 3 destinations (Touch tab removed)
└──────────────────────────────────────────┘
```

(The "·" above is wireframe shorthand; in UI the sample-data note is a separate tertiary line.)

## Connections

```
│ Connections                          ⋮   │  WHY: ⋮ holds Sort (rarely changed)
│ [⌕ Search people                    ]    │  WHY: search first; 12 dp field, not a pill
│ Pinned                                   │
│ (AR) Alyssa Ramos ⓥ  ⚲              2h   │  WHY: verified mark small; pin glyph quiet
│      UX Designer, Globe Labs             │
│ Recent                                   │
│ (MS) Miguel Santos ⓥ                5h   │
│      BS Information Technology, UP       │
│ (BV) Bea Villanueva                 1d   │
│      Product Manager, Maya               │
│      Unverified card                     │  WHY: trust shown only when not normal
│ (RL) Rafael Lim                     1w   │
│      Card revoked                        │  WHY: negative state in revoked colour + words
```
Row tap → person screen. Long-press → menu: Pin, Remove.

## Person (connection) screen

```
│ ←                                    ⋮   │  WHY: ⋮ = Pin / Remove
│ ( AR )                                   │
│ Alyssa Ramos                             │
│ UX Designer                              │
│ Globe Labs                               │
│ ✓ Verified card                          │
│ [ Save contact ]  [ Replay Reveal ]      │  WHY: the two things you do with a person
│ Met                                      │
│ Kipat Card tap, 2 hours ago              │  WHY: connection source as plain fact
│ Card  KPT-DEMO-0102                      │
```

## Profile (view) / Edit

```
│ Profile                         Edit     │  WHY: Edit is a text action in the bar
│ How others see you                       │  WHY: states what the preview is
│ ┌──────────────────────────────────────┐ │  WHY: the ONLY card in the shell: it previews
│ │ ( VP )  Vash Puno                    │ │  the physical/Reveal card the recipient sees
│ │         BS Computer Science          │ │
│ │         Polytechnic University …     │ │
│ │ ✓ Verified card                      │ │
│ └──────────────────────────────────────┘ │
│ [ Preview Reveal ]                       │
│ Reveal style            Signature    >   │
│ Profile template        Student      >   │
│ Links                                    │
│ GitHub                  vashpuno         │
│ Developer tools                      >   │  WHY: demoted, last
```
```
│ ✕  Edit profile                  Save    │  WHY: full-screen edit, M3 pattern
│ Profile template        Student      >   │
│ Name                                     │
│ [Vash Puno                          ]    │
│ Bio                                      │
│ [Building Kipat — identity in a tap.]    │
│ Education                                │
│ [University …] [Program …]               │
│ Links                                    │
│ [GitHub] [LinkedIn] [Website] [Email]    │
```

## Share

```
│ ←  Share                                 │
│        ┌───────────────┐                 │  WHY: QR is how non-NFC phones receive
│        │   ▓▓ QR ▓▓    │                 │
│        └───────────────┘                 │
│   Scan to open Vash's Kipat              │
│ ⧉ Copy link        kipat.app/c/KPT-…     │
│ ↗ Share link…                            │  WHY: Android sharesheet
│ ⌁ Kipat Card       Hold your card to     │  WHY: the physical path, with its state
│                    their phone · Active  │
│ ◌ Phone tap        Coming later          │  WHY: honest about HCE
```

## Recipient Reveal

```
│ (dimmed previous app — real, not drawn)  │
│              ◠  contact ring             │  WHY: acknowledges the tap at the antenna point
│ ┌──────────────────────────────────────┐ │  WHY: opaque card = legible over any app
│ │ ( VP )                         ✕     │ │
│ │ Vash Puno                            │ │
│ │ BS Computer Science                  │ │
│ │ Polytechnic University of the Phil…  │ │
│ │ ✓ Verified card                      │ │
│ │ [            Connect             ]   │ │
│ │ [ Save contact ]  [ Open profile ]   │ │
│ └──────────────────────────────────────┘ │
```
Motion: contact ring 0–150 ms → scrim deepens → cobalt hairline traces the card edge from the
top centre outward → surface resolves (opacity + 0.98→1) → avatar → name → lines → trust →
actions; settled ≈ 1.55 s. No particles, no glow.

## Risks

Can read as "generic clean SaaS" if typography and spacing are sloppy — distinctiveness must come
from the identity typesetting, the contact line and the Reveal, not decoration.
