# Line Assembly

**CONCEPT** The card is set out like a technical drawing: two construction lines establish its
top edge and left edge, the remaining edges fade in, the surface fills, and the lines leave.

**METAPHOR** Industrial design drafting; setting out a sheet before printing on it.

**WHY NFC** A card becomes a digital card: its physical geometry is "measured" first. The corner
where the lines start is the card's own origin (top-left, where the content grid starts).

**KEY FRAME 1 (0–150 ms)** A 3 dp accent mark at the card's top-left corner.
**KEY FRAME 2 (~520 ms)** Top and left hairlines drawn full length, overshooting the corners by
12 dp like drafting lines; right and bottom faint.
**KEY FRAME 3 (1400 ms)** Card filled; lines gone; identity and actions in place.

**MOTION TIMELINE**

| ms | Beat | Curve |
|---|---|---|
| 0–150 | Contact mark at top-left | KipatFast |
| 150–380 | Top line extends across, 12 dp past both corners | KipatEmphasized |
| 300–520 | Left line extends down, 12 dp past both corners | KipatEmphasized |
| 450–700 | Right and bottom lines fade in (not drawn) | KipatStandard |
| 600–900 | Surface fills (opacity only) | KipatStandard |
| 900–1200 | All lines fade out | KipatExit |
| 760 / 840 / 940 | Avatar / name (wipe) / role | KipatStandard |
| 1040 | Trust line | KipatStandard |
| 1100–1400 | Actions; settled at 1400 | KipatStandard |

**COLOR USE** Ink hairlines (1 dp, 75%); accent only for the contact mark.
**LIGHT MODE** Graphite lines on paper. **DARK MODE** Mist lines on charcoal.
**REDUCED MOTION** Perimeter fade: all four lines present at once and fade out over 200 ms as the card fades in.
**IMPLEMENTATION** `EntryPrimitive.LINES` (under the card; the card covers the inner part of each
line, leaving drafting overshoots visible), `SurfaceEntry.FADE`, `NameEntry.WIPE`. Four `drawLine`s.
**PERFORMANCE RISK** Very low.
**AI-SLOP REVIEW** First pass scored 3 (HUD trace, corner brackets). Revised: two lines drawn, two
faded, hairline ink, straight lines only, no scanning bar, no brackets.

## Design loop

1 Metaphor: drafting · 2 Communicates: the card has real dimensions · 3 NFC: physical → digital ·
4 Moment: two lines crossing at the corner with overshoot · 5 First 150 ms: corner mark · 6 Readable:
840 ms · 7 Decoration: the overshoot is the one stylistic element; it is the drafting idea itself ·
8 Mid-range: yes · 9 Reduced: perimeter fade · 10 Five years: yes · 11 Strongest for companies,
architects, engineers · 12 Distinct: the only rectilinear template · 13 Explainable: yes · 14 No
glow: yes · 15 Light: yes · 16 Real product: yes.

| Clarity | NFC | Original | Professional | Minimal | Motion | Perf | A11y | Feasible | Slop risk |
|---|---|---|---|---|---|---|---|---|---|
| 4 | 3 | 4 | 5 | 4 | 4 | 5 | 5 | 5 | 1 |
