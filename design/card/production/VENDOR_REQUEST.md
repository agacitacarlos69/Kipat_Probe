# Physical sample request (vendor handoff)

A ready-to-send brief for **pre-production samples** of the Kipat card.
- **Nothing has been ordered and no vendor has been contacted.**
- No prices are given or assumed.
- Quantities are left for the user and the vendor to fill in.

Artwork baseline: the vector masters on `main` in `design/card/directions/*/`. Do not redesign them
(see `design/card/FINAL_RECOMMENDATION.md`). Run `python3 design/card/tools/validate_assets.py`
before sending: it checks size, layers, text in the safe zone and that every QR decodes.

## What we are asking for

| | |
|---|---|
| Product | NFC PVC card, ISO/IEC 7810 ID-1 (CR80) **85.60 × 53.98 mm**, 0.76 mm thick (or the vendor's nearest standard), corner radius about 3.18 mm |
| Chip | **NTAG215** for the prototype (504 bytes; a linked Kipat card needs 69). Quote NTAG216 as an option. Quote NTAG 424 DNA separately for later authenticity work |
| Encoding | **Blank chips**, not pre-encoded. The Kipat app writes the linked-card URI at link time |
| Purpose | Pre-production samples to validate print quality, finishes and NFC read performance. Not a production run |

## Sample matrix

| # | Edition | Artwork | Card body / material | Print | Finish | NFC clearance |
|---|---|---|---|---|---|---|
| 1 | **Essential Mono** (white; dark variant optional) | `directions/essential-mono/front.svg`, `back-generic.svg` (+ `back.svg` personalised, `front-monogram.svg`) | White-core PVC, **matte** laminate | 1 ink (graphite) | None | Standard construction |
| 2 | **Graphite Precision** | `directions/graphite-precision/front.svg`, `back-generic.svg` | White-core PVC, graphite flood, **soft-touch** laminate (matte as fallback) | Up to 3 inks + flood | **Raised or spot gloss** on the orbit and contact point (`SPOT_GLOSS` layer, lines ≥ 1.0 mm) | **VENDOR NFC CLEARANCE REQUIRED** for soft-touch plus raised gloss over the inlay |
| 3 | **Architectural Line** | `directions/architectural-line/front.svg`, `back-generic.svg` | White-core PVC, light paper-white flood, **matte** laminate | 2 inks + flood | **Raised or spot gloss** on the line (stops at the safe zone) | **VENDOR NFC CLEARANCE REQUIRED** for raised gloss over the inlay |
| 4 | **Frosted Contact** *(experimental)* | `directions/frosted-contact/front.svg`, `back-generic.svg` | **Frosted translucent PVC** (clear core) | **White ink underbase** + graphite + cobalt | White-ink rings (part of the print) | **VENDOR NFC CLEARANCE REQUIRED**. Also confirm how visible the antenna is through frosted stock, and whether an opaque white layer behind the inlay is needed |

Quantity per edition: `____` (user or vendor to fill). Suggest enough per edition for print QA, NFC
tests on several phones and a destructive wear test.

## Non-negotiable constraints

1. **No metallic foil, metal plate, metal core or conductive or metallic ink** anywhere on the card
   until the vendor certifies the build and a printed sample passes our NFC test
   (`design/card/production/PRINT_QA.md` §3).
2. **Send us the NFC inlay drawing**: antenna coil outline and position, chip module position, and
   any keep-out areas. Our artwork's `NFC_SAFETY_ZONE` currently covers the whole card because the
   geometry is unknown; we will replace it with your drawing.
3. **Your template is authoritative.** Our 3 mm working bleed and 4 mm safe zone are placeholders.
   Send your CR80 template (bleed, safe zone, colour profile, spot or white-plate naming), and we will
   conform the files.
4. **Colour:** our values are digital references only (`design/card/system/color.md`). Supply a
   **printed colour proof** for approval before any run, especially for the cobalt contact point and
   the tonal orbit on Graphite Precision.
5. **Proof approval is required** before production: a signed digital proof **and** a physical
   pre-production sample.
6. **Physical NFC test is required** on the samples before approval (we run it): front and back taps,
   several phone brands, case on and off, read distance against a plain NTAG215 control card.
7. The QR on the personalised back is a **placeholder**. Pilot samples use the generic back (no QR).

## Files to send

- The SVG masters listed above. They have layers `WHITE_INK`, `CMYK`, `SPOT_GLOSS` (magenta key) and
  `DEBOSS` (cyan key), plus non-printing `NFC_SAFETY_ZONE`, `GUIDES` and `MATERIAL`. Text is outlined.
- `design/card/system/CARD_DESIGN_SYSTEM.md`, `color.md`, `finishes.md`
- `design/card/production/README.md`: how the files map to press files. We will produce PDF/X to
  your spec once we have your template.

## Questions for the vendor

1. Do you offer soft-touch laminate on PVC, and does it change NFC read range with your inlays?
2. Raised (3D) spot gloss on PVC: minimum line width, registration tolerance, and distance from the edge?
3. Frosted PVC: available thickness, white-ink opacity, and antenna visibility?
4. Can you make a coloured (cobalt) core visible at the card edge? This is a candidate signature,
   not part of this sample request.
5. Lead time and minimum order for samples? (No pricing assumptions on our side.)
