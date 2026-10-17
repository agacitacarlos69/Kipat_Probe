# Card typography

**One family: Inter** (Rasmus Andersson, SIL Open Font License 1.1). It is licensable for print and
embedding, has a large x-height that holds at small sizes on PVC, and has tabular-free, neutral shapes.
Inter sits close to the app's system sans (Roboto), so the card and the Reveal read as the same voice.

**Why not Roboto (the app's face)?** Roboto is also licensable (Apache 2.0) and is a valid swap.
Inter's taller x-height and more open apertures survive offset printing on laminate better at 8 pt.

**Rejected:**
- futuristic or "tech" display faces
- monospace labels
- a luxury serif added to look expensive
- tracked uppercase

Weights: **400 Regular** and **500 Medium** only, matching the app. Sentence case. All text is converted
to outlines in the masters (`data-text` keeps the original string).

## Scale

| Role | Face | Size | Tracking | Line | Colour | Where |
|---|---|---|---|---|---|---|
| Wordmark *kipat* | Inter Medium | 9 pt (3.18 mm) | −0.02 em | — | edition ink | Front lockup; generic back |
| Monogram | Inter Medium | 22 pt (7.76 mm) | −0.02 em | — | edition ink | Essential Mono, personalised |
| Name | Inter Medium | 11 pt → 10 → 9, then wrap | −0.01 em | 1.2 | edition ink | Back |
| Context (role / org) | Inter Regular | 8 pt (2.82 mm) | 0 | 1.35 | edition secondary | Back, ≤ 2 lines |
| Utility label | Inter Medium | 8 pt | 0 | — | edition ink | Back: "Tap to connect" |
| Microcopy | — | — | — | — | — | **None on the card.** No legal lines, URLs or serials in v1 |

## Minimums (print)

- Positive type (dark on light): **≥ 7 pt** Regular. The card uses ≥ 8 pt anyway.
- Reversed type (light on dark): **≥ 8 pt** (PLI guidance). Medium below 9 pt.
- Type in a finish layer (gloss, foil, deboss): **≥ 10 pt**, lines ≥ 1 pt (0.35 mm). Kipat puts no type in finishes.
- No grey-on-grey: secondary text keeps ≥ 4.5:1 contrast against its stock in the digital references.
  Measured values are in [color.md](color.md). Verify on the printed proof.
- No thin text over frosted stock: every glyph on frosted PVC sits on a white underbase.

## Copy

| String | Rule |
|---|---|
| Tap to connect | The only instruction on any card. Not "Tap here", "NFC", "Scan me" or "Tap. Know me. Connect." |
| Name | As the person writes it in Kipat; never forced to uppercase |
| Context | Plain words, two lines instead of a middle-dot string ("BS Computer Science" / "Polytechnic University of the Philippines") |
| Slogans | Never on the card; marketing copy lives around the product |
