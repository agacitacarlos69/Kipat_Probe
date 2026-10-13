# Contact Ripple

**CONCEPT** The card is uncovered by the ripple of the tap: one thin ring leaves the contact point
and the card surface is revealed inside it, like a still surface touched once.

**METAPHOR** Two surfaces touching; a single drop on still water — without water.

**WHY NFC** NFC is literally contact at a point. The ripple starts where Kipat's contact point has
been since v2 (the card's top centre), so the motion names the cause of the Reveal.

**KEY FRAME 1 (0–150 ms)** A 2.5 dp contact dot at the card's top centre; the first ring leaves it.
**KEY FRAME 2 (~400 ms)** The ring is the moving edge of a circular reveal: inside it the card,
avatar and name; outside it the scrim. A second, fainter ring follows.
**KEY FRAME 3 (1220 ms)** Full card, trust, actions. Rings gone.

**MOTION TIMELINE**

| ms | Beat | Curve |
|---|---|---|
| 0–100 | Contact dot in (fades out 100–300) | KipatFast |
| 40–640 | Ring A, radius 0 → farthest card corner; it is the edge of the card's circular reveal | KipatStandard |
| 40–160 | Card layer opacity 0→1 (inside the reveal) | KipatFast |
| 200–900 | Ring B, 1.25× radius, 40% → 0 opacity, 1 dp | KipatStandard |
| 260 / 340 / 420 | Avatar / name / role fade in as the reveal passes | KipatStandard |
| 760 | Trust line | KipatStandard |
| 920–1220 | Actions; settled at 1220 | KipatStandard |

**COLOR USE** Rings and dot in ink at 0.8 / 0.4 opacity. No accent until Connect.
**LIGHT MODE** Graphite rings on the paper scrim; the white card is uncovered inside them.
**DARK MODE** Mist rings on graphite; the charcoal card is uncovered inside them.
**REDUCED MOTION** Contact acknowledgement: the dot fades in and out once (200 ms) while the card crossfades.
**IMPLEMENTATION** `EntryPrimitive.RIPPLE` (under the card), `SurfaceEntry.CIRCULAR_REVEAL`
(`drawWithContent` + `clipPath` on a reused Path; no clip once complete), `NameEntry.FADE`.
**PERFORMANCE RISK** Low. Two stroked circles + one path clip for 600 ms.
**AI-SLOP REVIEW** Risk: "energy wave". Prevented: 1–1.25 dp rings, one ink, two rings only, no
glow, ripple ends at the card's edge rather than filling the screen.

## Design loop

1 Metaphor: contact on a still surface · 2 Communicates: "this came from the tap" · 3 NFC: contact
at a point · 4 Moment: the ring is the edge of the card · 5 First 150 ms: dot + ring + card starts
inside it · 6 Readable: ~420 ms (name) · 7 Decoration: ring B is borderline; kept at 40% because it
completes the gesture · 8 Mid-range: yes · 9 Reduced: dot acknowledgement · 10 Five years: yes
(circular reveal is an Android idiom) · 11 All roles: yes · 12 Distinct: only template that masks ·
13 Explainable: yes · 14 Without glow: yes · 15 Light: yes · 16 Real product: yes.

| Clarity | NFC | Original | Professional | Minimal | Motion | Perf | A11y | Feasible | Slop risk |
|---|---|---|---|---|---|---|---|---|---|
| 5 | 5 | 3 | 5 | 4 | 4 | 5 | 5 | 5 | 2 |
