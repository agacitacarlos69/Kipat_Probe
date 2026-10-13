# Kipat design system — foundation (Quiet Graphite)

Implemented in `app/src/main/java/com/kipat/probe/ui/theme/`. Screens use these tokens only; a raw
`Color(...)` or `dp` literal outside `ui/theme` is a review comment.

## Plan review against generic defaults (frontend-design process)

| First instinct | Why it's a default | Revised choice |
|---|---|---|
| Keep dark blue-black + cyan/violet | "Premium AI app" palette; audit tell | Neutral graphite/paper + one cobalt ink |
| Warm cream background + serif name | AI calibration trait 1 | Slightly warm *neutral* #F6F6F4, system sans |
| Uppercase tracked section labels, `A · B` meta | AI calibration trait 5 | Sentence-case headings only above lists; two lines instead of dots |
| One radius everywhere (28 dp) | SaaS card kit, trait 4 | Radius by role: 8 / 12 / 16 / 20 |
| Monospace for IDs | Trait 5 | Monospace only inside Developer tools |

Why cobalt: Kipat is an identity credential. Cobalt reads as **ink** — the colour of stamps,
signatures and printed ID — calm and authoritative, and distinct from the system's dynamic colour
so a person's Kipat always looks like Kipat. It carries meaning: it marks *the act of sharing* and
*the moment of contact*.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| background | #F6F6F4 | #131416 | Screen ground |
| surface | #FFFFFF | #1A1B1E | Inputs, the one preview card, menus |
| surfaceSecondary | #EEEEEB | #212226 | Avatar fill, pressed rows, QR frame ground |
| surfaceElevated | #FFFFFF | #26272B | Reveal card, dialogs |
| textPrimary | #17181C | #EDEDEF | Names, titles, values |
| textSecondary | #4E525B | #A9ACB3 | Roles, supporting lines |
| textTertiary | #6A6E78 | #8B8F98 | Timestamps, hints |
| divider | #E2E2DE | #2B2C30 | 1 dp rules (decorative, non-text) |
| outline | #8A8D95 | #737680 | Secondary-button and field borders (≥ 3:1 non-text) |
| accent | #2747C8 | #9DB0FF | Share button, selected tab, links, contact line |
| onAccent | #FFFFFF | #0F1A45 | Text on accent |
| accentContainer | #E5E9FA | #222B4D | Selected nav indicator, selected option |
| verified | #16794A | #5BC48C | Verified seal + text |
| warning | #8A5A00 | #E0A645 | Unverified |
| revoked | #B42318 | #F2766B | Revoked / error |
| backdrop (Reveal) | #E4E4DF @ 72%→92% | #0B0C0E @ 62%→88% | Over the previous app; a step darker than every light surface |
| identity surface — paper | #FCFCFA + 1 dp #DEDED9 | #1F2023 + 1 dp #34363C | Minimal Fade, Precision Orbit, Aperture |
| identity surface — tonal | #F7F5F0 (no edge) | #232428 + 1 dp #34363C | Contact Ripple |
| identity surface — structural | #FCFCFA + 1 dp #8A8D95, radius 12 | #1F2023 + 1 dp #737680, radius 12 | Line Assembly |
| identity surface — floating | #FFFFFF + 1 dp #E4E4E0 | #26272B + 1 dp #34363C | Spatial Card |
| ink (Reveal primitives) | #17181C | #EDEDEF | Arcs, rings, lines, blades — one ink, no gradients |

Share of accent on any screen ≤ 10% of pixels.

### Measured contrast (WCAG 2.x, all ≥ AA 4.5:1 for text)

| Pair | Light | Dark |
|---|---|---|
| textPrimary / background | 16.39 | 15.76 |
| textSecondary / background | 7.24 | 8.11 |
| textTertiary / background | 4.72 | 5.69 |
| textTertiary / surfaceElevated | 5.10 (on white) | 4.60 |
| accent / background (link text) | 6.88 | 8.85 |
| onAccent / accent (button) | 7.44 | 8.04 |
| verified / background | 5.02 | 8.53 |
| warning / background | 5.48 | 8.52 |
| revoked / background | 6.08 | 6.65 |
| outline / surface (non-text, needs 3:1) | 3.32 | 3.29 (on elevated) |

