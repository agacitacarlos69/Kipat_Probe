# Higgsfield v2 — generation log and critique

Run date 2026-10-13. Higgsfield was used **after** research, audit, principles and direction
selection, and only for card material and Reveal motion. It decided no layout, IA, typography or
copy. Total v2 spend: **4 credits** (1 + 1 + 2), confirmed in `higgsfield account transactions`.

| # | Purpose | Model | Job ID | Settings | Cost | Time |
|---|---|---|---|---|---|---|
| 1 | Primary still — Quiet Graphite | GPT Image 2.5 (`gpt_image_2_5`, variant flare) | `e0780603-0e8f-4840-bfa5-c13fd3f0821d` | 3:2, 2k, quality medium | 1 | 20 s |
| 2 | Backup still — Editorial Identity | GPT Image 2.5 | `115a30b3-319f-4d1f-8ebd-6ee0d2aef822` | 3:2, 2k, quality medium | 1 | 18 s |
| 3 | Reveal motion v2 | Seedance 2.0 Mini (`seedance_2_0_mini`) | `dcda48d8-abbe-4b10-b15c-c82d89762747` | 9:16, 480p, 4 s (model minimum), silent, start + end image | 2 | 149 s |

Prompts: `prompts/quiet-graphite-system.txt`, `prompts/editorial-identity-system.txt`,
`prompts/reveal-motion-v2.txt`. Files: `references/quiet-graphite/`,
`references/editorial-identity/` (downscaled JPEGs; originals and MP4 in git-ignored `out/v2/`).
All three are **design-only references**, not product art.

### Prompt change from the provided templates (deliberate)
Content is described only by geometry ("one long solid rounded bar … the thickest bar"), never by
meaning ("name placeholder"). In v1 the model printed "Role/program" because the prompt named the
field. v2 produced **zero** glyphs in all three outputs.

### Motion inputs
The motion was constrained by two frames built locally: a start frame of abstract neutral blocks
(no icons, no status bar — it represents "an app" without depicting one) and an end frame made by
cropping the accepted white card from still #1 *pixel-exact* onto the same backdrop with an 82%
graphite scrim. The template asked for the still as the *start* image; using it as the *end*
image instead lets the card resolve **into** the accepted geometry rather than starting from it.

---

## 1. Quiet Graphite board (primary) — ACCEPTED

| Axis | Assessment |
|---|---|
| Composition | Three variants, even spacing, honest front-on view; no perspective tricks |
| Proportions | Avatar ≈ 21% of card width, name bar ≈ 68%, two lines, trust row, primary + 2 secondary — matches the planned card hierarchy |
| Material | Flat; white with hairline border (1), tone-only (2), graphite (3). Barely-there shadow. No glass |
| Color | Neutral + one cobalt; green trust; graphite variant is gray, not blue-black |
| Radius | Card ≈ 16 dp feel; action rectangles ≈ 12 dp — matches tokens |
| Hierarchy | Primary action dominates bottom; name bar dominates top |
| Originality | Plain, which is the point; distinctiveness comes from type + Reveal |
| Implementability | Directly maps to Compose surfaces and tokens |
| Professionalism | High |
| AI-slop signals | None of: glow, gradient, glass, sparkle, fake text |

**KEEP:** left-aligned content on a single grid inside the card; 12 dp action radius; full-width
primary with two equal secondaries; hairline border on light, tone-only on dark; trust row placed
between identity and actions.
**MODIFY:** the trust dot is oversized relative to its label — use a 16 dp seal glyph next to
14 sp text. Text bars are full pills; real text has no such caps (no action needed). Variant 2
(tone-only on #EEEEEB) is too low-contrast to stand over another app — the Reveal card uses
variant 1 (light) / variant 3 (dark).
**REJECT:** nothing material.

## 2. Editorial Identity board (backup) — NOT ADOPTED

| Axis | Assessment |
|---|---|
| Composition | Strong whitespace; version 2 (no surface + divider) is genuinely editorial |
| Proportions | Avatar 33% of width and *two* full-width slab bars make the name block heavy |
| Material | Faint shadow on (1); near-black slab (3) reads as heavy |
| Color | Warm neutral canvas edges toward the cream default; muted green and blue fine |
| Radius | **Zero** on all bars — the "broadsheet" generated look (frontend-design calibration trait 3) |
| Hierarchy | Identity dominates as asked; but the only action is a short blue bar → poor affordance |
| Implementability | Easy, but wrong for an Android action-oriented product |
| AI-slop signals | Broadsheet zero-radius slabs; near-black panel |

**KEEP (borrowed into A):** identity block placed directly on the page with no container on Home
and Person screens; generous space above the name.
**REJECT:** slab bars, two-line oversized name, text-only primary action, near-black surface.

This confirms the scorecard decision (A primary, B backup).

## 3. Reveal motion v2 — ACCEPTED WITH CHANGES

Frame review (contact sheet `references/quiet-graphite/reveal-motion-v2-contact-sheet.jpg`):

| t | Observed | Verdict |
|---|---|---|
| 0.0–0.3 s | Single small ring at top-centre, then a brief cobalt-ish ring | **Keep** — contact acknowledgement |
| 0.5 s | Ring meets a short horizontal line at the card's top edge; backdrop begins to dim | **Keep** — the trace starts *at the point of contact* |
| 0.7–1.1 s | Edge line runs down both sides symmetrically; scrim deepens to graphite | **Keep** — symmetric two-sided trace + progressive dim |
| 0.9–1.3 s | Card fills as a *translucent* surface showing the backdrop | **Reject** — Kipat card is opaque for legibility |
| 1.3 s | Avatar and bars resolve at once, slightly soft | **Modify** — stagger avatar → name → lines |
| 1.6 s | Name bar revealed by a left-to-right wipe | **Keep** — adopt as the name's clip-reveal |
| 1.9 s | Card fully white; actions visible | Good settle point |
| 2.5–3.9 s | Green trust mark appears very late | **Reject timing** — trust at ~1.1–1.4 s |
| all | Trace is luminous cyan with a glow halo | **Reject** — 1.5 dp cobalt, no glow |
| all | No text, no letters, no OS chrome, no particles | Pass |

Pacing ≈ 1.5× the specification; native implementation keeps the spec timeline.

## Resulting native decisions (implemented in `reveal/`)

1. Remove particles, drip bead and additive glow from Signature.
2. Contact ring: one 1.5 dp ring from the antenna point (top-centre), 0–300 ms.
3. Scrim: 55% → 82% graphite as the card resolves.
4. Edge trace: cobalt 1.5 dp, starting at the card's top-centre, two segments running down both
   sides and meeting at the bottom; fades as the card becomes opaque.
5. Card: opaque `surfaceElevated`, opacity + 0.98→1 scale.
6. Content: avatar → name (left-to-right clip reveal) → lines → trust → actions; settled ≈ 1.55 s.
