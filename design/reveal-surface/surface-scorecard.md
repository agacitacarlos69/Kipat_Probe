# Reveal surface — scorecard

1–5, higher is better. Scored against the specs in `surface-directions.md` and then checked on
the implemented renders (`docs/images/reveal-surface-states.png`).

| Criterion | A Compact | B Profile header | C Editorial |
|---|---|---|---|
| Identity clarity | 5 | 5 | 4 |
| Professionalism | 5 | 5 | 4 |
| Compactness | 4 | 5 | 3 |
| Recipient speed | 4 | 5 | 4 |
| Distinctiveness | 3 | 3 | 5 |
| Android nativeness | 5 | 5 | 4 |
| Accessibility (font scale, long text) | 5 | 4 | 5 |
| Works with all templates | 5 | 4 | 4 |
| Dark mode | 5 | 5 | 5 |
| Light mode | 5 | 5 | 5 |
| Small-screen behaviour (320 dp) | 5 | 3 | 4 |
| **Total /55** | **51** | **49** | **47** |

## DEFAULT SURFACE: A — Compact identity

It is the one that degrades best: the name gets the full width (no narrow column at 320 dp or with
"Alexandra Marie Villanueva-Santiago"), it reads in one column, and it works under every
primitive. B is faster on a normal phone and is used where a technical, row-based header suits
the motion; C is the most distinctive and is reserved for the template whose motion is about the
anchor.

## Curated pairings (`RevealPresentation`)

Template and surface are separate concepts; only these pairings exist, so combinations don't
proliferate. The person picks a template and an accent; the surface follows.

| Template | Layout | Treatment | Why |
|---|---|---|---|
| Contact Ripple (default) | A Compact | TONAL | The default template gets the default surface; a softer material for the "touch" metaphor |
| Precision Orbit | B Profile header | PAPER | The orbit settles around the avatar, which sits beside the name; precise hairline edge |
| Line Assembly | B Profile header | STRUCTURAL | Drafting lines set a crisp edge (radius 12, ≥ 3:1 outline, full-width rule) |
| Aperture | C Editorial | PAPER | The iris opens on a large anchor; a minimal surface lets it lead |
| Spatial Card | A Compact | FLOATING | The surface is the moving object; slightly stronger depth |
| Minimal Fade | A Compact | PAPER | Quiet: neutral surface, thin border, almost no shadow |

## How the surface emerges from each motion (no "animation → cut → modal")

| Template | Before | After |
|---|---|---|
| Contact Ripple | Empty card ~350 ms before the name | The ripple's edge uncovers the card *and* its content: name at 180 ms |
| Precision Orbit | Ring closes, then a separate card fades in | The ring settles around the avatar; the surface grows outward **from the ring** (circular reveal from the avatar) |
| Line Assembly | Lines, then an empty card, then text | Lines set the edge; material fills inside; content follows 100 ms later |
| Aperture | Blades over an empty card for ~480 ms | Only the avatar disc exists under the iris; once open, the surface unfolds **from the anchor** |
| Spatial Card | An empty slab tilts for ~500 ms | The slab arrives *with* its content; the tilt carries the identity |
| Minimal Fade | Together | Together |

## Picker decision: full screen, not a ModalBottomSheet

The picker now holds a live preview (~300 dp), six styles, an accent row and two actions. That is a
full-height task; M3 positions modal sheets as alternatives to menus and simple dialogs, and a
full-height sheet would add drag-to-dismiss on top of an inner scrolling list. It stays a pushed
screen (predictive Back, own app bar) with the live preview at the top.
