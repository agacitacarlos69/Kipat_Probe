# Precision Orbit

**CONCEPT** The identity anchor is assembled: two incomplete arcs at the avatar's position rotate a
short controlled distance, close into one ring, and the person appears inside it.

**METAPHOR** A precision mechanism locking into place (a bezel turning to its stop). Not a
spinner: it turns once, a quarter turn, and stops.

**WHY NFC** Reading a card is a handshake that completes. Two halves aligning = two devices
agreeing on who this is.

**KEY FRAME 1 (0–150 ms)** A 3 dp contact dot where the avatar will be.
**KEY FRAME 2 (~450 ms)** Two 1.5 dp arcs, each about 150°, a quarter turn from where they started,
about to close.
**KEY FRAME 3 (1400 ms)** Ring closed and settled as a 1 dp hairline in the accent around the
avatar; name, role, trust, actions in place.

**MOTION TIMELINE**

| ms | Beat | Curve |
|---|---|---|
| 0–120 | Contact pulse: dot 0 → 3 dp (fades 120–360) | KipatFast |
| 120–550 | Arcs rotate 90° together; sweep 100° → 180° each from 300 ms; closed at 550 | KipatEmphasized |
| 450–700 | Ring expands 38 → 42 dp radius; stroke 1.5 → 1 dp; ink → accent | KipatStandard |
| 420–720 | Card surface opacity, scale 0.99 → 1 | KipatStandard |
| 560 | Avatar inside the ring (scale 0.92 → 1) | KipatStandard |
| 720 / 820 | Name (wipe) / role | KipatStandard |
| 1000 | Trust line | KipatStandard |
| 1100–1400 | Actions; ring settles to 35% opacity; settled at 1400 | KipatStandard |

**COLOR USE** Ink until the ring closes; accent only from completion (550–700 ms).
**LIGHT MODE** Graphite arcs on paper, cobalt hairline on the white card.
**DARK MODE** Mist arcs on charcoal, light-cobalt hairline on the dark card.
**REDUCED MOTION** Static ring: the settled ring fades in with the card (200 ms).
**IMPLEMENTATION** `EntryPrimitive.ORBIT` (drawn above the card, centred on the measured avatar),
`SurfaceEntry.FADE_SCALE`, `NameEntry.WIPE`. Two `drawArc` calls per frame.
**PERFORMANCE RISK** Very low.
**AI-SLOP REVIEW** First pass scored 4 (spinner, orb). Revised: quarter turn once, hairlines, no
glow, accent only after closing; the settled ring is a 1 dp frame, not an avatar halo.

## Design loop

1 Metaphor: a bezel locking · 2 Communicates: identity being confirmed · 3 NFC: two halves agree ·
4 Moment: the arcs closing (550 ms) · 5 First 150 ms: contact dot · 6 Readable: name from 720 ms ·
7 Decoration: none; the ring persists as the identity frame · 8 Mid-range: yes · 9 Reduced: static
ring · 10 Five years: yes if it never loops · 11 All roles: yes, strongest for developers/technical
· 12 Distinct: only template centred on the avatar with rotation · 13 Explainable: yes · 14 Without
glow: designed without · 15 Light: yes · 16 Real product: yes.

| Clarity | NFC | Original | Professional | Minimal | Motion | Perf | A11y | Feasible | Slop risk |
|---|---|---|---|---|---|---|---|---|---|
| 4 | 4 | 4 | 4 | 4 | 5 | 5 | 4 | 5 | 2 |
