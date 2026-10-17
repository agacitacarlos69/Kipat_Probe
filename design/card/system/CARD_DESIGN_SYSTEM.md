# Kipat card design system

This is the source of truth for every physical Kipat card. The artwork is built by
`design/card/tools/build.py`. That builder is deterministic: every shape is geometry, every word is
Inter shaped with HarfBuzz and outlined with fontTools, and every QR code is a real segno symbol.
No image model touches production artwork.

Companion documents:
- [typography.md](typography.md)
- [color.md](color.md)
- [finishes.md](finishes.md)
- [../production/PRINT_QA.md](../production/PRINT_QA.md)
- [../research/reference-matrix.md](../research/reference-matrix.md)

## The idea

> **One contact point, many characters.** Every Kipat card has a single 2.4 mm contact point at the
> same place: three-quarters across and vertically centred. Each edition adds exactly one idea
> around that point: an orbit, a ripple, a line, an aperture or a plane. Mono adds nothing.

The point is functional, because it marks where to tap (precedents: Teenage Engineering's antenna line, Linq's tap square). It is also the Kipat mark's own centre dot, and it is where every Kipat Reveal begins. Physical contact leads to a digital result, and it all starts from this one point.

## Format

| | |
|---|---|
| Size | ISO/IEC 7810 ID-1 (CR80), **85.60 × 53.98 mm**, 0.76 mm PVC |
| Corner radius | 3.18 mm (the die cuts it; artwork bleeds square) |
| Working bleed | 3 mm on every side (canvas 91.60 × 59.98 mm). **Final bleed must follow the selected vendor's template.** Vendors range from 2 mm (Worldline) to 3.2 mm (Plastic Printers). |
| Safe zone | 4 mm inside the trim: nothing important outside it. This is 1 mm more than Prontaprint's 3 mm because the 3.18 mm corners bite into the edge. |
| Content margin | 6 mm. The lockup, the name, the tap row and the QR all sit on this line. |
| Contact point | Centre at **x 64.20, y 26.99 mm** from the trim's top-left corner, Ø 2.4 mm, front only. It never moves. |
| Orientation | Landscape on both sides. Both sides share the same top; the card flips on its long edge. |

### Grid

```
x: 0      6                                            64.2           79.6  85.6
   ┌──────┬────────────────────────────────────────────┬──────────────┬─────┐ y 0
   │      ┊ (•)) kipat                                 ┊              ┊     │ 6  lockup baseline on the margin
   │      ┊                                            ┊              ┊     │
   │ ┄┄┄┄┄┼┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄●┄┄┄┄┄┄┄┄┄┄┄┄┄┄┼┄┄┄┄┄│ 26.99  contact axis
   │      ┊                                            ┊              ┊     │
   │      ┊                                            ┊              ┊     │ 47.98 margin
   └──────┴────────────────────────────────────────────┴──────────────┴─────┘ 53.98
```

## Logo

- Kipat has **no final logo yet**. The card uses the app's mark (`KipatIcons.Mark`: a contact point with
  two arcs) plus the word *kipat* typeset in Inter Medium. This is a lockup, not a new logo, and it is
  replaced wherever a final logo arrives.
- **Lockup:** the mark at 1 em high, then a 0.3 em gap, then *kipat* at 9 pt with −0.02 em tracking.
- **Front position:** top-left, on the 6 mm margin. It is the same on every edition.
- **Minimum size:** 9 pt wordmark (3.2 mm mark). The mark's stroke is then 0.26 mm: never smaller,
  never in a finish layer.
- **Clear space:** one mark-height on every side. Nothing else is in the top-left 25 × 12 mm on the front.
- Never: a large K, a glowing mark, a mark inside a badge, or the mark as the contact point.

## Front hierarchy

1. The edition's one idea, built around the contact point.
2. The contact point.
3. The lockup (small, quiet).

Nothing else on a front. This means no email, phone number, URL, QR, social icons, NFC icon, slogan,
chip-like rectangle, hologram, embossed digits or tier word. The only optional element is a **monogram**
on Essential Mono (personalised edition).

## Back architecture (identical on every edition)

```
┌──────────────────────────────────────────────────────────┐
│  Name                                 11 pt Medium        │
│  Role or program                      8 pt Regular        │
│  Organisation                         8 pt Regular        │
│                                                ┌──────┐   │
│                                                │  QR  │   │ 16 mm symbol
│                                                │      │   │ + 4-module quiet zone
│  (•)) Tap to connect                  8 pt Med └──────┘   │
└──────────────────────────────────────────────────────────┘
```

- The back is where utility lives and where editions converge. Spacing, type, QR position and the
  tap row never change; only the stock and the inks change.
- **Name:** 11 pt. Long names step down to 10 pt, then 9 pt, then wrap at a word. Tested with
  "PUP Microsoft Student Community".
