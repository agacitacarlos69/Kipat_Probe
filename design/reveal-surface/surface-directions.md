# Reveal surface — three directions

All three use the same data and order (identity → context → trust → actions), the same tokens,
and the same components. They differ in how the identity header is composed. Heights are
computed from the specs below for the sample identity at 411 dp; the implementation is
content-driven (`wrapContentHeight`), never a fixed size.

Shared spec (all directions):

- Width: screen − 40 dp (20 dp margins), max 380 dp. Padding 24 dp (20 dp below 340 dp width).
- Close: 48 dp target in the top-end corner, 20 dp ✕ in tertiary ink, same place in every
  direction; dismiss also by scrim tap and Back.
- Trust row: 18 dp glyph + state words in one line; demo shown as a small "Sample data" chip on
  the same row; one detail line only for revoked / couldn't-verify.
- Rule: a hairline divider separates identity from actions; the actions are attached to it.
- Primary: Connect, filled accent, 48 dp, no icon (the word is enough). Connected = tonal, ✓.
- Secondary: Save contact / Open profile as icon + label text buttons on one row, never stacked
  outlined buttons.
- Avatar: photo, organisation logo (rounded square) or a designed monogram (accent tint 12%,
  initials in the accent, weight 500).

## A — Compact identity (stacked)

```
┌────────────────────────────────────┐
│ (VP)                            ✕  │  avatar 56
│                                    │
│ Vash Puno                          │  24/30 · 500
│ BS Computer Science                │  16/22 secondary
│ Polytechnic University of the …    │  14/20 secondary, ≤ 2 lines
│ ✓ Verified card       [Sample data]│
│ ────────────────────────────────── │
│ [            Connect             ] │
│  ⊕ Save contact      ↗ Open profile│
└────────────────────────────────────┘   ≈ 330 dp
```

Name gets the full width (best for long names and small phones). The identity block reads top to
bottom in one column.

## B — Profile header (row)

```
┌────────────────────────────────────┐
│ (VP)  Vash Puno                 ✕  │  avatar 56 beside a 22/28 name
│       BS Computer Science          │
│       Polytechnic University…      │
│ ✓ Verified card       [Sample data]│
│ ────────────────────────────────── │
│ [            Connect             ] │
│  ⊕ Save contact      ↗ Open profile│
└────────────────────────────────────┘   ≈ 290 dp
```

Fastest "who": face and name in one line of sight, like Android contact headers and the Google
account card. The name column is ~80 dp narrower, so long names wrap sooner.

## C — Editorial identity

```
┌────────────────────────────────────┐
│ ┌────┐                          ✕  │  avatar 72
│ │ VP │                             │
│ └────┘                             │
│ Vash Puno                          │  24/30 · 500, 20 dp below the anchor
│ BS Computer Science                │
│ Polytechnic University of the …    │
│                                    │
│ ✓ Verified card                    │
│ ────────────────────────────────── │
│ [            Connect             ] │
│  ⊕ Save contact      ↗ Open profile│
└────────────────────────────────────┘   ≈ 370 dp
```

A large anchor and more air around the name: the most distinctive, still composed (every gap is
one of 12 / 16 / 20 dp, none above 20).

## Surface treatments (material, not layout)

| Treatment | Light | Dark | Character |
|---|---|---|---|
| PAPER | `#FCFCFA`, 1 dp `#DEDED9`, 2 dp shadow | `#1C1D20`, 1 dp `#2E3035` | Neutral, almost no depth |
| TONAL | `#F7F5F0` (warmer), no border, 6 dp soft shadow | `#222326`, no border | Softer material |
| STRUCTURAL | `#FCFCFA`, 1 dp `#A9ABB2` (≥ 3:1), no shadow, radius 12, full-width rule | `#1C1D20`, 1 dp `#4A4C54`, radius 12 | Crisp, drafted edge |
| FLOATING | `#FFFFFF`, 1 dp `#E4E4E0`, 16 dp shadow | `#26272B` (raised graphite), 1 dp `#34363B` | Slightly stronger depth |

Backdrop: light `#ECECE8` at 72 → 92% (darker than every light surface, so the card separates by
tone, not by shadow alone); dark `#0B0C0E` at 62 → 88%. No translucent/glass treatment: the Reveal
sits over arbitrary apps and must stay legible; glass is banned in Kipat.
