# Card finishes

Rules:
1. Every edition must look finished in flat CMYK; a finish adds touch, never the idea.
2. At most one special finish per side, plus the base laminate.
3. Nothing conductive near the coil.
4. Never recommend a finish just because it "looks expensive".

Sources are in [../research/reference-matrix.md](../research/reference-matrix.md):
- B3: MOO special finishes
- B4: Prontaprint PVC
- B5: Plastek and 4OVER4
- C6: Dot
- NFC facts

| Finish | Visual value | Cost risk | NFC risk | Durability | Where to use | Where not to use |
|---|---|---|---|---|---|---|
| **Matte laminate** | Calm, no glare, reads like good paper; hides scratches (Plastek) | Low (common) | None | Good | Default for every light edition | — |
| **Soft-touch** | "Velvety, slightly grippy" (MOO); the tactile signature of the dark editions | Medium; **vendor-dependent on PVC** (Plastek lists it as paper-only; Dot ships soft-touch PVC) | None | Fair: can burnish and pick up oils over months | Graphite Precision, Layered Depth | Light stocks (shows dirt); anything that must look new for years without a sample test |
| **Spot gloss (flat UV)** | Sheen contrast on matte; visible at an angle | Low–medium | None (non-conductive) | Good | One motif per edition | Text below 10 pt; more than 50% of a side; across the die line |
| **Raised spot gloss (3D UV)** | Touchable motif: you *feel* the orbit or line | Medium | None | Good; edges can chip if too thin | Graphite Precision orbit, Architectural Line, Aperture segments. Lines ≥ 1.0 mm, inside the 4 mm safe zone, ±0.5 mm registration | Thin text; bleed; within 4 mm of the edge; the contact point (keep it flat ink) |
| **Deboss** | Real depth without colour | Medium–high on PVC (die; vendor capability varies) | Low, but **verify**: pressure can stress the inlay; keep it away from the chip module | Excellent | Layered Depth plane (0.15 mm) | Over the chip module position; fine detail |
| **Emboss** | Raised relief | Medium–high | Low; same chip caution | Good | Not used: raised PVC characters are bank-card furniture | Names, numbers (bank cue) |
| **Frosted translucent PVC** | Material-led: light passes, the card feels like an object rather than a print | Medium (special stock plus white ink) | None for the stock. The antenna may show through, which can be honest (Nothing) or ugly, so decide with a sample | Good | Frosted Contact | Full-colour imagery (gift-card look); thin unbacked text |
| **White ink** | Opacity on clear or frosted stock; soft milky lines | Medium (extra plate) | None | Good | Frosted underbase and rings | On opaque white-core stock (wasted) |
| **Edge colour** | The purest accent: seen only in the hand (MOO Luxe) | High; edge painting is **unproven on PVC**. The PVC route is a coloured **core sheet** (Monzo "coral core") | None | Excellent if core-based | Candidate Kipat signature: a cobalt core on Signature tier | Do not promise until a vendor confirms a coloured core in 0.76 mm builds |
| **Metallic foil** | Shine | Low–medium | **High.** Conductive, so it can detune and shield the coil; no vendor clearance found | Fair (scuffs) | **Not used** in any v1 edition | Anywhere in the NFC safety zone (the whole card by default) |
| **Metal card / metal core** | Weight, status | High | **Very high** without a ferrite layer, slot or PVC tap face | Excellent | Not in scope. If ever: a hybrid with a PVC tap face (Mobilo) | Kipat v1 |
| **Holographic / iridescent film** | Flash | Medium | Varies (some films are metallised) | Fair | **Never**: payment-card and gaming cue | — |

## Per edition

| Edition | Base laminate | One special finish | Without the finish it still works because… |
|---|---|---|---|
| Graphite Precision | Soft-touch (fallback: matte) | Raised gloss on the orbit plus a gloss contact point | The orbit is printed as a tonal line |
| Frosted Contact | — (frosted stock) | White ink rings | The rings *are* the white ink, part of the stock build |
| Architectural Line | Matte | Raised gloss on the line (stops at the safe zone) | The line is printed graphite |
| Aperture | Matte | Spot gloss on the segments | The segments are printed graphite |
| Layered Depth | Soft-touch (fallback: matte) | Deboss **or** spot gloss on the plane (choose one with the vendor sample) | The plane is a printed tonal step |
| Essential Mono | Matte | None | It has no tricks by definition |
