# Signature Reveal — native implementation notes

How the brief's motion language maps onto the Compose runtime. Built first from the written
brief, then revised against the Higgsfield clip generated on 2026-10-13 (job
`555bd1ef-97f8-4a89-8323-f7a0d0d7274d`, see `README.md`).

## Beat map

| Brief beat | Target (ms) | Native implementation | Code |
|---|---|---|---|
| Tap ripple + first glow | 0–200 | 2 stroke rings from the tap origin (upper-centre, 16% height); radial glow additively blended (`BlendMode.Plus`) peaking at 200 ms | `RevealEffects.drawRipples`, `drawGlow` |
| Scrim | 0–900 | Fades in over 180 ms to 60%, then deepens to 100% with the card surface — **adopted from the clip** | `RevealScreen` scrim |
| Particle convergence | 200–600 | 44 precomputed particles (`ParticleField`, FloatArrays, fixed seed) pulled **inward** with a slight swirl; a "drip" bead falls from tap point to card centre | `drawConverge`, `drawDrip` |
| Outline trace (Signature) | 200–900 | A light traces the card's final outline (soft, 55%), bright head dot, fades with the CTAs — **adopted from the clip** | `drawTrace`, `RevealConfig.perimeterTrace` |
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

## Compared against the Higgsfield clip

| Aspect | Clip | Native decision |
|---|---|---|
| Backdrop | Dims progressively as the card resolves | **Adopted** (progressive scrim) |
| Card materialisation | Sparkles trace the outline, then glass fills | **Adopted** for Signature (perimeter trace); the full-strength version is the *Line Trace* template |
| Ripple | Concentric rings at the card centre | Kept at the tap point (signature drip) |
| Particles | Edge sparkles, sparse | Kept inward convergence per brief, ≤ 44 particles |
| Pacing | ~2× slower (CTA ≈ 3.0 s) | Kept spec targets (CTA 1800 ms) |
| Underlying app | Fake iPhone-like home screen | Rejected — the real app shows through |
| Text | Gibberish in name bars | Rejected — all text is dynamic and real |

Still open for a physical-device pass: halo strength over bright apps, and particle size at
device density (`ParticleField` size range).
