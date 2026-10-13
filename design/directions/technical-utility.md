# Direction C — Technical Utility

**Character:** fast, precise, compact, productivity-oriented. Everything is a list row with an
immediate action; almost no decorative content. Not terminal cosplay — no monospace, no neon.

## Palette

Neutral grays (same values as Quiet Graphite) with a more saturated blue accent (#1F5BE0 light /
#8DAEFF dark) used for every interactive affordance (icons in rows, trailing actions).

## Type & shape

System sans, 14 sp body, 20 sp titles, 22 sp identity name. Radius 8 dp everywhere. Dense
56 dp rows, 40 dp avatars, 12 dp gutters.

## Home

```
│ Kipat                       ⌁  ⊞QR  ↗    │  WHY: share methods as app-bar icons (1 tap each)
│ (VP) Vash Puno                       ✎   │  WHY: identity as a single dense row + edit
│      Developer, PUP                      │
│ ─────────────────────────────────────    │
│ Card     KPT-DEMO-0001   Active      >   │  WHY: status table
│ Reveal   Signature                   >   │
│ Template Student                     >   │
│ ─────────────────────────────────────    │
│ Recent                                   │
│ (AR) Alyssa Ramos   Globe Labs  2h  ⋮    │  WHY: overflow actions per row
│ (JR) Jonas Reyes    UST         4h  +    │  WHY: inline connect
```

## Connections

Table-like rows with inline pin/remove icons and a filter chip row (All, Pinned, Unverified).

## Profile / Edit

Settings-style key/value list; each value opens a single-field editor dialog.

## Share

App-bar icons on Home do the job; a separate Share screen only shows the QR.

## Recipient Reveal

Compact bottom sheet (not centered card) sliding up over the previous app with a single row:
avatar · name · role · verified, and two actions. Under 500 ms, no trace.

## Risks

- Identity becomes one row among many — fails "identity is the hero".
- App-bar icon trio (NFC/QR/share) hides labels; poor discoverability for first-time users.
- Dialog-per-field editing is slow for first setup.
- The recipient Reveal as a generic bottom sheet loses Kipat's defining moment.
- Many accent-colored icons raise the accent share well above 10%.
