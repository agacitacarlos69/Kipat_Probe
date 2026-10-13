# Kipat Reveal templates

Six interpretations of **one** identity system. Every template shows the same card, the same
data (avatar, name, role/program, company/university, authenticity, Connect, Save contact) in the
same order and layout. Only the entrance choreography differs. The app shell stays Quiet Graphite.

| # | Template | One primary motion | Character |
|---|---|---|---|
| 1 | [Precision Orbit](precision-orbit.md) | Two arcs align into the identity ring | Measured, geometric |
| 2 | [Contact Ripple](contact-ripple.md) | One ripple from the contact point uncovers the card | Subtle and tactile |
| 3 | [Line Assembly](line-assembly.md) | Two construction lines set the card's geometry | Architectural precision |
| 4 | [Aperture](aperture.md) | A flat iris opens on the avatar | Focused and technical |
| 5 | [Spatial Card](spatial-card.md) | A 6° tilt settles flat | Subtle physical depth |
| 6 | [Minimal Fade](minimal-fade.md) | Fade and a 10 dp rise | Quiet and immediate |

Rule (no effect stacking): **one primary motion + surface resolve + text staging.** Nothing else.
No particles, glow, gradients, blur, bloom or sound in any template.

Final scores and role picks: [final-scorecard.md](final-scorecard.md).

**Revised for the identity surface** (`design/reveal-surface/`): every template now settles into
`KipatIdentitySurface`, and content, trust and actions arrive as the surface reaches them. The
primitive beats in each template doc are unchanged except Precision Orbit's ring, which now
tightens onto the avatar (avatar + 5 dp) instead of expanding. Current card/content timings:

| Template | Surface | Name | Trust | Actions | Clock stops |
|---|---|---|---|---|---|
| Contact Ripple | 40 (reveal from contact point, 40–760) | 180 | 300 | 380 | 900 |
| Precision Orbit | 560 (grows from the ring, 560–1000) | 700 | 780 | 860 | 1160 |
| Line Assembly | 600–900 (fills inside the lines) | 680 | 780 | 840 | 1200 |
| Aperture | avatar disc from 40; unfolds 520–900 | 600 | 700 | 780 | 1080 |
| Spatial Card | 0–250 (the moving object) | 100 | 340 | 400 | 700 |
| Minimal Fade | 40–320 | 40 | 300 | 380 | 620 |

## Reference study: "Processing animation for Light camera" (fantasy, Dribbble 3251472)

The shot page could not be fetched from this container (Dribbble returns a bot challenge), so this
study works from the brief's description and its published palette (#000000, #FBFBFB, #2A2A2A,
#A1A1A1, #545454, #737373). Nothing from the artwork is reproduced.

| What makes it work | What Kipat takes | What Kipat does not take |
|---|---|---|
| Monochrome: black, near-white, four greys | Primitives are drawn in one ink (graphite on paper, mist on charcoal). Accent only at completion | A pure black stage; Kipat must work in light mode |
| One central kinetic object | One primary motion per template | Multiple competing objects |
| Geometric transformation (shapes change state, not position) | Arcs that *close*, an iris that *opens*, lines that *set* geometry | Morphing for its own sake |
| Mechanical precision: motion stops exactly | Every template settles once and stops; no loops, no idle motion | A processing loop; the Reveal never "waits" visibly |
| Negative space | Primitives are 1–1.5 dp hairlines or flat fills on a calm scrim | Decoration around the card |
| Motion is the identity | Each template is recognisable from its first 300 ms | Branding drawn into the motion (logos, letters) |

## Shared identity model

`RevealPayload` → `IdentityBlock` inside `RecipientCard` (`ui/components/KipatIdentity.kt`).
Templates choose from a closed set of parts (`reveal/RevealTemplate.kt`):

| Part | Options |
|---|---|
| Entry primitive | none · ripple · orbit · lines · aperture · edge |
| Surface entry | fade-rise · circular reveal · fade-scale · tilt |
| Name entry | fade · wipe |
| Timeline | per template (`RevealTimeline`) |
| Reduced motion | crossfade · contact acknowledgement · static ring · perimeter fade · scale-fade |