## Typography

System sans (Roboto / Roboto Flex). Two weights: **400 Regular, 500 Medium**. All sizes `sp`.
Sentence case everywhere; no tracked uppercase.

| Role | Size / line | Weight | Use |
|---|---|---|---|
| identityName | 28 / 34 | 500, −0.01 em | The person's name (Home, Profile preview, Person, Reveal) |
| titleLarge | 22 / 28 | 400 | Top app bar titles |
| titleMedium | 16 / 24 | 500 | List row titles, card name in previews |
| sectionHeading | 14 / 20 | 500, textSecondary | Above a list only |
| bodyLarge | 16 / 24 | 400 | Supporting line 1, field values |
| bodyMedium | 14 / 20 | 400 | Supporting lines, descriptions |
| bodySmall | 12 / 16 | 400 | Timestamps, footnotes |
| labelLarge | 14 / 20 | 500 | Buttons |

No display sizes in the product shell.

## Spacing

4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 (dp). Screen gutter **16** (M3 compact margin). List rows
16 horizontal padding, min height 56 (one-line) / 72 (two-line). Section spacing 24 above a
heading, 8 below. Touch targets ≥ 48.

## Radii

| Token | dp | Use |
|---|---|---|
| small | 8 | Chips, small tags, text field corners in dense lists |
| medium | 12 | Buttons, inputs, QR frame, menus |
| large | 16 | Profile preview card, Reveal card |
| extraLarge | 20 | Dialogs, bottom sheets (top corners) |
| full | — | Avatars, status *chips* only |

Buttons are **not** pills.

## Surfaces & depth

Default flat. Hierarchy by tone (background → surface → surfaceSecondary) and 1 dp dividers. No
shadows in the shell. The Reveal card is the only elevated surface (opaque surfaceElevated, a
single soft shadow in light mode, 1 dp divider-coloured border). No glass, no blur, no gradients anywhere in the
shell.

## Iconography

One family: **Material Symbols Outlined**, weight 400, 24 dp (`ui/components/KipatSymbols.kt`).
Filled variants only for the selected navigation item and the verified seal. Icons appear only
where they speed recognition (nav, row leading glyphs for share methods, actions in app bars).
The Kipat mark (dot + arcs) appears only in the Home app bar and nowhere else in the shell.

## Motion

| Token | ms | Easing |
|---|---|---|
| quick | 120 | standard decelerate |
| standard | 200 | emphasized decelerate |
| tab change | fade 150 | — |
| push / pop | 250 slide 1/8 + fade | emphasized |

No ambient animation in the shell.

Reveal motion curves (`reveal/RevealEngine.kt`, `KipatEasing`), from Material 3:

| Token | Curve | Use |
|---|---|---|
| Fast | standard `cubic(0.2, 0, 0, 1)` | Contact marks, small fades, ripple |
| Standard | emphasized decelerate `cubic(0.05, 0.7, 0.1, 1)` | Card surface and text entering |
| Emphasized | M3 emphasized (two-segment path) | A template's one geometric move |
| Exit | emphasized accelerate `cubic(0.3, 0, 0.8, 0.15)` | Primitives leaving |
| Settle | spring ζ 0.9, stiffness 300 | Spatial Card tilt only |

Template timelines: `docs/reveal.md` and `design/reveal-templates/`.

## Components

- **IdentityBlock** — avatar + name + lines + trust, on the page (no container).
- **KipatIdentitySurface** — the single card: what a recipient sees (Reveal, and Profile's "How
  others see you"). Identity, context, trust row, attached actions; see `docs/reveal.md`. Primary,
  secondary and tertiary ink measure ≥ 4.5:1 on every treatment (lowest: tertiary on dark
  floating, 4.60).
- **RecipientCard** — legacy container kept for the Home/Person identity previews.
- **PersonRow** — M3 two-line list item: avatar 40, name, "Role, Organisation", trailing time.
- **ActionRow** — leading symbol, label, optional value, trailing chevron.
- **TrustLine** — seal/glyph + words, colour by state; demo shown as a separate "Sample data" line.
- **PrimaryButton / SecondaryButton / TextButton** — 48 dp height, 12 dp radius.
