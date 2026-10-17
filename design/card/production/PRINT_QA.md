# Card print QA

Run this for every edition, every vendor and every run. Record PASS / FAIL / NOT TESTED, with a note and
a photo, in `production/runs/<date>-<vendor>-<edition>.md`. Copy this file as the template.
**Nothing has been printed yet. Every row below is NOT TESTED.**

## 1. Before sending artwork

| # | Check | How | Result |
|---|---|---|---|
| 1 | Dimensions | Trim 85.60 × 53.98 mm, radius 3.18 mm. Matches the **vendor template** (some use 85.5 × 54 or 85 × 55) | |
| 2 | Bleed | Vendor value (2–3.2 mm). Our working bleed is 3 mm: extend or crop to the vendor's figure | |
| 3 | Safe zone | Nothing important within 4 mm of the trim (`GUIDES` → `safe`) | |
| 4 | Vector logo | The lockup is vector paths, not raster. The mark's stroke is ≥ 0.26 mm (convert strokes to fills if the vendor asks) | |
| 5 | Fonts | All text is outlined in the masters (`data-text` keeps strings). No live fonts in the press file | |
| 6 | QR | The placeholder replaced by the per-card URL via variable data; 16 mm; 4-module quiet zone; graphite on light; `build.py` decode passes | |
| 7 | QR, physical | Printed proof scans first time with **three phones** (one budget Android, one flagship Android, one iPhone camera) at 10, 20 and 30 cm, in office light and dim light | |
| 8 | Minimum type | Back ≥ 8 pt; reversed ≥ 8 pt; no type in finish layers | |
| 9 | Black handling | Small type and lines in 100K (or the vendor's recipe), **not** 4-colour black. Rich black only on floods, per the vendor's recipe | |
| 10 | Transparency | Flatten as the vendor requires; no live transparency in the press file | |
| 11 | Spot layers | `SPOT_GLOSS` and `DEBOSS` exported as separate 100% black vector masks, named the way the vendor asks; inside the safe zone; < 50% of a side; lines ≥ 1.0 mm | |
| 12 | White ink (frosted) | `WHITE_INK` plate under every coloured element; choke as the vendor recommends | |
| 13 | Orientation | Front and back share the same top; the card flips on its long edge. Mark this on the order | |
| 14 | NFC zone | Vendor inlay drawing received; `NFC_SAFETY_ZONE` updated; no foil, metal or conductive ink inside it | |
| 15 | Colour | Vendor's printed colour proof approved against the references in `system/color.md`, under D50 or daylight. Check especially the cobalt dot and the orbit tone (it must stay tonal, not white) | |
| 16 | Proof approval | Signed digital proof **and** a physical pre-production sample before any run | |

## 2. Physical sample

| # | Check | Result |
|---|---|---|
| 17 | Contact point is 2.4 mm, sits at x 64.2, y 27.0 mm, and reads as deliberate rather than a speck (especially on Essential Mono) | |
| 18 | Raised gloss in register (±0.5 mm) and can be felt; no chipping at the line ends | |
| 19 | Soft-touch: no visible fingerprints after 1 minute of handling; no peeling at the edges | |
| 20 | Frosted: the antenna's visibility through the stock is acceptable, or the order switches to an opaque white core under the art | |
| 21 | Deboss (Layered Depth): depth even; no stress whitening; chip module untouched | |
| 22 | Legibility in ordinary indoor light: name, context and "Tap to connect" readable at arm's length | |
| 23 | Photograph the card with a phone, badly lit (review question 8). Does it still look intentional? | |

## 3. NFC QA (every prototype, after production)

The card must hold the linked route. Write it with Profile → Kipat cards → Add Kipat card; it expects 69 bytes.

| # | Test | Phones (≥ 3 brands) | Result |
|---|---|---|---|
| 24 | Tap the **front** at the contact point, no case | | |
| 25 | Tap the **back** | | |
| 26 | Tap the card's centre and each corner (record where it fails) | | |
| 27 | Phone with a typical case on | | |
| 28 | Card rotated 90° and 180° | | |
| 29 | Different phone antenna positions (top-back, centre-back) | | |
| 30 | Read distance compared with a plain white NTAG215 control card | | |
| 31 | Write and read back in the app ("Card linked"), then Reveal | | |

Record any finish that shortens read distance or causes misses. **Do not approve a beautiful card that
reads worse than the plain control card.**

## 4. Sign-off

| Role | Name | Date |
|---|---|---|
| Design | | |
| Production | | |
| NFC test | | |
