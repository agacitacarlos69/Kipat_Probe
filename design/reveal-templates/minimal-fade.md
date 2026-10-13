# Minimal Fade

**CONCEPT** The baseline. The card is simply there, a beat after the tap. Nothing draws attention
to the motion; everything draws attention to the person.

**METAPHOR** A page turned into view. No physical object is simulated.

**WHY NFC** The tap itself is the event; the haptic carries the physical acknowledgement. The
screen answers immediately rather than performing.

**KEY FRAME 1 (0–150 ms)** Scrim dims the previous app. The card begins to fade in 10 dp low.
**KEY FRAME 2 (320 ms)** Card, avatar, name and role fully readable.
**KEY FRAME 3 (620 ms)** Trust line and actions in place. Settled.

**MOTION TIMELINE**

| ms | Beat | Curve |
|---|---|---|
| 0–150 | Scrim to base | linear |
| 40–320 | Card surface + identity: opacity 0→1, rise 10 dp→0 | KipatStandard |
| 300–540 | Trust line | KipatStandard |
| 380–620 | Actions; settled at 620 | KipatStandard |

**COLOR USE** None beyond the card. Accent only on Connect.
**LIGHT MODE** Paper scrim, white card with hairline border and soft shadow.
**DARK MODE** Graphite scrim, #26272B card, no shadow.
**REDUCED MOTION** 200 ms crossfade; everything readable at once.
**IMPLEMENTATION** `SurfaceEntry.FADE_RISE`, `EntryPrimitive.NONE`, `NameEntry.FADE`. One `graphicsLayer`.
**PERFORMANCE RISK** None. One layer alpha + translation.
**AI-SLOP REVIEW** Nothing to remove. Risk: looks unfinished next to the others. Mitigation: its
speed is the feature; it is the low-power and reduced-motion reference.

## Design loop

1 Metaphor: page into view · 2 Communicates: "here is the person" · 3 NFC: the tap is the event, the
screen just answers · 4 Moment: identity readable at 320 ms · 5 First 150 ms: scrim + card begins ·
6 Readable: 320 ms · 7 Decoration: none · 8 Mid-range: trivially · 9 Reduced: crossfade · 10 Five
years: yes · 11 Student/dev/exec/company: all · 12 Distinct: by absence · 13 Explainable: yes ·
14 Without glow: has none · 15 Light: yes · 16 Real product: yes.

| Clarity | NFC | Original | Professional | Minimal | Motion | Perf | A11y | Feasible | Slop risk |
|---|---|---|---|---|---|---|---|---|---|
| 5 | 2 | 1 | 5 | 5 | 3 | 5 | 5 | 5 | 1 |