One renderer (`RevealScreen`), one effects file (`effects/RevealEffects.kt`).

## Kipat motion system

Researched against Material 3 easing and spring tokens. The cubic values are M3's published
easing curves; the spring values follow the community-published M3 spring token tables (`motor`
package docs; not confirmed against an official Google source).

| Token | Curve | Use |
|---|---|---|
| `KipatFast` | M3 standard `cubic(0.2, 0, 0, 1)`, 120–200 ms | Contact marks, small fades, actions |
| `KipatStandard` | M3 emphasized decelerate `cubic(0.05, 0.7, 0.1, 1)`, 250–400 ms | Anything entering: card surface, text, ripples |
| `KipatEmphasized` | M3 emphasized (two-segment path), 300–450 ms | The template's single geometric move (arcs closing, iris opening, lines extending) |
| `KipatExit` | M3 emphasized accelerate `cubic(0.3, 0, 0.8, 0.15)`, 150–250 ms | Primitives leaving |
| `KipatSettle` | Spring, damping ratio 0.9, stiffness 300 (M3 "standard spatial slow") | Spatial Card tilt; settles once, overshoot ≈ 0.15% (invisible) |

No bouncy or elastic curves. The previous `easeOutBack` overshoot was removed.

## Haptics and sound

- First visual frame: one light tick (`GESTURE_THRESHOLD_ACTIVATE` / `CLOCK_TICK`), skipped under reduced motion.
- Authentic identity resolved (trust line appears): one `CONFIRM`. Never for unverified/revoked.
- Nothing for lines, arcs, text or actions. No sound in any template.

## Concept review (before implementation)

Each template doc answers the 16 design-loop questions and carries a 1–5 scorecard
(clarity, NFC relevance, originality, professionalism, minimalism, motion quality, performance,
accessibility, feasibility, AI-slop risk — lower is better for slop). Any concept with slop risk
> 2 was revised before implementation:

| Template | First slop risk | Why | Revision | Revised |
|---|---|---|---|---|
| Precision Orbit | 4 | Read as a loading spinner and a "glowing orb" | Arcs rotate a short distance once (90°), close, and stop; one ink, no glow; the ring becomes the avatar's quiet frame | 2 |
| Aperture | 3 | Camera skeuomorphism; a lens on screen | Flat blades only, 36 dp across, on the avatar; on screen ≤ 600 ms; no metal, no lens | 2 |
| Spatial Card | 4 | "3D card demo", trading-card shine | 6° max, no rotation on Y, no gloss; depth is only a 3 dp edge and a shadow | 2 |
| Line Assembly | 3 | Cyberpunk HUD tracing | Two lines drawn, two faded in, all ink hairlines; no brackets, no scanning, no glow | 1 |
| Contact Ripple | 2 | — | — | 2 |
| Minimal Fade | 1 | — | — | 1 |

## Higgsfield use (Basic plan)

Phase B chose Precision Orbit, Aperture and Spatial Card for references (geometry and timing are
the open questions; Ripple, Lines and Fade are fully specified by their docs). See
`../higgsfield/critique-v3.md`.

## Future ideas (documented, not built)

| Idea | Primary motion | Why not now |
|---|---|---|
| Monogram Resolve | Initials resolve from two strokes | Needs a real type system for monograms |
| Typographic Reveal | Name set letter-by-letter in reading order | Slow for long names; RTL and accessibility cost |
| Split Plane | Card opens from a centre seam | Close to a door/wallet cliché |
| Layer Lift | Avatar, name, actions lift on separate planes | Three planes = effect stacking |
| Edge Sweep | One sweep across the card face | Reads as a "shine" effect |
| Magnetic Align | Elements snap into the grid | Needs overshoot, which the motion system forbids |
| Soft Mask | Feathered reveal | Feathering needs blur or offscreen buffers |
| Signal Bars | Bars rise like reception | Literal and dated |
