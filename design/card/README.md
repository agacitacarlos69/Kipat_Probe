# Kipat physical card

The physical Kipat NFC card as a product: six editions of one design system, built as real vector
artwork. Design authority: `.claude/skills/kipat-card-design/SKILL.md`.

| Start here | |
|---|---|
| [FINAL_RECOMMENDATION.md](FINAL_RECOMMENDATION.md) | Launch set, tiers, complexity, future editions |
| [final/card-family-board.png](final/card-family-board.png) | All six editions: front, back, material, finish, Reveal pairing, complexity |
| [system/CARD_DESIGN_SYSTEM.md](system/CARD_DESIGN_SYSTEM.md) | Source of truth: format, grid, logo, back architecture, QR, NFC zone, personalisation |
| [research/reference-matrix.md](research/reference-matrix.md) | 20 shipped references and NFC/print facts |
| [directions/scorecard.md](directions/scorecard.md) | Scores, forced redesigns, finalists |
| [production/PRINT_QA.md](production/PRINT_QA.md) | Print and NFC QA (nothing printed yet) |
| [higgsfield/critique.md](higgsfield/critique.md) | Generated references, critique, credits spent |

```
research/      reference matrix
system/        CARD_DESIGN_SYSTEM, typography, color, finishes
directions/    six editions: concept.md, front/back SVG masters, proofs, finish and guide renders
boards/        wireframes, physical/digital pairings, identity stress test (+ per-identity backs)
final/         family board, finalist boards, marketing validation mockup
production/    PRINT_QA, export handoff
higgsfield/    prompts, reference inputs, generated references (NOT source files)
tools/         build.py (artwork), boards.py (presentation)
```

**Card edition is not a Reveal setting.** A printed edition is chosen when the card is ordered, and it
never changes. The Reveal style is a software preference the owner can change at any time; editions
only *recommend* a pairing. No card-customisation UI exists in the app yet. When one is built, it must
show "Physical card design" (edition, name or monogram, QR preview) separately from "Digital Reveal
style", and must never imply that a printed card can change.
