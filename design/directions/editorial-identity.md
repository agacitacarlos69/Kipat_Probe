# Direction B — Editorial Identity

**Character:** identity-focused, human, refined, slightly warmer, professional.
**One-line idea:** the app reads like a well-typeset personal page — "this is my identity", not
"this is my dashboard". Boundaries almost disappear; rhythm comes from type and whitespace.

## Palette

| Token | Light | Dark |
|---|---|---|
| background | #F7F5F1 (warm neutral, *not* cream #F4F1EA) | #161514 |
| surface | #FFFFFF | #1E1D1B |
| textPrimary | #1B1A18 (ink) | #EFEDEA |
| textSecondary | #57544F | #ADA9A3 |
| accent | #3552B5 (desaturated blue) | #A5B4F0 |

## Type & shape

Still system sans for UI, but the identity name is set very large (34 sp, 400 weight, tight
leading) and left-aligned with generous whitespace. Optional serif for the name only was
considered and **rejected**: cream + serif display is the most common AI-generated look
(frontend-design calibration, trait 1) and adds a font file for little gain. Radius 8–12 dp.

## Home

```
│ Kipat                                    │  WHY: wordmark only
│                                          │
│                                          │  WHY: deliberate whitespace above identity
│ Vash                                     │  WHY: name broken over two lines as the page's
│ Puno                                     │  typographic event (34 sp)
│                                          │
│ BS Computer Science                      │
│ Polytechnic University of the Philippines│
│ Verified card                            │  WHY: plain words, verified colour
│                                          │
│ Share my Kipat →  (text-style action)    │  WHY: editorial voice; primary but quiet
│                                          │
│ People you met                           │  WHY: human phrasing instead of "Recent"
│ Alyssa Ramos — UX Designer, Globe Labs   │  WHY: single-line entries like an index
│ Miguel Santos — BS IT, UP                │
```

## Connections

```
│ People                                   │
│ ⌕ Search                                 │
│ A                                        │  WHY: alphabetical index headings
│ Alyssa Ramos                 Globe Labs  │
│ B                                        │
│ Bea Villanueva                     Maya  │
```

## Profile / Edit

Profile is a full-bleed "page": name large, lines below, links as plain text list. Edit opens
the same layout with each line becoming an inline field (in-place editing).

## Share

Large QR on the page with the name above it; "Copy link" and "Share" as text actions.

## Recipient Reveal

Card is a light, paper-like surface; name set at 30 sp across two lines; actions as text buttons
"Connect", "Save contact". Motion is a slow fade (≈ 600 ms) without a trace.

## Risks

- Editorial devices (em-dash index lines, `Share →` arrows) are themselves AI tells
  (frontend-design calibration, trait 5) — would need care to avoid.
- Low density: Connections becomes slow to scan past ~30 people.
- Text-styled primary action reduces discoverability of the main job (sharing).
- The Reveal's quiet fade loses the "physical contact" moment that differentiates Kipat.
