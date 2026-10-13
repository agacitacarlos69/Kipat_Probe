# Aperture

**CONCEPT** The card arrives with its avatar closed behind a small flat iris. The iris turns a
little, opens, and the person is "in focus". The mechanism is gone before the name is read.

**METAPHOR** Focus: the device has resolved a real identity. Optical, not photographic.

**WHY NFC** A tag read is a moment of resolution — the unknown card becomes a specific person.

**KEY FRAME 1 (0–150 ms)** Card fading in; six flat ink blades closed over the avatar position.
**KEY FRAME 2 (~400 ms)** Blades rotated ~8°, half open; initials visible in the hexagonal opening.
**KEY FRAME 3 (1240 ms)** Blades gone; avatar, name, trust, actions settled.

**MOTION TIMELINE**

| ms | Beat | Curve |
|---|---|---|
| 40–340 | Card surface opacity (blades are part of it) | KipatStandard |
| 120–220 | Blades turn −5° (closing pressure before opening) | KipatFast |
| 220–560 | Opening: hexagon 0 → beyond the 36 dp outer edge; blades turn −5° → +12° | KipatEmphasized |
| 480–640 | Blade opacity → 0 | KipatExit |
| 520 / 620 | Name / role | KipatStandard |
| 820 | Trust line | KipatStandard |
| 940–1240 | Actions; settled at 1240 | KipatStandard |

Mechanism on screen: 40–640 ms (600 ms).

**COLOR USE** Blades in ink; seams in the card surface colour. No accent.
**LIGHT MODE** Graphite blades on the white card. **DARK MODE** Mist blades on the charcoal card.
**REDUCED MOTION** Crossfade; no blades.
**IMPLEMENTATION** `EntryPrimitive.APERTURE` (above the card, centred on the measured avatar),
`SurfaceEntry.FADE`, `NameEntry.FADE`. Six blade paths built in a reused `Path` (annulus between a
rotating hexagon and a circle, split along the hexagon's extended edges); seams stroked in the card
colour, so no offscreen layer.
**PERFORMANCE RISK** Low: six small path fills for 600 ms, no allocation.
**AI-SLOP REVIEW** First pass scored 3 (camera skeuomorphism). Revised: flat blades, 36 dp, ink
only, no metal, lens or reflection; it never becomes interface.

## Design loop

1 Metaphor: focus · 2 Communicates: "resolved to this person" · 3 NFC: resolution of an unknown
card · 4 Moment: initials appearing in the opening iris · 5 First 150 ms: card + closed iris ·
6 Readable: name 520 ms · 7 Decoration: none · 8 Mid-range: yes · 9 Reduced: crossfade · 10 Five
years: yes, if kept flat · 11 Strong for creators, photographers, designers; fine for all ·
12 Distinct: only template that hides then shows · 13 Explainable: yes · 14 No glow: yes · 15 Light:
yes · 16 Real product: yes.

| Clarity | NFC | Original | Professional | Minimal | Motion | Perf | A11y | Feasible | Slop risk |
|---|---|---|---|---|---|---|---|---|---|
| 4 | 3 | 5 | 4 | 4 | 4 | 4 | 4 | 4 | 2 |
