---
name: kipat-card-design
description: Kipat's physical NFC card design authority. Use before designing, reviewing, regenerating or producing any physical Kipat card (fronts, backs, editions, finishes, QR, print files, mockups, Higgsfield product photos) so the card stays one recognisable, manufacturable, NFC-safe Kipat object and never drifts into bank-card, crypto, gaming or AI-concept styling.
---

# Kipat card design

Source of truth: `design/card/system/CARD_DESIGN_SYSTEM.md`, with `typography.md`, `color.md` and
`finishes.md` alongside it. Evidence is in `design/card/research/reference-matrix.md` (20 shipped
references). The artwork is built only by `design/card/tools/build.py`. App UI rules are a separate
skill: `kipat-product-design`.

## What the card is

A tangible extension of one Kipat identity. The phone holds the profile; the card holds only
`https://kipat.app/c/<card id>` and is resolved to the owner's current identity and Reveal at tap time
(`docs/nfc-card-identity-sync.md`). So the card never has to carry information. It has to be an object
people want to own, carry, show and tap. Technology disappears; the tap remains.

Users: students, developers, designers, creators, founders, professionals, speakers, organisations.
It must not look like a student org ID, a gaming membership, an anime collectible, a crypto wallet, a
nightclub pass or a bank card.

## Non-negotiables

1. **One idea per card.** Explain the front in one sentence or reject it.
2. **The contact point** is a 2.4 mm dot at x 64.20, y 26.99 mm on every front. Same size, same place,
   always. Cobalt #2747C8 (#5470E6 on dark), or the edition ink on Mono. It is functional: it marks
   where to tap. It is never red, never a logo, never holds letters, and never sits in a finish layer.
3. **Lockup** (app mark plus Inter Medium *kipat*, 9 pt) sits top-left on the 6 mm margin of every
   front. Kipat has no final logo yet: never draw a new one, never add a giant K, never put the mark in
   a badge.
4. **The back architecture is identical across editions:**
   - name 11 pt (stepping down to 9 pt, then wrapping)
   - ≤ 2 context lines at 8 pt
   - "(•)) Tap to connect" at 8 pt Medium on the bottom margin
   - optional 16 mm QR at the bottom-right, with a 4-module quiet zone
5. **Front contents:** the motif, the contact point and the lockup only, plus an optional monogram on
   Essential Mono. There is never any of the following on a front:
   - email, phone, URL, QR, social icons
   - an NFC icon, a slogan
   - a chip-like rectangle, a hologram, embossed digits
   - a network-style corner mark, a tier word
6. **Print constraints:**
   - CR80 85.60 × 53.98 mm, radius 3.18 mm
   - 3 mm *working* bleed: the vendor's template wins
   - 4 mm safe zone, 6 mm content margin
7. **Inks:** 1–3 inks plus the base. No gradients, metallic inks, fluorescent ink or holographic film.
   Cobalt appears only at the contact point.
8. **Finishes:**
   - At most one special finish per side.
   - The card must work in flat CMYK.
   - Raised gloss lines are ≥ 1.0 mm wide, inside the safe zone, cover < 50% of a side, and allow
     ±0.5 mm registration.
   - Finish text ≥ 10 pt (Kipat puts none in finishes).
9. **NFC:** the safety zone is the whole card until the inlay vendor supplies a drawing. Inside it, no
   metallic foil, metal or conductive ink. Never guess antenna geometry. Never print a visible NFC graphic.
10. **QR:**
    - Graphite on light, a 4-module quiet zone, 16 mm.
    - Never recoloured, stylised or given a logo in the middle.
    - Design files carry the placeholder `kc_0000000000000000`, marked `data-placeholder`.
    - `build.py` must decode every QR.
11. **Type:**
    - Inter (OFL), Regular and Medium only, sentence case.
    - ≥ 8 pt on backs, ≥ 8 pt for reversed type.
    - Outlined in the masters (`data-text` keeps the string).
12. **Tone balance:** across the family, two dark, two light, one material-led and one neutral.
13. **Personalisation (v1):** edition, name, context, QR on/off and the Mono monogram. No uploaded
    art, photos, free colours or front text.
14. **Card edition is not a Reveal setting.** Editions have recommended Reveal pairings, but the user
    can choose any Reveal. Never claim the printed card changes.

## The six editions (one idea each)

| Edition | Tone | One idea | Reveal pairing |
|---|---|---|---|
| Graphite Precision | dark | One precision orbit around the contact point | Precision Orbit |
| Frosted Contact | material | Touch leaves a mark: two white-ink rings in frosted PVC | Contact Ripple |
| Architectural Line | light | One line enters, turns once, ends at the contact point | Line Assembly |
| Aperture | neutral | One flat six-segment aperture opening on the contact point | Aperture |
| Layered Depth | dark | The contact point is the corner of a second, pressed plane | Spatial Card |
| Essential Mono | light (dark variant) | Nothing but Kipat and the point (optional monogram) | Minimal Fade |

## Workflow

1. Change geometry or type **only in `design/card/tools/build.py`**, then run
   `python3 design/card/tools/build.py`. It regenerates `front.svg`, `back.svg`, `back-generic.svg`, the
   proofs, the finish maps, the guides and the identity stress backs, and decodes every QR.
2. Review against the 12 questions below with the proofs open. Check all four identities in
   `design/card/boards/identities/` (student, professional, creator, organisation).
3. Higgsfield comes only after vector artwork is approved, and only for product photography
   (`design/card/higgsfield/`). Supply the proof as a reference, and require the model to preserve it
   exactly: no text, logo, pattern, metal, neon, hands or phone unless planned. On the Basic plan, at
   most one still per finalist per round. Critique every output (`critique.md`: KEEP / REFERENCE ONLY /
   REJECT). Generated images **never** enter `directions/` or `production/`.

## Review questions (all must be yes)

1. Is there one visual idea, explainable in one sentence?
2. Has everything removable been removed?
3. Is it recognisably Kipat (contact point, lockup, back architecture)?
4. Is the typography excellent at actual size (print a 1:1 proof)?
5. Is it manufacturable with the listed finishes on PVC?
6. Is the NFC area free of conductive material?
7. Does it work without gradients and without any special finish?
8. Does it still look good photographed badly?
9. Is it free of credit-card language?
10. Does it look like a real product, not AI concept art?
11. Would someone want it if NFC were never mentioned?
12. Would a student carry it, a founder hand it out, and a designer call it tasteful, three years from now?

## Reject on sight

- black card with blue neon
- purple-to-blue gradient
- glowing K
- circuit-board or binary pattern
- particles
- holographic rainbow
- fake glass
- metallic foil over the coil
- meaningless waves
- diagonal mesh
- random AI shapes
- everything centred
- huge NFC icon or huge QR
- fake signature
- lorem ipsum
- slogans on the card
- trading-card look
- literal camera shutter
- mechanical chrome
