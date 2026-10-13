---
name: kipat-product-design
description: Kipat's product-design rules (Quiet Graphite). Use before designing, reviewing or changing any Kipat UI — Compose screens, theme tokens, copy, icons, the NFC Reveal, or Higgsfield design prompts — so work stays consistent and never drifts back to generic AI-styled UI.
---

# Kipat product design

Evidence and rationale: `design/research/reference-matrix.md`, `design/audit/current-ui-audit.md`,
`design/KIPAT_DESIGN_PRINCIPLES.md`, `design/system/foundation.md`. Read the relevant one when a
rule here needs context. Tokens live in `app/src/main/java/com/kipat/probe/ui/theme/`.

## What Kipat is

An Android-first identity + physical-to-digital connection system: tap a Kipat Card on a phone →
a translucent **Reveal** shows who the person is over whatever app was open → Connect / Save
contact / Open profile → dismiss back to that app. Promise: *Tap. Know who I am. Connect.*

Users: students, developers, professionals, founders, creators, speakers, organisation members.

## Key user jobs → primary action

| Context | Job | Primary action |
|---|---|---|
| Home | Present my identity | **Share** |
| Share | Let someone receive it | QR / copy link / system share |
| Connections | Find someone I met | Open person (row tap) |
| Person | Keep their details | **Save contact** |
| Profile | See how others see me; change it | **Edit profile** |
| Reveal (recipient) | Understand who this is | **Connect** (or Save contact) |

Exactly one filled button per context. Everything else: text button, row, or overflow/long-press.

## Navigation rules

- Bottom navigation: **Home, Connections, Profile** (3). Sharing is an action, not a tab.
- Share is a full-screen route from Home/Profile. Developer tools live under Profile, last row.
- Selected tab icon = filled Material Symbol; unselected = outlined.
- Re-tap active tab → its root. Back from a non-Home tab → Home → exit. Edit with unsaved changes → confirm discard.
- Top app bars: small for every screen; Home's hero is the identity, not the bar.

## Visual rules

- **Color:** neutral 90–95%. Light bg #F6F6F4, text #17181C/#4E525B/#6A6E78; dark bg #131416, text #EDEDEF/#A9ACB3/#8B8F98. One accent: cobalt ink #2747C8 (light) / #9DB0FF (dark) — for Share, selected nav, links, the Reveal contact line. Status: verified #16794A/#5BC48C, warning #8A5A00/#E0A645, revoked #B42318/#F2766B. Dividers #E2E2DE/#2B2C30 (decorative); outlines of buttons/fields #8A8D95/#737680 (≥ 3:1). Every text pair ≥ 4.5:1 (measured).
- **Type:** system sans, weights 400 & 500 only, sentence case. Identity name 28 sp/500 is the largest type in the shell. App bar title 22 sp. Row title 16/500. Section heading 14/500 secondary, only above a list.
- **Spacing:** 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40. Gutter 16. Rows ≥ 56 dp (one-line) / 72 dp (two-line). Targets ≥ 48 dp.
- **Radius:** 8 chips, 12 buttons/inputs/QR frame, 16 recipient card & Reveal card, 20 dialogs/sheets. Buttons are not pills. Avatars and status chips are round.
- **Surfaces:** flat; tone steps + 1 dp dividers. The shell has **one** card: Profile's identity surface preview (`KipatIdentitySurface`, `inShell = true`, always edged). No shadows, glass, blur or gradients in the shell.
- **Icons:** Material Symbols Outlined only (`KipatSymbols`). The Kipat mark appears only in the Home app bar and on the Share screen's card row.

## Motion rules

