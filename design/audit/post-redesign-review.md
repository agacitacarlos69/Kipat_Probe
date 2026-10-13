# Post-redesign review

Reviewed build: this branch after the Quiet Graphite implementation. Evidence: Robolectric renders
in light and dark (`docs/images/app-screens.png`, `docs/images/reveal-strip.png`), the code, and
mechanical checks (no `uppercase()`, no `·` meta strings in UI, no `Brush`/blur/`BlendMode` in the
shell, no raw colours or literal radii in screens, no `rememberInfiniteTransition`, a single icon
family). Checklist: `.claude/skills/kipat-product-design/SKILL.md` → Review checklist.

Ratings 1–5 (5 best).

| Screen | Hierarchy | Clarity | Task speed | Low noise | Consistency | Professional | Distinctive | Android-native | Accessibility |
|---|---|---|---|---|---|---|---|---|---|
| Home | 5 | 5 | 5 | 5 | 5 | 5 | 3 | 5 | 4 |
| Share | 5 | 5 | 5 | 5 | 5 | 5 | 3 | 5 | 4 |
| Connections | 5 | 5 | 5 | 4 | 5 | 5 | 3 | 5 | 4 |
| Person | 5 | 5 | 5 | 5 | 5 | 5 | 3 | 5 | 4 |
| Profile (view) | 4 | 5 | 4 | 4 | 5 | 5 | 4 | 5 | 4 |
| Edit profile | 4 | 5 | 4 | 5 | 5 | 5 | 3 | 5 | 4 |
| Developer tools | 4 | 4 | 4 | 4 | 5 | 4 | 2 | 5 | 4 |
| Reveal | 5 | 5 | 5 | 5 | 5 | 5 | 4 | 4 | 4 |

## Against the principles

1. **Identity is the hero.** The name is the largest type on Home, Person, Profile and the Reveal,
   on the page or in the single recipient card. Pass.
2. **One primary action.** Home → Share; Person → Save contact; Profile → Edit profile; Reveal →
   Connect (or Done for unresolved cards); Share uses rows. Exactly one filled button per screen.
   Pass.
3. **Quiet shell, memorable Reveal.** No ambient motion in the shell (the beacon is gone); the
   Reveal keeps one contact ring and one edge trace. Pass.
4. **Type and spacing before containers.** The shell has one card (the Profile preview = what a
   recipient sees). Everything else is rows and dividers. Pass.
5. **Physical → digital.** The contact ring sits at the card's top edge and the cobalt line runs
   from that point around the card, so the tap visibly *becomes* the identity. Pass.
6. **Professional, not corporate.** No greeting, plan tile, metrics, "leads". Pass.
7. **Trust looks calm.** Seal + "Verified card" in green; "Sample data" on its own tertiary line;
   lists only show trust in words when it's abnormal. Pass.
8. **Speed.** Tab fade 150 ms; Reveal settled at ~1.55 s (was 2.08 s), name readable from 850 ms.
   No network before the first frame. Pass (device timing not measured).
9. **Native beats web.** One-tap Save contact via ACTION_INSERT; Reveal over the previous app.
   Pass.
10. **Honest UI.** Phone-to-phone shown as "Not available yet"; the Share screen says links open
    kipat.app in a browser; "Open profile" only appears when there is a profile to open. Pass.

## Against the references

- **Linear** (chrome recedes, structure felt not seen): bars use the page background, one 1 dp
  divider above the nav bar, section headings in secondary colour. Matches.
- **Raycast** (primary on tap, rest contextual): Connections row tap opens the person; Pin/Remove
  live in long-press and the overflow menu; sort moved into a menu. Matches.
- **Android/M3**: 3-destination nav bar, small top app bars, two-line list items with leading
  avatar and trailing time, full-screen edit with Save/close and discard dialog, system
  Sharesheet, 48 dp targets, edge-to-edge insets. Matches.
- **Competitors**: identity before any form; no upsell or acquisition prompt; native contact save.

## Does this still look like AI-generated UI?

**Mostly no.** Specific checks:

| Earlier tell | Now |
|---|---|
| Cyan/violet on blue-black, glow, glass, gradients | Neutral graphite/paper + one cobalt; none of the effects remain in the shell |
| Tracked ALL-CAPS eyebrows, `A · B` meta strings | Sentence-case headings above lists only; comma-joined plain text |
| Card kit, 28 dp radii, pill buttons | One card; 12 dp buttons; radius by role |
| Greeting dashboard, feature tiles | Removed |
| Particle/energy Reveal | Ring + line + opaque card |

Remaining risks and what was done:

- *Generic clean-neutral convergence.* Distinctiveness now rests on the identity typesetting, the
  cobalt contact line and the Reveal. That's deliberate (principle 3) but the shell's
  distinctiveness scores stay at 3. **Attention:** a real Kipat mark and a photo avatar will
  carry identity further than any decoration should.
- *Em dashes in copy* were removed from UI strings and sample data (a generated-copy tell).
- *The 600 ms Reveal frame* shows the card mid-fade as a grey slab against the scrim; it lasts
  ≈ 100 ms of a 300 ms fade. Acceptable, but **watch on device**.

## Accessibility notes

- All text pairs ≥ 4.5:1 and interactive outlines ≥ 3:1 (measured, see foundation.md).
- State is never colour-only: trust, card state and write status are words.
- TalkBack: trust line reads "Authenticity: Verified card. …"; icon-only buttons have
  descriptions; avatars describe themselves; identity text is in the Reveal's layout from the
  first frame (alpha only).
- Font scale: no fixed-height text containers; rows use `heightIn(min = …)`.
- Reduced motion: unchanged path (single 200 ms fade, all content visible).
- **Not verified here:** 1.3×/2× font scale screenshots and TalkBack traversal on a device.

## Still needs attention

1. Real photo avatars / logo upload (the avatar API accepts a URI; rendering is initials only).
2. Persistence: edits and connections are in-memory.
3. On-device Reveal review over bright and busy apps (scrim strength 55% → 82%).
4. A finalized Kipat mark.