- **Context:** up to two lines (role or program, then organisation). A creator may have only one.
- **Tap row:** the Kipat mark is the tap cue, followed by "Tap to connect". There is no generic NFC glyph.
- **QR (optional):** graphite on light. Dark and frosted stock get a light quiet-zone patch with 0.8 mm
  radius. It is never recoloured, stylised or given a logo in the middle. See [QR rules](#qr).
- **Generic back (inventory editions):** the lockup takes the name's place and the tap row stays. No
  QR is printed, because the card id is written by the app at link time (see the personalisation section below).

## Tap affordance

- **Front:** the contact point. It is small, deliberate and the same on every card, and it marks where to tap.
- **Back:** the mark plus "Tap to connect", the only instruction on the card.
- The real antenna is the inlay, usually a coil covering most of the card. The point is a cue, not a
  coordinate for the coil; any tap near it reads. If the chosen inlay's coil turns out to sit
  elsewhere, the contact point **stays**: it must still be inside the coil footprint, so choose the inlay accordingly.

## QR

| Rule | Value |
|---|---|
| Content | `https://kipat.app/c/<card id>` (the same route the NFC chip holds) |
| Symbol | 16 × 16 mm; version 3 at 29 modules for a `kc_` URL, so 0.55 mm per module; error correction M |
| Quiet zone | 4 modules (2.2 mm), always light |
| Contrast | Graphite #17181C on paper or a soft-white patch; never reversed |
| Placement | Bottom-right, outer edges on the 6 mm margin lines |
| Design files | Every SVG carries the **placeholder** `https://kipat.app/c/kc_0000000000000000` (`data-placeholder="true"`). It is replaced per card by variable-data printing. |
| Check | `build.py` decodes every back proof at five scales with OpenCV. Physical scans with at least three phones are in PRINT_QA. |

The QR is a fallback for phones without NFC or Kipat. Without a web profile it opens kipat.app in a
browser. Until the web profile exists, a printed QR is optional; ship it only when the web profile
does, or accept the gap and say so.

## NFC material safety zone

Default: **the whole card**. The inlay vendor has not been chosen, so the antenna geometry is unknown,
and the artwork never guesses it. The `NFC_SAFETY_ZONE` layer in every SVG is a configurable
rectangle; replace it with the vendor's inlay drawing once one exists.

Inside the zone, none of these unless the vendor certifies the build and a printed sample passes the NFC QA:
- metallic foil
- metal plate or metal core
- conductive or metallic ink

Allowed everywhere: CMYK, white ink, spot or raised spot gloss, soft-touch and matte laminates, and
frosted stock. None of these is conductive.

No visible NFC graphic sits in the zone. The contact point is the only tap cue.

## Layers (every SVG)

| Layer | Content | In print |
|---|---|---|
| `MATERIAL` | Reference rectangle naming the stock | Never printed |
| `WHITE_INK` | Opaque white underbase (frosted only) | Printed first |
| `CMYK` | Artwork (RGB references; vendor converts) | Printed |
| `SPOT_GLOSS` | Spot or raised gloss plate, 100% magenta key | Finish plate (vendor wants 100% black: recolour on export) |
| `DEBOSS` | Deboss die area, cyan key | Die |
| `NFC_SAFETY_ZONE` | Configurable keep-out | Never printed |
| `GUIDES` | Bleed, trim, safe, margin, contact axes, QR quiet zone | Never printed |

Plates and guides are hidden in the masters. `front-finish.png` shows the plates over the art, and
`front-guides.png` shows the grid and the zone.

## Finishes (summary)

The full table is in [finishes.md](finishes.md).
- Every edition must look finished in **flat CMYK alone**. Finishes add touch; they never carry the idea.
- At most one special finish per side (plus the base laminate).
- Raised gloss lines are at least 1.0 mm wide, cover less than 50% of a side, stay inside the 4 mm safe zone, and allow ±0.5 mm registration.

## Colour (summary)

The full table is in [color.md](color.md).
- Editions use **1–3 inks plus the base**. There are no gradients and no metallic inks.
- Cobalt appears in exactly one place: the contact point. On dark stock it is a lifted cobalt.
- Mono uses no colour at all.

## Personalisation (v1)

| Variable | Options | Where |
|---|---|---|
| Edition | Six curated designs; launch with three | Front + back stock |
| Name | Any. Steps 11 → 10 → 9 pt, then wraps | Back |
| Context | 0–2 lines | Back |
| QR | On / off (on only once the web profile exists) | Back |
| Monogram | 1–3 letters, Inter Medium 22 pt | Essential Mono front only |
| Accent | Fixed per edition; no free colour picker | — |

**Not allowed in v1:** uploaded artwork, photos, logos other than Kipat's, free colours, or free text on
the front. Each of these would break print QA, NFC safety and brand consistency.

### Personalised versus generic inventory

| | Generic front, personalised back | Generic both sides (inventory) |
|---|---|---|
| Print | Variable data on the back only (name, context, QR) | One plate per edition; offset-friendly runs |
| Stock risk | Made to order | Stocked; no waste from misspelled names |
| Card id | Pre-assigned at print (QR = id), or written at link | Written by the app when linking (works today, no change) |
| QR | Possible (per-card QR = per-card id) | None: the id doesn't exist yet when printed |
| Ownership feel | Higher (your name on it) | Lower; the identity lives in the app |
| App compatibility | A pre-assigned id needs a **claim** flow. Today the link flow refuses an unknown `kc_` id on a card ("belongs to another Kipat"). Needs the backend. | Works with today's link flow |

**Recommendation:** launch generic inventory cards, so the front is the edition and the back carries the
lockup and the tap row. Offer the personalised back (name and context, no QR) as an upgrade printed on
demand. Add the per-card QR only with the backend and web profile, together with a claim flow. Fronts
are never personalised except for Mono's monogram, which keeps fronts stockable.

## Recognition across editions

Two signatures make six editions read as one brand:
1. **The contact point**: same size, same position, every front.
2. **The back architecture**: same grid, type, tap row and QR position on every edition.

The lockup's position is a third, quieter constant. A candidate fourth is a **cobalt core** visible
only at the card's edge (MOO Luxe seam, Monzo coral core). It is a vendor question: coloured PVC core
sheets exist for some manufacturers, but this is unverified for Kipat.

## Never

- Gradients
- Neon or glow
- Holographic film
- Circuit or binary patterns
- Particles
- Fake glass
- Metallic foil over the coil
- A giant K
- A giant NFC icon
- A giant QR
- Slogans on the card
- Fake signatures
- Lorem ipsum
- Centred everything
- Payment-card furniture (chip rectangle, embossed digits, network-style corner marks, tier words)
