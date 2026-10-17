# Card colour

**FINAL PRINT COLOUR MUST BE PROOFED WITH THE VENDOR.** The values below are **digital references**
(sRGB hex), taken from the app's Quiet Graphite tokens where they exist. They are not CMYK or Pantone
values. Final separations depend on the vendor's press, stock, laminate and ICC profile, so this
document deliberately does not invent CMYK builds or Pantone numbers. The print intent column says what
the colour must achieve, so the vendor can match the intent rather than the number.

## Principles

- **1–3 inks plus the base.** No gradients, no metallic inks, no fluorescent inks. A flood counts as one ink.
- **Cobalt is the tap colour.** It appears in exactly one place, the contact point, as in the app (the
  accent marks the act of sharing and the moment of contact). Mono uses no cobalt.
- **Material-named neutrals** (graphite, stone, charcoal, paper): muted and desaturated, as in N26's
  mineral palette. Never a blue-tinted near-black.
- **Two dark, two light, one material-led, one neutral** across the six editions, so Kipat doesn't
  become "the black NFC card brand".

## Palette

| Name | Hex (reference) | Print intent | Used by |
|---|---|---|---|
| Kipat graphite ink | #17181C | Neutral rich black text, never a 4-colour registration black on small type (use 100K or a vendor rich-black recipe for floods only) | Text and lines on light stock; QR |
| Secondary ink | #565961 | Neutral mid grey, ≥ 4.5:1 on its stock | Context lines on light stock |
| Kipat cobalt | #2747C8 | Saturated, slightly warm ultramarine ink; match against the app's accent on a calibrated screen *and* a printed swatch | Contact point on light stock |
| Cobalt on dark | #5470E6 | Lifted cobalt that holds on a dark flood (no white underbase needed on white-core PVC: the flood knocks out) | Contact point on Graphite, Layered Depth |
| Graphite | #26272A | Neutral deep grey, **not** blue-black; matte or soft-touch kills sheen | Graphite Precision base |
| Graphite line | #63656B | One tonal step above graphite | Precision orbit |
| Soft white | #E8E8E4 | Warm-neutral off-white, never pure white on dark | Text on dark stock; QR patch |
| Soft white secondary | #A2A4A6 | ≥ 4.5:1 on graphite | Context lines on dark stock |
| Paper | #F6F6F2 | Warm-neutral white flood; looks like good paper under matte laminate | Architectural Line base |
| White | #FAFAF7 | Near-white flood (or bare white PVC core plus matte laminate) | Essential Mono |
| Stone | #D7D5CE | Light warm grey; mineral, not beige | Aperture base |
| Blade | #2B2C30 | Graphite, flat | Aperture segments |
| Charcoal | #2D2C29 | Warm deep grey | Layered Depth base |
| Plane | #413F3A | One tonal step above charcoal (depth comes from deboss and gloss) | Layered Depth plane |
| Soft black | #202020 | Neutral, not tinted | Essential Mono, dark |
| Frost | #E6E9E8 (simulated) | The stock itself: frosted translucent PVC. Nothing printed as a flood | Frosted Contact |
| White ink | #FFFFFF | Opaque white underbase | Frosted Contact |

Plate key colours in the SVGs, which are never printed as colour:
- `SPOT_GLOSS` magenta #FF00FF
- `DEBOSS` cyan #00AEEF

Vendors usually want masks as 100% black: recolour on export.

## Inks per edition

| Edition | Base | Inks | Count |
|---|---|---|---|
| Graphite Precision | Graphite flood | Soft white, graphite line, cobalt-on-dark | 3 + base |
| Frosted Contact | Frosted stock (no flood) | White ink, graphite, cobalt | 3 |
| Architectural Line | Paper flood | Graphite, cobalt | 2 + base |
| Aperture | Stone flood | Graphite (text and blades), cobalt | 2 + base |
| Layered Depth | Charcoal flood | Plane tone, soft white, cobalt-on-dark | 3 + base |
| Essential Mono | White | Graphite | 1 + base |
| Essential Mono, dark | Soft black flood | Soft white | 1 + base |

Every edition can also be printed process CMYK on white-core PVC with no spot inks. The counts above
are the design's discipline, not a press requirement.

## Measured contrast (digital references, WCAG formula)

These numbers are a sanity check only; the printed proof decides.

| Edition | Name / ink | Secondary | Contact point | Motif |
|---|---|---|---|---|
| Graphite Precision | 12.16 | 5.97 | 3.44 | 2.56 (orbit, a deliberately quiet tonal line) |
| Frosted Contact | 14.52 | 5.73 | 6.09 | white ink on frost (material contrast) |
| Architectural Line | 16.37 | 6.47 | 6.87 | 16.37 |
| Aperture | 12.08 | 5.59 | 5.07 | 9.50 |
| Layered Depth | 11.37 | 5.74 | 3.22 | 1.33 (plane, a tonal step; depth comes from the finish) |
| Essential Mono | 16.96 | 6.70 | 16.96 | — |
| Essential Mono, dark | 13.26 | 6.52 | 13.26 | — |
| QR ink on soft-white patch | 14.44 | | | |
| QR ink on stone | 12.08 | | | |

All text pairs are ≥ 4.5:1. The contact point is a non-text mark and is ≥ 3:1 everywhere.
