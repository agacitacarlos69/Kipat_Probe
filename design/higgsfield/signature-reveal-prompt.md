# Kipat Signature NFC Reveal — Higgsfield prompts

Status: **generated 2026-10-13** (see `README.md` for job IDs, credits and verdict).
These are the exact prompts `generate.sh` sends. Edit here, not in the script.

Keyframe v2 (job `8a943dec…`) used the §1 prompt plus this extra line, which did not stop the
model from labelling the role bar:

> IMPORTANT: render zero typography. Every bar is a plain solid rounded rectangle with nothing
> written on or near it; do not label any element (no 'Role', 'Program', 'Display name' or
> similar). The background behind the card contains no words at all, only soft out-of-focus dark
> shapes.

Lesson for next time: describe bars by size and colour only — never name what they will hold.

## 0. Original brief (as given)

> KIPAT SIGNATURE NFC REVEAL — an original premium Android identity-reveal motion concept.
> The prior app stays faintly visible under a translucent fullscreen layer. From the NFC tap
> point a subtle circular energy ripple expands; fine luminous particles are pulled inward
> toward the center (not exploding). A compact translucent identity card materialises with a
> soft glass surface, restrained depth and a faint halo, with empty/dynamic areas for avatar
> or logo, display name, role/program, company/university, authenticity badge, Connect and
> Save/Open. No baked names. Timing 0.0–0.2 s ripple + glow, 0.2–0.6 s particle convergence,
> 0.6–0.9 s surface resolves, 0.9–1.2 s name, 1.2–1.5 s role/company, 1.5–1.8 s verified +
> actions. Premium, minimal, dark translucent glass, soft cyan / cool violet. No phone mockup,
> hands, fake 3D device, illegible decorative text, giant holograms, HUD clutter or gaming
> look. Original — not NameDrop, Apple Pay, Google Wallet or Samsung Wallet. Portrait, critical
> content away from edges. A motion reference for engineers rebuilding it in Jetpack Compose.

## 1. Keyframe (end state) — `nano_banana_2_lite`, 9:16, 1k

Purpose: the settled frame the motion clip must land on, and the reference card frame.
There is no finalized Kipat logo, so the mark is described as an abstract placeholder.

```
Flat, front-on portrait UI frame for a premium Android identity app, 9:16. No phone, no device frame, no hands, no perspective.
Full-bleed near-black blue background (#06080D) with a very soft vertical gradient, as if a dark translucent layer sits over another app; the app underneath is only a faint, blurred, unreadable hint of shapes.
Centered slightly above middle: one compact rounded-rectangle identity card (about 78% of the width, corner radius ~28px) made of dark translucent glass (#121722 at ~85% opacity) with a 1px hairline edge that shifts from soft cyan (#5EE6F2) at the top-left to cool violet (#9B8CFF) at the bottom-right, restrained depth, and a faint cyan halo bleeding softly around the card.
Inside the card, top to bottom, all centered, using ONLY empty placeholder shapes, no letters or words anywhere:
- a tiny abstract mark: a small dot with two thin arcs radiating to the right, in cyan
- a circular avatar placeholder (about 64px) with a thin cyan-to-violet gradient ring and empty dark center
- a wide rounded bar for the display name (light grey, ~55% card width)
- a thinner bar for role/program (mid grey, ~45% width) and a thinner, dimmer bar below it for organization (~60% width)
- a small pill-shaped badge with a mint-green dot on the left and an empty mint-tinted bar
- a full-width pill button filled with soft cyan (#5EE6F2), empty, no label
- a full-width outlined pill button with a hairline grey border, empty, no label
A few tiny luminous cyan and violet particles resting close around the card edge, very sparse.
Style: minimal, calm, high-end mobile system interaction, crisp vector-like edges, soft light, subtle dimensionality.
Strictly no text, no letters, no numbers, no logos of real companies, no holograms, no HUD lines, no neon gaming look, no 3D phone, no wallet or payment card imagery.
```

## 2. Motion concept — `seedance_2_0_mini`, 9:16, 480p, 4 s, silent, `--end-image` = keyframe

```
Vertical 9:16 UI motion design, flat front-on screen recording style, no device, no hands, locked-off camera, no camera movement.
Starts on a dark, nearly black translucent screen with a faint blurred hint of another app underneath.
0.0–0.2s: at the upper-center of the screen, a tap point emits one subtle thin circular cyan ripple and a soft glow appears.
0.2–0.6s: fine, sparse luminous cyan and violet particles drift INWARD from all around and converge toward the center of the screen, smoothly, like being gently pulled in — not an explosion; a single small bead of light falls from the tap point to the center.
0.6–0.9s: at the convergence point a compact dark translucent glass identity card scales up slightly from 94% to 100% and fades in, with a faint cyan halo and a hairline cyan-to-violet edge.
0.9–1.2s: the avatar circle and the wide name bar fade and rise gently into place.
1.2–1.5s: the thinner role and organization bars appear.
1.5–1.8s: the small mint status pill appears, then the two pill buttons fade up and settle; everything comes to rest and holds still until the end.
All content areas are empty placeholder shapes — absolutely no readable text, letters or numbers.
Mood: premium, minimal, calm, precise easing, restrained glow, soft cyan and cool violet on near-black.
Avoid: phone mockups, hands, 3D devices, holograms, HUD lines, glitch, lens flares, gaming aesthetics, wallet or payment cards, any real brand.
```

## 3. Rejected directions (do not generate)

- Card "flipping" in 3D or a physical card sliding out of a phone — reads as Wallet/Pay trade dress.
- Particle explosion outward from the card — the brief asks for inward attraction; outward
  bloom exists only as the separate *Particle Bloom* template.
- Full-screen aurora/hologram backgrounds — too loud over another app, costly to render natively.
- Any version with sample names baked in — the runtime is always dynamic text.
