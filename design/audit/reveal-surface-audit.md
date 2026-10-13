# Reveal surface audit (before the surface redesign)

Source: `RevealScreen.kt` / `RecipientCard` / `IdentityBlock` at commit `7f8216f`, and the
Robolectric renders of all six templates (`docs/images/reveal-templates-{light,dark}.png`,
411 × 891 dp, xxhdpi). Measurements below were taken from the settled renders by pixel analysis,
not estimated.

## Measured

| Property | Value | Note |
|---|---|---|
| Width | 361 dp on a 411 dp screen (24 dp margins), max 420 dp | Never capped for phones; 420 dp is wider than any reading need |
| Height (settled) | 366–368 dp | Content-driven already; **not** a fixed height |
| Radius | 16 dp | Fine |
| Padding | 24 dp all sides | Same on a 320 dp phone (no compact step) |
| Avatar | 56 dp, neutral `surfaceSecondary` disc, initials in `textSecondary` (titleLarge) | Reads as a placeholder |
| Name | 28 / 34 sp, weight 500, wipe or fade | Largest type, but alone on a wide plane with nothing tying it to the avatar |
| Role / org | 16 / 24 and 14 / 20 sp, `textSecondary`, 4 dp under the name | Two similar grey lines; organisation equal in weight to role |
| Spacing | avatar→name 16, lines→trust 12, trust→actions 24, actions 12 apart | No rhythm: three different gaps, no grouping |
| Trust | 16 dp glyph + "Verified card" (14 sp, state colour), then "Sample data" on its own 12 sp line | The demo line looks like debug output; trust floats between identity and actions |
| Actions | Connect: full-width filled 48 dp with icon. Save contact: full-width outlined 48 dp (Open profile halves it when present) | Two stacked full-width buttons = a form |
| Close | 48 dp `IconButton`, 24 dp ✕ in `textSecondary`, in the avatar row | Sits mid-height of the avatar, unrelated to the surface corner |
| Empty-space ratio | 35% (light) / 48% (dark) of the card's rows contain nothing | Includes the 24 dp gap above the actions and the 16 dp row gaps |
| Light | White `#FFFFFF` card + `#E2E2DE` border + 12 dp shadow on a `#F6F6F4` scrim at 94% | White on near-white: separation depends on the shadow alone |
| Dark | `#26272B` card, no border, no shadow, on `#0E0F12` at 86% | A flat slab; no layering |
| Per template | Identical surface for all six | Every animation ends in the same object |

## The "empty rectangle" is mostly a timing problem

The settled card is not tall. What reads as "large blank rectangle" is the **surface arriving
before its content**. Time between the surface being half visible and the name being half
visible (from `RevealTimeline`):

| Template | Surface ½ | Name ½ | Empty card on screen |
|---|---|---|---|
| Contact Ripple | ~100 ms | ~450 ms | ~350 ms |
| Precision Orbit | ~480 ms | ~780 ms | ~300 ms |
| Line Assembly | ~650 ms | ~900 ms | ~250 ms |
| Aperture | ~100 ms | ~580 ms | ~480 ms (blades on an empty card) |
| Spatial Card | ~40 ms | ~540 ms | ~500 ms (an empty slab tilting) |
| Minimal Fade | ~120 ms | ~120 ms | none |

That is the "cool animation → CUT → generic modal" effect: the primitive finishes, then an
empty card waits, then text fades in.

## The five questions

**Why is the card this tall?** It is content height (≈ 367 dp), but ~130 dp of it is unstructured
gaps plus a second full-width button. There is no UX reason for the stacked outlined button;
it exists because "secondary = outlined full width" was the default component.

**Why is there this much empty space?** Three unrelated gaps (16 / 12 / 24 dp) with no grouping,
and an avatar row whose right half is empty except for the ✕. In motion, mostly the timing above.
No justification. → Change.

**Why is the identity pushed to the top?** Because the avatar sits on its own row and the trust and
actions are separated by the largest gap. The identity is a top cluster; the actions a bottom
cluster; nothing joins them. → Make identity + trust one block, actions a second block, joined by a
divider at a deliberate distance.

**Why are the actions so far from the identity?** The 24 dp gap plus two 48 dp buttons push
"Save contact" ~110 dp below the trust line. No justification. → One primary button; secondary
actions become a light row.

**Why does it need to look like a dialog?** It doesn't. A dialog is a question with buttons; the
Reveal is a *person* with actions. The ✕ in the avatar row, the white plane and the stacked buttons
are dialog conventions. → The surface should read as an identity object: a designed anchor, a
name with a strong relationship to it, trust stated as part of identity, actions attached.

## What to keep

Opaque surface (legibility over any app), 16 dp radius, left-aligned single grid, name as the
largest type, reading order avatar → name → context → trust → actions, the close affordance,
Connect as the only filled button, light/dark following the system.