- Shell: tab fade ≤ 150 ms, push/pop 250 ms; no ambient or decorative motion.
- Reveal: six templates (`design/reveal-templates/`): Contact Ripple (default), Precision Orbit, Line Assembly, Aperture, Spatial Card, Minimal Fade (low-power). Each = **one primary motion + surface resolve + text staging**, nothing else. No particles, glow, gradients, blur, bloom, loops or sound in any template. Every template settles once (no overshoot) in ≤ 1.4 s, name readable by ~1.1 s.
- Motion curves (`KipatEasing`): M3 standard (contact marks, small fades), M3 emphasized decelerate (anything entering), M3 emphasized (a template's one geometric move), M3 emphasized accelerate (primitives leaving), spring ζ 0.9 / k 300 (Spatial Card only).
- A new template must: reuse `RevealStage`/`IdentityBlock`, add one `EntryPrimitive`, have a design doc with the 16-question review and a slop risk ≤ 2, a reduced-motion equivalent, light + dark previews, and a gallery render.
- Reduced motion (`ValueAnimator.areAnimatorsEnabled() == false`): each template's own 200 ms equivalent (static ring, dot acknowledgement, perimeter fade, scale-fade, crossfade); nothing moves, tilts or masks; all content readable immediately.

## NFC Reveal rules

- Own task (`taskAffinity=""`), translucent, edge-to-edge; the real previous app shows through. Never draw a fake app behind it.
- Every template settles into **`KipatIdentitySurface`** (`design/reveal-surface/`): opaque, content-height (≈ 280–370 dp), width screen − 40 dp up to 380 dp, padding 24 (20 below 340 dp). Layout and material are curated per template (`RevealPresentation`); never add free combinations. Surface, backdrop and primitive ink follow the system theme. Accent only on Connect, the monogram, completion details and selection.
- The surface must look finished as a still: judge it with `IdentitySurfaceRenderTest` renders, not the animation. Content arrives as the surface reaches it; never an empty card waiting for text or buttons.
- Order of information: anchor (photo / logo / accent monogram — never a grey disc), name 22–24 sp, role then organisation (quieter), trust row (glyph + words, demo chip on the same row, detail only when something is wrong), rule, actions. Identity text is in the layout from frame one (alpha only) for TalkBack.
- Actions: Connect (filled, 48 dp; "Done" for an unresolved card; "Connected" = accent tint), then Save contact (Android ACTION_INSERT) / Open profile (only when there is a Kipat profile to open) as a light icon + label row. Never stacked outlined buttons. Close: 48 dp target, quiet 20 dp ✕, same top-end corner in every layout. No "tap outside" hint text.
- Never record a tap of your own card as someone you met; never turn a tap into a connection without an explicit Connect.
- No network before the first frame. Haptic on first frame; a second, calm haptic only when the card is verified.
- Demo/sample data says "Sample data" on its own line. Debug sources are not shown in the card.

## Copy rules

Sentence case. Verbs that say exactly what happens: Share, Copy link, Save contact, Connect, Open profile, Edit profile, Save, Discard, Remove, Pin. Same verb through the flow ("Save" → "Saved"). No "Continue/Proceed/Manage/Explore/Discover/Experience". No marketing in product UI. No middle-dot meta strings — use two lines or plain words ("UX Designer, Globe Labs"; "Connected 2h ago by card tap").

## Accessibility

Font scaling (no fixed heights on text containers), 48 dp targets, content descriptions on icon-only buttons, state never by colour alone (always words), Back works everywhere, drafts survive rotation (ViewModel), reduced motion honoured, both themes pass contrast.

## Android-native requirements

M3 components (TopAppBar, NavigationBar, ListItem-style rows, AlertDialog, DropdownMenu), edge-to-edge with inset padding, predictive-back-safe navigation (no custom back interception except the discard dialog), system Sharesheet for sharing, ACTION_INSERT for contacts.

## Anti-AI-slop rules (reject on sight)

Card inside card · a card around two lines of text · 28 dp+ radii · pill buttons · glowing or gradient borders · gradient washes · cyan/violet palette · blue-tinted near-black · greeting dashboards ("Good afternoon, …") · feature tiles / carousels of options · fake metrics · status pills everywhere · decorative icons in headings · tracked ALL-CAPS eyebrows · `A · B · C` meta strings · monospace data labels (outside Developer tools) · ambient pulsing animation · particles anywhere (including the Reveal) · spinners or looping motion · 3D beyond a 6° tilt · glass in the shell · bottom sheet on every row tap · segmented controls for rarely-changed settings · iOS patterns (centered modal with "tap outside", segmented filters) · Higgsfield or any generator inventing layout or text.

## Do / don't examples

- DO: Home = small bar with mark → avatar, name 28 sp, "BS Computer Science", "Polytechnic University of the Philippines", "✓ Verified card" → [Share] [Preview] → "Kipat Card — Active" row → "Recent" list.
- DON'T: a glass hero card with an eyebrow "ACTIVE IDENTITY" and a gradient avatar ring.
- DO: Connections row "Bea Villanueva / Product Manager, Maya / Unverified card", trailing "1d".
- DON'T: "Verified · demo" in mint on every row.
- DO: Share screen with a large QR, Copy link, Share link…, Kipat Card (Active), Phone tap (Later, disabled).
- DON'T: a pulsing beacon saying "Ready to share" when nothing is ready.

## Higgsfield rules

Higgsfield explores card material and Reveal motion only — never layout, IA, typography or copy.
Generate isolated components (no phone, no scene) on transparent or perfectly uniform backgrounds;
prefer GPT Image 2.5 `background=transparent` at medium (0.5 credit) for stills; one Seedance job
at a time on Basic. References are recreated procedurally in Compose, never shipped
(`design/higgsfield/critique-v3.md`).
Prompts must forbid all text/letters/numbers and represent content with blank bars and an avatar
circle. Generated output is critiqued (`design/higgsfield/critique-v2.md`) before anything is
adopted; it is never bundled in the APK.

## Review checklist (run after any UI change)

1. Is the name the first thing you see on identity screens?
2. Exactly one filled button?
3. Any container that only groups two lines? Delete it.
4. Any colour outside tokens, any radius outside 8/12/16/20?
5. Any uppercase label, middle-dot string, gradient, glow, glass?
6. Any motion the user didn't cause (outside the Reveal)?
7. State shown in words as well as colour?
8. Light and dark both checked; font scale 1.3× checked.
