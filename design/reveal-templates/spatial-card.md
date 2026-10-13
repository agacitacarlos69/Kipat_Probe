# Spatial Card

**CONCEPT** The restrained 3D experiment: the card arrives as a thin physical object tilted 6°
back, shows its edge, and settles flat into the screen — physical becoming digital.

**METAPHOR** A card set down on a desk, seen from just above.

**WHY NFC** The Reveal comes from a physical card; this is the only template that acknowledges it
is an object with thickness.

**KEY FRAME 1 (0–150 ms)** Card fading in, tilted 6° (top away), 16 dp low, a 3 dp edge visible
along its bottom.
**KEY FRAME 2 (~400 ms)** Nearly flat, shadow forming (light mode), edge almost gone.
**KEY FRAME 3 (1200 ms)** Flat, settled; identity and actions in place.

**MOTION TIMELINE**

| ms | Beat | Curve |
|---|---|---|
| 0–250 | Card opacity | KipatStandard |
| 0–~600 | rotationX 6° → 0, translation 16 dp → 0 (pivot: bottom edge) | KipatSettle spring (ζ 0.9, k 300) |
| 0–600 | Edge thickness 3 dp → 0 (proportional to tilt); shadow 0 → 12 dp (light) | follows tilt |
| 380 / 480 / 580 | Avatar / name / role | KipatStandard |
| 780 | Trust line | KipatStandard |
| 900–1200 | Actions; settled at 1200 | KipatStandard |

**COLOR USE** Edge in the outline token. No accent.
**LIGHT MODE** Shadow grows as it settles; edge in #8A8D95. **DARK MODE** No shadow; edge #737680.
**REDUCED MOTION** Scale 0.98 → 1 + opacity, 200 ms. No tilt.
**IMPLEMENTATION** `EntryPrimitive.EDGE` (drawn behind the face inside the card's own layer, so it
gets the same perspective), `SurfaceEntry.TILT` via `graphicsLayer { rotationX; cameraDistance;
transformOrigin }`. No 3D engine: a 6° rotation is fully described by one layer transform.
**PERFORMANCE RISK** Low: one layer transform; animated shadow is skipped on low-power devices.
**AI-SLOP REVIEW** First pass scored 4 (3D demo, trading card). Revised: 6° max, X axis only, no
gloss, no Y rotation, no flip, no float; depth = edge + shadow.

## Design loop

1 Metaphor: a card set down · 2 Communicates: a real object arrived · 3 NFC: physical card →
digital · 4 Moment: the edge disappearing as it lands flat · 5 First 150 ms: tilted card with edge ·
6 Readable: 480 ms · 7 Decoration: the edge is the whole idea; nothing else · 8 Mid-range: yes ·
9 Reduced: scale + opacity · 10 Five years: yes at 6° · 11 Strong for executives and companies ·
12 Distinct: only template with depth · 13 Explainable: yes · 14 No glow: yes · 15 Light: yes, best
in light (shadow) · 16 Real product: yes.

| Clarity | NFC | Original | Professional | Minimal | Motion | Perf | A11y | Feasible | Slop risk |
|---|---|---|---|---|---|---|---|---|---|
| 5 | 3 | 3 | 5 | 4 | 4 | 5 | 4 | 5 | 2 |
