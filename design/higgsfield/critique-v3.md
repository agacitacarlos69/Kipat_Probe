# Higgsfield v3 — Reveal template component references

Basic plan, 2026-10-13. Component motion only: no phone, no UI, no scene, no text. Everything here
is reference; the runtime is Compose (`app/src/main/java/com/kipat/probe/reveal/`) and nothing
below is bundled in the APK. Videos stay outside git (`out/`, ignored).

## Account

| | |
|---|---|
| Plan | basic |
| Credits before | 122 |
| Credits after | 115.5 |
| Spent | **6.5** (budget 10–15) |

Basic exposed every model I tried. Relevant prices (from `generate cost`): GPT Image 2.5 0.25 / 0.5 /
1.5 credits (low / medium / high), Seedance 2.0 Mini 2 (4 s minimum, 480p), Video Background Remover
1, Text to 3D (Tripo) 5, Hunyuan 3D 7, 3D Jutsu Angles 1.25. GPT Image 2.5 supports
`background=transparent` (real alpha). Two Seedance jobs at once were refused with
`not_enough_credits` despite a 118-credit balance and not charged; run alone, the same job
succeeded. Basic appears to allow one video job at a time.

## Phase B choice

Precision Orbit, Aperture, Spatial Card: geometry and timing are the open questions. Contact
Ripple, Line Assembly and Minimal Fade are fully specified by their docs.

## Generations

| # | Job | Model | Params | Credits | Verdict |
|---|---|---|---|---|---|
| 1 | `2ec9188f-b448-4c06-aa8b-6ccffcc2c74b` | GPT Image 2.5 | 16:9, medium, transparent | 0.5 | **Adopted** (orbit states) |
| 2 | `b1531a77-132a-4785-98e7-b71ad481374c` | GPT Image 2.5 | 16:9, medium, transparent | 0.5 | States 2–3 adopted; closed state rejected (pie chart) |
| 3 | `51c8ffdd-885f-4603-9dba-8a19d69d2d8f` | GPT Image 2.5 | 16:9, medium, transparent | 0.5 | Adopted for edge and light fall-off; proportions ignored |
| 4 | `66c2f437-2cb2-467e-aec1-3bfab0f8a6d9` | Seedance 2.0 Mini | 1:1, 480p, 4 s, silent, start/end frames | 2 | **Adopted with corrections** |
| 5 | `1b2ba1c7-39ac-4f8d-b895-0e91cace70cd` | Seedance 2.0 Mini | 1:1, 480p, 4 s, silent, start/end frames | 2 | First 1.0 s adopted; rest rejected |
| 6 | `a5e899be-bb6f-4a09-bf02-c1453796a09d` | Video Background Remover | on #4 | 1 | **Rejected** (no alpha, damaged hairlines) |

Prompts: `prompts/precision-orbit-motion.txt`, `prompts/aperture-motion.txt`,
`prompts/spatial-card-3d.txt` (the brief's prompts verbatim, plus the "STILL" variant actually sent
for Phase C). Motion jobs appended one line: start/end on the reference frames, uniform #F6F6F4,
silent. Start/end frames: orbit = crops of still #1; aperture = drawn locally with the shipping
blade construction (`references/v3/aperture-geometry-check.jpg`), because still #2's closed state
was wrong.

## Validation (rule 15)

| Check | #1 | #2 | #3 | #4 | #5 |
|---|---|---|---|---|---|
| Texture / room shadows | none | none | soft contact shadow only (asked for) | none | none |
| Fake phone / UI / icons | none | none | none | none | none |
| Gibberish / text | none | none | none | none | none |
| Geometry changes shape | — | closed state = radial pie | — | arcs turn asymmetric at 1.3–1.8 s | **yes**: blades turn into a translucent star after 1.0 s |
| Drift / camera move | — | — | — | none | none |
| Alpha edges | clean (native alpha) | clean | clean | n/a | n/a |

## What each reference taught the runtime

**Precision Orbit (#1, #4)** — `references/v3/precision-orbit-motion-contact-sheet.jpg`
- Keep: the contact dot clears *before* the arcs move (contact, then mechanism); colour changes only
  at closure; the closed ring is one even stroke.
- Correct: two symmetric arcs at all times. In the clip, one arc chased the other for ~0.5 s, which
  is exactly a loading spinner. The runtime keeps them 180° apart with equal sweep.
- Correct: pacing ~3× slow (closed at ~1.8 s); runtime closes at 550 ms.
- Correct: the cobalt ring reads heavier than the arcs; runtime thins 1.5 → 1 dp as it changes colour.

**Aperture (#2, #5)** — `references/v3/aperture-motion-contact-sheet.jpg`
- Keep: a brief hold closed, then a fast pinwheel opening (≈ 350 ms in the clip at 0.5–0.9 s); the
  opening is a clean regular hexagon; the end is thin slivers at the rim.
- Correct: a closed iris with radial seams reads as a pie chart. The runtime never closes fully: the
  minimum opening is 12% of the radius, so seams are always offset (tangential).
- Correct: "reduce opacity" produced muddy translucent overlaps. Runtime blades stay solid while
  moving and fade only once they are slivers (160 ms).
- Geometry limit found while building the start frame: the construction breaks once the opening
  passes the rim, so the runtime caps it at the rim and clips to the circle.

**Spatial Card (#3)** — `references/v3/spatial-card-still-alpha.png`
- Keep: at a small tilt the edge reads as a thin band *lighter-than-shadow*, not a dark outline;
  the shadow strengthens as the card goes flat.
- Runtime: edge in the outline token at 45% opacity, ≤ 3 dp, proportional to tilt; X axis only
  (the still rotated on Y, which reads as a product shot, not a card arriving).

## Transparent / background-removal workflow

1. Stills: native alpha from GPT Image 2.5 `background=transparent` — clean edges, verified by
   compositing on paper and graphite.
2. Video: Higgsfield **Video Background Remover** (1 credit) returned H.264 `yuv420p` (no alpha
   channel) on black, and turned 2 px graphite strokes into hollow double outlines; the contact dot
   nearly disappeared. Rejected (`references/v3/orbit-bg-remover-rejected.jpg`).
3. Clean mask instead (option B): the clip was generated on a perfectly uniform #F6F6F4, so alpha was
   derived locally by distance-to-background keying and unpremultiplied, then encoded as VP9 WebM
   with alpha (`out/v3/orbit-alpha-keyed.webm`, 0.58 MB, not committed). Checked at 0/25/50/75/100%
   on paper and graphite (`references/v3/orbit-local-key-check.jpg`): clean on paper; on graphite
   the graphite ink nearly vanishes — which is why runtime ink follows the theme.

## 3D

Text to 3D (5 credits) and Hunyuan 3D (7) are available on Basic. Not run: the only open question
(edge thickness and light at a 6° tilt) was answered by a 0.5-credit still, and a 6° rotation is a
single `graphicsLayer { rotationX }`, so a mesh would not change the build.

## Runtime decision

All six templates are procedural Compose (Canvas strokes/paths + `graphicsLayer`). No video, Lottie,
Rive or 3D asset is needed: every reference reduces to arcs, a hexagonal iris, lines, circles and
one layer transform. The keyed WebM is a timing reference only.
