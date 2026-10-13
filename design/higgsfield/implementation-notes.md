# Signature Reveal — native implementation notes

How the brief's motion language maps onto the Compose runtime. The Higgsfield reference has
**not** been generated yet (see `README.md`), so this mapping comes from the written brief; revisit
it against the real clip.

## Beat map

| Brief beat | Target (ms) | Native implementation | Code |
|---|---|---|---|
| Tap ripple + first glow | 0–200 | 2 stroke rings from the tap origin (upper-centre, 16% height); radial glow additively blended (`BlendMode.Plus`) peaking at 200 ms | `RevealEffects.drawRipples`, `drawGlow` |
| Particle convergence | 200–600 | 44 precomputed particles (`ParticleField`, FloatArrays, fixed seed) pulled **inward** with a slight swirl; a "drip" bead falls from tap point to card centre | `drawConverge`, `drawDrip` |
| Identity surface resolves | 600–900 | Card alpha 0→1, scale 0.94→1, rise 28 dp; glow migrates to card centre and decays to 35% | `RevealScreen` surface `graphicsLayer` |
| Avatar / logo | 600–880 | Avatar scales 0.8→1 and fades | `IdentityCardSlots.avatar` |
| Name | 900–1180 | Fade + 10 dp rise | `slots.name` |
| Role / company | 1200–1480 | Fade + rise | `slots.role` |
| Verification | 1500–1780 | Status badge fades in; *verified* haptic only when `AUTHENTIC` | `slots.badge`, `KipatHaptics.verified` |
| CTAs ready | 1800–2080 | Buttons fade/rise; enabled once past 50% | `ctaReady` |

Timings live in `RevealTimeline.SIGNATURE`; they are product targets, not measurements.

## Behaviours to recreate natively (from the brief)

- Inward attraction, never an explosion (outward is reserved for *Particle Bloom*).
- Previous app faintly visible: translucent window + Compose-drawn scrim (`RevealPalette.scrimTop/Bottom`), no window dim.
- Soft glass card: translucent surface, hairline cyan→violet gradient border, no blur shader (blur is expensive and inconsistent across OEM compositors).
- Restraint: glow ≤ 32% alpha, particles ≤ 56 (12 on low-end), no per-frame allocation for particles.

## Intentionally rejected

- Playing a video/Lottie as the Reveal — content must be dynamic (name, avatar, subtitle, org, authenticity, CTA labels, theme).
- Real-time blur/refraction of the app underneath — not reliably available above other apps and costly.
- 3D card flips, wallet-style card stacks, holographic HUDs — trade-dress and taste reasons.

## To check once a Higgsfield clip exists

1. Does the particle density/size read similarly at 480p? Adjust `ParticleField` size range and `RevealConfig.particleCount`.
2. Halo strength and colour balance (cyan vs violet) — `RevealPalette.NIGHT`, `drawGlow` stops.
3. Drip length/speed — `drawDrip` tail offset (0.22) and `particles` easing.
4. Card entrance overshoot — currently none for Signature (`RISE`); Liquid Logo uses `easeOutBack`.
5. Record the decisions in this file and commit stills ≤ 360 px wide under `references/`.
