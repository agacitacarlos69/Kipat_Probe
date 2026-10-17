# Higgsfield card references: log and critique

**These are references, not source files.** Production geometry exists only in
`design/card/directions/*/front.svg` and `back.svg`, built by `design/card/tools/build.py`. No generated
image is, or will be, copied into `directions/` or `production/`.

## Plan and spend (Basic plan, official CLI `higgsfield` 1.1.26)

| | |
|---|---|
| Order | Research, then the vector directions, scorecard and design system. Higgsfield came only after these, with the approved flat proofs supplied as image references |
| Credits before | **115.5** |
| Credits after | **101.75** (13.75 spent; the account balance confirms it) |
| Image model | GPT Image 2.5 (`gpt_image_2_5`), quality high, 2k, 3:2, 2.75 credits each |
| Product-photoshoot skill | Tried for the finalists' photoshoot images. Its prompt-enhancer endpoint (`/product-photoshoot/enhance`) failed from this sandbox with "Cannot reach", twice; no credits were charged. The same host answered a direct request (HTTP 401), so it looks like a backend or timeout problem rather than a network block. Fallback: GPT Image 2.5 with the brief's §36 preservation prompt, written into `prompts/` |
| Video | **Not generated.** The brief allows one only after the artwork is approved. It hasn't been yet: the user and a physical print proof still have to approve it. Credits are kept for a post-proof round |

| # | Job | Prompt file | Reference input | Output (in repo, downscaled JPG) | Credits |
|---|---|---|---|---|---|
| 1 | `2b152437-8590-40f8-994a-445666524a21` | `prompts/graphite-precision-hero-still.txt` | `references/inputs/graphite-precision-front.png` | `references/graphite-precision-still-r1.jpg` | 2.75 |
| 2 | `7c568cce-bde2-4028-aad7-c849417f292f` | `prompts/architectural-line-hero-still.txt` | `references/inputs/architectural-line-front.png` | `references/architectural-line-still-r1.jpg` | 2.75 |
| 3 | `588fc0c8-7564-440f-a5c3-6383589640fa` | `prompts/frosted-contact-hero-still.txt` | `references/inputs/frosted-contact-front.png` | `references/frosted-contact-still-r1.jpg` | 2.75 |
| 4 | `b1b17ed2-6986-46ae-88d9-c43aa228f8e6` | `prompts/graphite-precision-front-back-hero.txt` | front + back inputs | `photoshoots/graphite-precision-front-back-hero.jpg` | 2.75 |
| 5 | `437cfa7a-f68e-4d75-bc66-add5f778c3cb` | `prompts/frosted-contact-tap-moment.txt` | `references/inputs/frosted-contact-front.png` | `photoshoots/frosted-contact-tap-moment.jpg` | 2.75 |

`prompts/*-photoshoot-*.txt` are the two product-photoshoot attempts that never ran (backend unreachable); kept for a retry.

The full-resolution PNGs (about 3.5 MB each) were not committed. They can be retrieved with
`higgsfield generate get <job id>`.

## Critique checklist

Legend: ✓ good, ✗ problem, ~ partly.

| Check | 1 Graphite still | 2 Arch. Line still | 3 Frosted still | 4 Graphite front+back | 5 Frosted tap |
|---|---|---|---|---|---|
| Preserved artwork (layout, positions) | ✓ | ✓ | ✓ | ✓ both faces | ✓ |
| Invented text | None | None | None | None. All back text is correct, letter for letter | None |
| Altered logo / lockup | ✓ intact | ✓ intact | ✗ lockup white instead of graphite (**my prompt asked for white**) | ✓ | ✓ graphite, correct |
| Changed proportions | ~ edge looks about 1.5 mm, twice too thick | ✗ about 2 mm thick, reads as a coaster | ✓ thin | ✓ believable | ✓ |
| Fake emboss / relief | None | None | None | None | None |
| Impossible materials | None | None | None | None | None |
| Metallic when not specified | No | No | No | No | No |
| Cyberpunk light / glow | No | No | No | No | No |
| Looks like a credit card | No (no chip, digits or network mark) | No | No | No | No |
| QR | n/a | n/a | n/a | Decodes to the **placeholder** route (OpenCV) | n/a |
| Orbit tone | ✗ rendered near-white, not tonal | n/a | n/a | ✗ near-white again | n/a |
| Looks AI-generated | Slightly: the studio is too perfect | Yes: the coaster thickness | Barely | Barely | Slightly: the phone has no camera, too generic |
| **Classification** | **REFERENCE ONLY** | **REJECT** | **REFERENCE ONLY** | **KEEP** | **KEEP** |

## What the references taught the design

1. **Graphite Precision photographs well, even plainly** (review question 8). Twice the model
   brightened the tonal orbit to near-white. That is a warning for print: the printed orbit must be
   proofed so it reads **tonal**, otherwise the card drifts towards "black card with a white ring". The
   raised gloss, not the ink, should make it visible.
2. **Frosted Contact is the most desirable object in the set.** The material carries it, the fingers
   showing through the card in the tap moment are the "ano 'yan?" moment, and nothing else had to be
   added. It is still conditional on a real stock sample (antenna visibility, white-ink opacity).
3. **Architectural Line loses its quality when the edge reads thick.** The idea needs the flat,
   paper-like thinness of a 0.76 mm card. Its approval rests on a real print proof, not on renders.
   It stays the light launch design on cost and reliability.
4. **The back architecture survives photography**: every line was legible and correct in
   reference 4. That supports making the back the unifying system.
5. No reference changed the vector artwork. Lesson 1 becomes a PRINT_QA item: proof the orbit tone.

## Rules carried forward

- Supply the flat proof as the reference. Describe every printed element in words. Forbid
  text, logo, QR, metal, chip, neon and thick edges.
- Describe ink colours exactly as built. Reference 3's white lockup was a prompt error, not a model error.
- Never ship a generated QR. Even when it decodes, the real QR comes from `build.py` and variable-data printing.
