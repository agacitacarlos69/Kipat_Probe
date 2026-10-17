# Nearby sharing: scorecard

Scores 1–5, higher is better on every row (for **clutter**, 5 means least cluttered). Judged by
the strategy author against `wireframes.md`. **No user testing has been done.** Condition 5 in
`docs/product/feature-gates.md` requires a moderated test before any of this is built.

| Criterion | A: small "Send" action | B: post-connect action | C: contextual row | Why |
|---|---|---|---|---|
| **Clarity** | 3 | **5** | 3 | B adds one labelled action with the person's name, at the moment it makes sense. A shows Send to someone you haven't decided about. C's row changes under the thumb |
| **Identity priority** | 3 | **5** | 4 | B's first frame is today's Reveal, unchanged. A adds a row to every Reveal |
| **Speed** (taps to the picker) | **5** (2 taps) | 4 (Connect + 2) | 4 (Connect + 2) | A is fastest but allows sending before Connect. B's extra step is Connect, which users take anyway when they mean to keep someone |
| **Clutter** | 2 | **5** | 4 | A: +48 dp on every Reveal. B: +48 dp only after Connect. C: no height change, but three labels on one row wrap at large font |
| **Professionalism** | 3 | **5** | 4 | B reads as a calm sequence (meet, keep, send). A reads like a tool |
| **Scalability** | 2 | 4 | 3 | A has nowhere to grow except more icons. B's sheet holds two rows today and can hold three without touching the Reveal. C's three-slot cap is a constant temptation to swap items |
| **Total** | 18 | **28** | 22 | |

## Recommendation: **Option B (post-connect action)**

1. **It matches principle 4 (intentional connections) and principle 8 (consent):** you can only send
   content to someone you chose to keep, and they must accept it.
2. **The first state stays the signature asset.** The Reveal that people recognise is not changed by
   a feature most taps will never use.
3. **It separates two "shares":** **Share** (my identity, Home) versus **Send to Vash** (content to a person).
4. **It degrades well:** if the gate never opens, nothing in the Reveal has to be undone. If it
   opens at S0 only, Share link and Share contact live on the Person screen.

Borrow from C: when the Reveal shows your **own** preview, the light row holds Edit profile and
Reveal style. That needs no Send.

Reject the brief's concept as drawn: it exceeds the surface height ceiling, puts a toolbox on the
first frame, allows sending before Connect, and collides with Share
(`wireframes.md`, "The concept in the brief, critiqued").
