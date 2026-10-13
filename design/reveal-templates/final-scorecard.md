# Reveal templates — final scorecard

Scored after implementation from the Robolectric renders of the real Compose code
(`docs/images/reveal-templates-light.png`, `-dark.png`; regenerate with
`./gradlew :app:testDebugUnitTest -Pkipat.screenshots=true`). Not yet seen on a phone, so motion
feel at 60/120 Hz, haptic pairing and behaviour over real apps are **unverified**; scores for
"Performance" are design-budget judgements, not measurements.

1–5, higher is better.

| | Professional | Minimal | Distinctive | NFC meaning | Identity clarity | Performance | Accessibility | Implementation | Durability | **Total /45** |
|---|---|---|---|---|---|---|---|---|---|---|
| Contact Ripple | 5 | 4 | 4 | 5 | 5 | 5 | 5 | 5 | 5 | **43** |
| Line Assembly | 5 | 4 | 4 | 3 | 4 | 5 | 5 | 5 | 5 | **40** |
| Spatial Card | 5 | 5 | 3 | 4 | 5 | 5 | 4 | 5 | 4 | **40** |
| Precision Orbit | 4 | 4 | 5 | 4 | 4 | 5 | 4 | 5 | 4 | **39** |
| Minimal Fade | 5 | 5 | 1 | 2 | 5 | 5 | 5 | 5 | 5 | **38** |
| Aperture | 4 | 4 | 5 | 3 | 4 | 4 | 4 | 4 | 4 | **36** |

Notes behind the numbers:

- **Contact Ripple** — the only template whose motion *is* the cause of the Reveal (contact at a
  point). Identity is uncovered in reading order from the top. Ring B is the one element that could
  go; kept at 40% because it ends the gesture rather than cutting it off.
- **Line Assembly** — reads as drafting, not HUD: two drawn lines, two faded, all hairline ink. The
  card is invisible until ~700 ms, so identity arrives later than Ripple (name 840 ms).
- **Spatial Card** — the most "physical" and the calmest; the edge band does all the work. Depth is
  weaker in dark mode (no shadow; edge is a faint lit line). Spring tilt untested at 120 Hz.
- **Precision Orbit** — the most mechanical; the symmetric quarter turn avoids the spinner read
  the Higgsfield clip fell into. The settled 1 dp accent ring around the avatar is the only
  persistent decoration in the family — kept at 35% so it frames rather than haloes.
- **Minimal Fade** — nothing to remember, nothing to dislike. Fastest (620 ms).
- **Aperture** — the most striking moment, and the most at risk: the closed iris is a strong dark
  disc for ~250 ms, and its seams are drawn in the card colour (light lines over the avatar disc).
  Professional when brief; would not survive being slower.

## Picks

| Role | Template | Why |
|---|---|---|
| **DEFAULT** | **Contact Ripple** | Highest score; strongest NFC meaning; identity readable early (name 340–640 ms); calm in both themes. Not the most complex template. |
| Best for professionals | Spatial Card | Quiet, physical, no geometry to interpret; looks like an object, not an effect |
| Best for students | Precision Orbit | The most expressive while staying monochrome and brief |
| Best for companies | Line Assembly | Architectural, rectilinear, brand-neutral; suits a company card's precision |
| Most distinctive | Aperture | The one people will remember (and the one to use sparingly) |
| Best low-power fallback | Minimal Fade | One layer fade, 620 ms; also the reduced-complexity reference |

## Product review

*Beside a well-designed banking, productivity or identity app, would this motion feel equally
intentional?* Yes for Contact Ripple, Spatial Card, Line Assembly and Minimal Fade: each move
has one stated reason, uses platform curves, and stops. Precision Orbit and Aperture are
intentional too but more expressive — they are options, not the default.

*Engineered product or portfolio animation?* Engineered product: one renderer, one primitive
file, the same card and actions everywhere, per-template reduced-motion equivalents, a picker
that previews before applying, and timings that settle faster than the previous Signature
(1550 ms) in every case.

## Removed during review (rule 20: "if removing something improves it, remove it")

- Contact Ripple's emphasized-decelerate reveal (front-loaded; read as a wipe) → M3 standard curve.
- Aperture's fully closed state (pie chart) → minimum 12% opening.
- Aperture's translucent blades (from the Higgsfield clip) → solid until they are slivers.
- Orbit's asymmetric arc phase (spinner) → arcs locked 180° apart.
- Spatial Card's grey edge in dark mode (invisible) → faint lit edge.
- The retired v1/v2 catalog: Particle Bloom, Soft Glow, Liquid Logo, Pixel Build, Constellation,
  and the Signature drip/trace (their IDs still resolve, to the default).
